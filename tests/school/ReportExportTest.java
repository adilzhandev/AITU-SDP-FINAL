package school;

import school.application.Attendance;
import school.application.SchoolFacade;
import school.courses.CourseBuilder;
import school.grades.Grade;
import school.reports.ExportFormat;
import school.reports.ReportExporter;
import school.storage.FileSchoolStore;
import school.users.Role;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Reads exported artifacts to verify their content, options and non-overwriting behavior. */
public final class ReportExportTest {
    private ReportExportTest() { }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("ReportExportTest passed.");
    }

    public static void run() throws Exception {
        Path directory = Files.createTempDirectory("school-report-export-");
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            SchoolFacade school = fixture();
            var grades = school.getGrades();
            var attendance = school.getAttendance();
            var events = school.getActivityLog();
            var notifications = school.getNotifications("S1");
            ReportExporter exporter = new ReportExporter();
            for (boolean includeAttendance : new boolean[] {false, true}) {
                for (boolean includeComments : new boolean[] {false, true}) {
                    String suffix = includeAttendance + "-" + includeComments;
                    Path textPath = directory.resolve("nested/student-" + suffix + ".txt");
                    Path returned = exporter.export(school, " S1 ", includeAttendance, includeComments,
                            ExportFormat.TXT, textPath);
                    check(returned.equals(textPath.toAbsolutePath().normalize()), "Export returns the saved absolute path");
                    check(Files.readString(textPath, StandardCharsets.UTF_8)
                                    .equals(school.generateReport("S1", includeAttendance, includeComments)),
                            "TXT exactly matches the chosen report decorators");
                    Path csvPath = directory.resolve("student-" + suffix + ".csv");
                    exporter.export(school, "S1", includeAttendance, includeComments, ExportFormat.CSV, csvPath);
                    String csv = Files.readString(csvPath, StandardCharsets.UTF_8);
                    List<List<String>> rows = parseCsv(csv);
                    check(rows.get(0).equals(List.of("section", "student_id", "student_name", "course_id",
                            "course_title", "grade_id", "category", "earned", "possible", "result_percent",
                            "date", "present", "comment")), "CSV exposes a stable structured header");
                    check(rows.stream().allMatch(row -> row.size() == 13), "CSV rows use a uniform column count");
                    check(section(rows, "student").size() == 1, "CSV has one student identity row");
                    check(section(rows, "student").get(0).get(2).equals("'=SUM(1,2)"),
                            "Formula-like student names are prefixed for safe spreadsheet import");
                    List<List<String>> courses = section(rows, "course");
                    check(courses.size() == 2, "CSV includes both graded and ungraded courses");
                    check(courses.get(0).get(4).equals("Қазақ, \"Java\"\nAdvanced"),
                            "UTF-8, commas, quotes and multiline text survive CSV escaping");
                    check(courses.get(0).get(9).equals("80.00"), "Numbers use a decimal point in any locale");
                    check(courses.get(1).get(9).isEmpty(), "An ungraded course has an empty result, not zero");
                    List<List<String>> entries = section(rows, "attendance");
                    check(entries.size() == (includeAttendance ? 2 : 0), "Attendance option controls CSV attendance rows");
                    if (includeAttendance) {
                        check(entries.get(0).get(10).equals("2026-11-01") && entries.get(0).get(11).equals("true"),
                                "Attendance exports real dates and booleans in chronological order");
                    }
                    List<List<String>> comments = section(rows, "comment");
                    check(comments.size() == (includeComments ? 2 : 0), "Comments option exports only nonempty comments");
                    if (includeComments) {
                        check(comments.get(0).get(5).equals(Long.toString(grades.get(0).id())),
                                "Comment rows identify the saved assessment");
                        check(comments.get(0).get(12).equals("Жақсы, \"отлично\"\nSecond line"),
                                "Teacher comments retain Unicode, quotes and newlines");
                        check(comments.get(1).get(12).equals("'@SUM(1,2)"),
                                "Formula-like comments are exported as text");
                        check(comments.get(0).get(7).equals("8.0") && comments.get(0).get(8).equals("10.0"),
                                "Comment rows retain numeric assessment values");
                    }
                    check(rows.stream().skip(1).allMatch(row -> row.get(1).equals("S1")),
                            "Export only includes the requested student's records");
                }
            }
            Path emptyStudentReport = directory.resolve("empty.csv");
            exporter.export(school, "S3", true, true, ExportFormat.CSV, emptyStudentReport);
            check(parseCsv(Files.readString(emptyStudentReport)).size() == 2,
                    "A student without courses still gets a CSV identity row");

            Path protectedFile = directory.resolve("school.properties");
            new FileSchoolStore().save(school, protectedFile);
            byte[] original = Files.readAllBytes(protectedFile);
            for (ExportFormat format : ExportFormat.values()) {
                expectIOException(() -> exporter.export(school, "S1", true, true, format, protectedFile));
            }
            check(Arrays.equals(original, Files.readAllBytes(protectedFile)), "Export cannot overwrite a school snapshot");
            Path existingReport = directory.resolve("student-false-false.csv");
            byte[] reportBytes = Files.readAllBytes(existingReport);
            expectIOException(() -> exporter.export(school, "S1", true, true, ExportFormat.CSV, existingReport));
            check(Arrays.equals(reportBytes, Files.readAllBytes(existingReport)), "Export cannot overwrite an existing report");
            expectIOException(() -> exporter.export(school, "S1", true, true, ExportFormat.TXT, directory));
            expectIOException(() -> exporter.export(school, "S1", true, true, ExportFormat.TXT,
                    protectedFile.resolve("child.txt")));
            rejectLinks(directory, protectedFile, exporter, school);
            Path invalidStudentFile = directory.resolve("invalid-student.txt");
            try {
                exporter.export(school, "T1", true, true, ExportFormat.TXT, invalidStudentFile);
                throw new AssertionError("Teacher must not have a student report");
            } catch (IllegalArgumentException expected) {
                check(!Files.exists(invalidStudentFile), "Invalid export does not create a file");
            }
            check(school.getGrades().equals(grades) && school.getAttendance().equals(attendance)
                            && school.getActivityLog().equals(events) && school.getNotifications("S1").equals(notifications),
                    "Exports and rejected destinations do not mutate grades, attendance or notifications");

            long correctedId = grades.get(0).id();
            school.updateGrade(correctedId, "quiz", 10, 10, "Corrected, 你好");
            Path correctedCsv = directory.resolve("corrected.csv");
            exporter.export(school, "S1", false, true, ExportFormat.CSV, correctedCsv);
            List<List<String>> correctedRows = parseCsv(Files.readString(correctedCsv, StandardCharsets.UTF_8));
            check(section(correctedRows, "course").get(0).get(9).equals("86.67"),
                    "CSV reflects the recalculated result after editing a grade");
            List<String> correctedComment = section(correctedRows, "comment").get(0);
            check(correctedComment.get(5).equals(Long.toString(correctedId))
                            && correctedComment.get(7).equals("10.0")
                            && correctedComment.get(12).equals("Corrected, 你好"),
                    "CSV reflects the corrected grade values and comment with its stable ID");
            Path correctedText = directory.resolve("corrected.txt");
            exporter.export(school, "S1", false, true, ExportFormat.TXT, correctedText);
            String correctedReport = Files.readString(correctedText, StandardCharsets.UTF_8);
            check(correctedReport.equals(school.generateReport("S1", false, true))
                            && !correctedReport.contains("Second line"),
                    "TXT exports current comments without retaining replaced text");
            try (var files = Files.walk(directory)) {
                check(files.noneMatch(path -> path.getFileName().toString().startsWith(".school-report-")),
                        "Exports leave no temporary files");
            }
        } finally {
            Locale.setDefault(previous);
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static SchoolFacade fixture() {
        SchoolFacade school = new SchoolFacade();
        school.addUser(Role.TEACHER, "T1", "Teacher", "teacher@school.test");
        school.addUser(Role.STUDENT, "S1", "=SUM(1,2)", "student@school.test");
        school.addUser(Role.STUDENT, "S2", "Other student", "other@school.test");
        school.addUser(Role.STUDENT, "S3", "No enrollment", "empty@school.test");
        school.addCourse(new CourseBuilder().id("C1").title("Қазақ, \"Java\"\nAdvanced").teacherId("T1").build());
        school.addCourse(new CourseBuilder().id("C2").title("Ungraded").teacherId("T1").build());
        school.enroll("S1", "C1");
        school.enroll("S1", "C2");
        school.enroll("S2", "C1");
        school.recordGrade(new Grade("S1", "C1", "quiz", 8, 10, "Жақсы, \"отлично\"\nSecond line"));
        school.recordGrade(new Grade("S1", "C1", "quiz", 8, 10, "@SUM(1,2)"));
        school.recordGrade(new Grade("S1", "C1", "quiz", 8, 10, ""));
        school.recordGrade(new Grade("S2", "C1", "quiz", 10, 10, "Another student's comment"));
        school.recordAttendance(new Attendance("S1", "C1", LocalDate.of(2026, 11, 2), false));
        school.recordAttendance(new Attendance("S1", "C1", LocalDate.of(2026, 11, 1), true));
        school.recordAttendance(new Attendance("S2", "C1", LocalDate.of(2026, 11, 1), true));
        return school;
    }

    private static void rejectLinks(Path directory, Path protectedFile,
                                    ReportExporter exporter, SchoolFacade school) throws Exception {
        Path alias = directory.resolve("linked-report.txt");
        try {
            Files.createSymbolicLink(alias, protectedFile.getFileName());
        } catch (UnsupportedOperationException | IOException exception) {
            // Some platforms (e.g. Windows without Developer Mode) do not let
            // ordinary users create symbolic links; skip these checks there.
            return;
        }
        byte[] before = Files.readAllBytes(protectedFile);
        expectIOException(() -> exporter.export(school, "S1", true, true, ExportFormat.TXT, alias));
        check(Files.isSymbolicLink(alias) && Arrays.equals(before, Files.readAllBytes(protectedFile)),
                "Existing symbolic link and its target remain unchanged");
        Path dangling = directory.resolve("dangling-report.txt");
        Files.createSymbolicLink(dangling, Path.of("no-report.txt"));
        expectIOException(() -> exporter.export(school, "S1", true, true, ExportFormat.TXT, dangling));
        check(Files.isSymbolicLink(dangling) && !Files.exists(directory.resolve("no-report.txt")),
                "Dangling report links are not followed or replaced");
    }

    private static List<List<String>> section(List<List<String>> rows, String section) {
        return rows.stream().filter(row -> row.get(0).equals(section)).toList();
    }

    /** Minimal independent CSV reader used to verify cells, including embedded line breaks. */
    private static List<List<String>> parseCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char character = csv.charAt(i);
            if (character == '"') {
                if (quoted && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (!quoted && character == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (!quoted && character == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                row.add(cell.toString());
                rows.add(List.copyOf(row));
                row.clear();
                cell.setLength(0);
                i++;
            } else cell.append(character);
        }
        check(!quoted && cell.isEmpty() && row.isEmpty(), "CSV terminates complete rows and quoted cells");
        return rows;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectIOException(Action action) throws Exception {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Expected IOException");
    }

    @FunctionalInterface
    private interface Action { void run() throws Exception; }
}
