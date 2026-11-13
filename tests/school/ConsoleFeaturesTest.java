package school;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Scanner;
import school.application.DemoData;
import school.application.SchoolFacade;
import school.grades.Grade;
import school.grades.GradingMode;
import school.storage.FileSchoolStore;
import school.ui.ConsoleMenu;

/** User-facing regression tests for correction, cancellation, editing and file export. */
public final class ConsoleFeaturesTest {
    private ConsoleFeaturesTest() { }

    public static void run() throws Exception {
        retriesInvalidFieldsWithoutDiscardingForms();
        retriesCategoryThatExpandsDuringNormalization();
        editsOnlyTheSelectedAssessmentAndNotifies();
        unchangedEditDoesNotNotify();
        cancelledActionsDoNotChangeSchool();
        eofDuringEditKeepsOldAssessment();
        exportsReportsWithoutOverwritingExistingFiles();
        refusesTheActiveDataPathEvenBeforeItExists();
        explainsMemoryModeSaving();
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("ConsoleFeaturesTest passed.");
    }

    private static SchoolFacade demo() {
        SchoolFacade school = new SchoolFacade();
        DemoData.populate(school);
        return school;
    }

    private static String runConsole(SchoolFacade school, String input, Path dataFile) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (Scanner scanner = new Scanner(input);
             PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            new ConsoleMenu(scanner, stream, school, new FileSchoolStore(), dataFile).run();
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static void retriesInvalidFieldsWithoutDiscardingForms() throws Exception {
        SchoolFacade school = demo();
        String input = "4\nC3\nNew Course\nT1\nMonday\n0\n-1\nwrong\n25\n9\n1\n"
                + "7\nS1\nC1\nunknown\nexam\nNaN\nInfinity\n-1\n150\nbad\n0\n100\n101\n95\nCorrected score\n"
                + "9\nS1\nC1\n2026-02-30\n2026-2-3\n2026-02-03\nmaybe\nда\n0\n";
        String output = runConsole(school, input, null);
        check(school.findCourse("C3").getCapacity() == 25 && school.findCourse("C3").getGradingMode() == GradingMode.POINTS,
                "Capacity and mode retries must retain the course identity and schedule");
        check(school.findCourse("C3").getSchedule().equals("Monday"), "Course fields entered before errors are retained");
        Grade added = school.getGrades().get(4);
        check(added.studentId().equals("S1") && added.courseId().equals("C1") && added.category().equals("exam")
                        && added.earned() == 95 && added.possible() == 100 && added.comment().equals("Corrected score"),
                "Category, finite number and score-bound retries complete exactly one valid grade");
        check(school.getGrades().size() == 5 && school.getNotifications("S1").size() == 5,
                "Invalid grade fields do not add assessments or events");
        check(output.contains("Grade #" + added.id() + " saved"), "Adding a grade reveals its stable ID");
        check(school.getAttendance().stream().anyMatch(entry -> entry.studentId().equals("S1")
                        && entry.courseId().equals("C1") && entry.date().equals(LocalDate.of(2026, 2, 3)) && entry.present()),
                "Date and yes/no retries preserve student and course");
        check(output.contains("Points must be finite numbers") && output.contains("YYYY-MM-DD format"),
                "Numeric and date errors explain what to correct");
    }

    private static void retriesCategoryThatExpandsDuringNormalization() throws Exception {
        SchoolFacade school = demo();
        String input = "7\nS1\nC2\n" + "İ".repeat(64) + "\nquiz\n80\n100\nValidated category\n0\n";
        String output = runConsole(school, input, null);
        Grade grade = school.getGrades().get(4);
        check(grade.category().equals("quiz") && grade.earned() == 80 && grade.comment().equals("Validated category"),
                "A category that exceeds its bound after normalization retries only that field");
        check(output.contains("Error:") && output.contains("Grade #" + grade.id() + " saved"),
                "Normalized category validation happens before the rest of the assessment form");
    }

    private static void editsOnlyTheSelectedAssessmentAndNotifies() throws Exception {
        SchoolFacade school = demo();
        Grade original = school.getStudentGrades("S1", "C1").get(0);
        Grade otherCourse = school.getStudentGrades("S1", "C2").get(0);
        var before = school.getGrades();
        int studentNotifications = school.getNotifications("S1").size();
        int teacherNotifications = school.getNotifications("T1").size();
        int events = school.getActivityLog().size();
        String input = "15\nS1\nC1\nwrong\n" + otherCourse.id() + "\n" + original.id()
                + "\n\nwrong\n100\n\n/clear\n8\nS1\nC1\n0\n";
        String output = runConsole(school, input, null);
        Grade edited = school.findGrade(original.id());
        check(school.getGrades().size() == before.size(), "Editing replaces an assessment instead of appending");
        check(edited.id() == original.id() && edited.studentId().equals(original.studentId())
                        && edited.courseId().equals(original.courseId()) && edited.category().equals(original.category())
                        && edited.earned() == 100 && edited.possible() == original.possible() && edited.comment().isEmpty(),
                "Edit keeps identity, supports blank defaults and clears a comment explicitly");
        check(school.getGrades().stream().filter(grade -> grade.id() != original.id())
                        .allMatch(before::contains), "Other assessments are unchanged");
        check(Math.abs(school.calculateFinalGrade("S1", "C1") - 82.0) < 0.000001, "Correction recalculates the weighted result");
        check(school.getNotifications("S1").size() == studentNotifications + 1
                        && school.getNotifications("T1").size() == teacherNotifications + 1
                        && school.getActivityLog().size() == events + 1,
                "One successful correction notifies both recipients and adds one event");
        check(output.contains("selected student and course") && output.contains("Grade #" + original.id() + " updated")
                        && output.contains("#" + original.id() + " | homework | 100.00/100.00"),
                "The console restricts selection to the pair and lists stable assessment IDs");
    }

    private static void unchangedEditDoesNotNotify() throws Exception {
        SchoolFacade school = demo();
        long id = school.getGrades().get(0).id();
        var grades = school.getGrades();
        var events = school.getActivityLog();
        String output = runConsole(school, "15\nS1\nC1\n" + id + "\n\n\n\n\n0\n", null);
        check(output.contains("No changes to grade #" + id), "All blank defaults report an unchanged assessment");
        check(school.getGrades().equals(grades) && school.getActivityLog().equals(events),
                "Leaving every edit field blank must not issue a correction event");
    }

    private static void cancelledActionsDoNotChangeSchool() throws Exception {
        SchoolFacade school = demo();
        var users = school.getUsers();
        var courses = school.getCourses();
        var enrollments = school.getEnrollments();
        var grades = school.getGrades();
        var attendance = school.getAttendance();
        var events = school.getActivityLog();
        var notifications = school.getNotifications("S1");
        String input = "2\n1\nS3\nNew Student\n/cancel\n"
                + "4\nC3\nNew Course\nT1\n/cancel\n"
                + "5\nS2\n/cancel\n"
                + "7\nS1\nC1\nexam\n95\n/cancel\n"
                + "9\nS1\nC1\n2026-01-01\n/cancel\n"
                + "15\nS1\nC1\n" + grades.get(0).id() + "\n\n100\n\n/cancel\n"
                + "16\nS1\ny\nn\n1\n/cancel\n0\n";
        String output = runConsole(school, input, null);
        check(output.split("Action cancelled", -1).length - 1 == 7, "Cancellation returns every incomplete action to the menu");
        check(school.getUsers().equals(users) && school.getCourses().equals(courses)
                        && school.getEnrollments().equals(enrollments) && school.getGrades().equals(grades)
                        && school.getAttendance().equals(attendance), "Cancelled forms do not mutate any school records");
        check(school.getActivityLog().equals(events) && school.getNotifications("S1").equals(notifications),
                "Cancelled assessments do not publish events or notifications");
    }

    private static void eofDuringEditKeepsOldAssessment() throws Exception {
        SchoolFacade school = demo();
        var before = school.getGrades();
        var events = school.getActivityLog();
        var inbox = school.getNotifications("S1");
        String output = runConsole(school, "15\nS1\nC1\n" + before.get(0).id() + "\nexam\n100\n100\n", null);
        check(output.contains("Input ended.") && output.contains("Goodbye."), "EOF during editing exits cleanly");
        check(school.getGrades().equals(before) && school.getActivityLog().equals(events)
                        && school.getNotifications("S1").equals(inbox), "An unfinished edit leaves the original assessment and inbox intact");
    }

    private static void exportsReportsWithoutOverwritingExistingFiles() throws Exception {
        SchoolFacade school = demo();
        Path directory = Files.createTempDirectory("school-console-export-");
        try {
            Path existing = directory.resolve("existing.txt");
            Files.writeString(existing, "keep existing file", StandardCharsets.UTF_8);
            Path text = directory.resolve("nested/report.txt");
            Path csv = directory.resolve("report.csv");
            var grades = school.getGrades();
            var events = school.getActivityLog();
            String input = "16\nS1\ny\ny\n9\n1\n" + existing + "\n" + text
                    + "\n16\nS1\nn\nn\n2\n" + csv + "\n0\n";
            String output = runConsole(school, input, null);
            check(Files.readString(existing).equals("keep existing file"), "Export must never overwrite an existing destination");
            check(Files.readString(text).equals(school.generateReport("S1", true, true)), "TXT export matches the exact selected decorated report");
            String csvText = Files.readString(csv);
            check(csvText.contains("S1") && csvText.contains("Software Design Patterns") && csvText.contains("78.00"),
                    "CSV export includes structured student course results");
            check(output.contains("EXPORT FAILED") && output.contains("Choose another file path")
                            && output.contains("Report exported to: " + text), "A failed export retries only the destination path");
            check(school.getGrades().equals(grades) && school.getActivityLog().equals(events), "Exporting reports leaves school data and events unchanged");
        } finally { deleteTree(directory); }
    }

    private static void refusesTheActiveDataPathEvenBeforeItExists() throws Exception {
        SchoolFacade school = demo();
        Path directory = Files.createTempDirectory("school-console-data-path-");
        try {
            Path snapshot = directory.resolve("school.properties");
            Path report = directory.resolve("report.txt");
            Path alias = directory.resolve("alias");
            boolean hasAlias = false;
            try {
                Files.createSymbolicLink(alias, directory);
                hasAlias = true;
            } catch (UnsupportedOperationException | java.io.IOException unsupported) {
                // Some platforms do not grant symbolic-link creation to ordinary users.
            }
            String aliasAttempt = hasAlias ? alias.resolve("school.properties") + "\n" : "";
            String input = "15\nS1\nC1\n" + school.getGrades().get(0).id() + "\n\n100\n\n/cancel\n"
                    + "16\nS1\nn\nn\n1\n" + snapshot + "\n" + aliasAttempt + report + "\n0\n";
            String output = runConsole(school, input, snapshot);
            check(output.contains("different from the school data file"), "The active snapshot path cannot be used for report export");
            if (hasAlias) check(output.split("different from the school data file", -1).length - 1 == 2,
                    "A parent-directory alias cannot bypass the active snapshot guard before the file exists");
            check(Files.readString(report).equals(school.generateReport("S1", false, false)), "A new destination still exports after the guard");
            SchoolFacade restored = new FileSchoolStore().load(snapshot);
            check(restored.getGrades().equals(school.getGrades()), "The active data file remains a valid school snapshot");
            check(output.split("Saved\\.", -1).length - 1 == 1, "Cancelled editing and report export do not trigger an automatic snapshot save");
        } finally { deleteTree(directory); }
    }

    private static void explainsMemoryModeSaving() throws Exception {
        String output = runConsole(new SchoolFacade(), "14\n0\n", null);
        check(output.contains("0 Exit") && !output.contains("0 Save and exit"), "Memory mode labels exit honestly");
        check(output.contains("Saving is unavailable in memory mode") && !output.contains("Saved."),
                "Explicit save explains that memory mode does not persist school data");
    }

    private static void deleteTree(Path directory) throws Exception {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
