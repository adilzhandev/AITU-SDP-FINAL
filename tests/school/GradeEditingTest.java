package school;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import school.application.SchoolFacade;
import school.courses.CourseBuilder;
import school.grades.Grade;
import school.grades.GradingMode;
import school.storage.FileSchoolStore;
import school.users.Role;

/** Stable assessment identity and atomic corrections through the facade. */
public final class GradeEditingTest {
    private GradeEditingTest() { }

    public static void run() throws Exception {
        correctsOneAssessmentAndNotifiesOnlyItsRecipients();
        invalidCorrectionsHaveNoEffects();
        assignsStableIdsAndPreservesThemAcrossReload();
        handlesIdExhaustionWithoutWrapping();
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("GradeEditingTest passed.");
    }

    private static SchoolFacade school() {
        SchoolFacade school = new SchoolFacade();
        school.addUser(Role.TEACHER, "T1", "Teacher", "teacher@school.test");
        school.addUser(Role.STUDENT, "S1", "First Student", "first@school.test");
        school.addUser(Role.STUDENT, "S2", "Second Student", "second@school.test");
        school.addCourse(new CourseBuilder().id("W").title("Weighted Course").teacherId("T1")
                .gradingMode(GradingMode.WEIGHTED).weights(Map.of("quiz", 40.0, "exam", 60.0)).build());
        school.addCourse(new CourseBuilder().id("P").title("Points Course").teacherId("T1").build());
        school.enroll("S1", "W");
        school.enroll("S1", "P");
        school.enroll("S2", "P");
        return school;
    }

    private static void correctsOneAssessmentAndNotifiesOnlyItsRecipients() {
        SchoolFacade school = school();
        Grade quiz = school.recordGrade(new Grade("S1", "W", "quiz", 80, 100, "Old feedback"));
        Grade exam = school.recordGrade(new Grade("S1", "W", "exam", 50, 100, "Exam"));
        Grade other = school.recordGrade(new Grade("S2", "P", "quiz", 5, 10, "Other student"));
        List<Grade> before = school.getGrades();
        List<Grade> pairBefore = school.getStudentGrades("S1", "W");
        int eventsBefore = school.getActivityLog().size();
        int studentBefore = school.getNotifications("S1").size();
        int teacherBefore = school.getNotifications("T1").size();
        int otherBefore = school.getNotifications("S2").size();

        Grade corrected = school.updateGrade(quiz.id(), " QUIZ ", 90, 100, "Corrected feedback");
        check(corrected.id() == quiz.id() && corrected.studentId().equals("S1")
                && corrected.courseId().equals("W"), "Correction retains assessment identity and enrollment");
        check(school.findGrade(quiz.id()).equals(corrected), "ID lookup returns corrected assessment");
        check(school.getGrades().equals(List.of(corrected, exam, other)), "Correction preserves global order and count");
        check(school.getStudentGrades(" S1 ", " W ").equals(List.of(corrected, exam)),
                "Enrollment index replaces exactly one assessment");
        check(before.equals(List.of(quiz, exam, other)) && pairBefore.equals(List.of(quiz, exam)),
                "Previously returned snapshots remain unchanged");
        close(66, school.calculateFinalGrade("S1", "W"), "Weighted result uses corrected score");
        close(50, school.calculateFinalGrade("S2", "P"), "Another student is unchanged");
        check(school.getActivityLog().size() == eventsBefore + 1
                && school.getNotifications("S1").size() == studentBefore + 1
                && school.getNotifications("T1").size() == teacherBefore + 1
                && school.getNotifications("S2").size() == otherBefore,
                "Correction publishes one event to the assessment's student and teacher only");
        check(school.getActivityLog().get(eventsBefore).contains("grade #" + quiz.id())
                && school.getActivityLog().get(eventsBefore).contains("corrected"),
                "Correction notification identifies the edited assessment");
        String report = school.generateReport("S1", false, true);
        check(report.contains("Corrected feedback") && !report.contains("Old feedback"),
                "Reports use the replacement comment");

        Grade points = school.recordGrade(new Grade("S1", "P", "quiz", 2, 10, ""));
        school.updateGrade(points.id(), "exam", 16, 20, null);
        close(80, school.calculateFinalGrade("S1", "P"), "Points correction uses the new denominator");
    }

    private static void invalidCorrectionsHaveNoEffects() {
        SchoolFacade school = school();
        Grade original = school.recordGrade(new Grade("S1", "W", "exam", 70, 100, "Keep"));
        var grades = school.getGrades();
        var pair = school.getStudentGrades("S1", "W");
        var student = school.getNotifications("S1");
        var teacher = school.getNotifications("T1");
        var events = school.getActivityLog();
        expectInvalid(() -> school.updateGrade(999, "exam", 50, 100, ""));
        expectInvalid(() -> school.updateGrade(0, "exam", 50, 100, ""));
        expectInvalid(() -> school.updateGrade(original.id(), "missing", 50, 100, ""));
        expectInvalid(() -> school.updateGrade(original.id(), "exam", 101, 100, ""));
        expectInvalid(() -> school.updateGrade(original.id(), "exam", 1, 0, ""));
        expectInvalid(() -> school.updateGrade(original.id(), "exam", Double.NaN, 100, ""));
        expectInvalid(() -> school.updateGrade(original.id(), "exam", 50, Double.POSITIVE_INFINITY, ""));
        expectInvalid(() -> school.updateGrade(original.id(), "exam", 50, 100, "x".repeat(4001)));
        expectInvalid(() -> school.updateGrade(original.id(), "exam", 50, 100, "bad\uD800"));
        expectInvalid(() -> school.recordGrade(new Grade(original.id(), "S2", "P", "quiz", 1, 10, "")));
        expectInvalid(() -> new Grade(-1, "S1", "W", "exam", 1, 10, ""));
        check(school.getGrades().equals(grades) && school.getStudentGrades("S1", "W").equals(pair),
                "Rejected corrections and duplicate IDs preserve both indexes");
        check(school.getNotifications("S1").equals(student) && school.getNotifications("T1").equals(teacher)
                && school.getActivityLog().equals(events), "Rejected corrections do not publish events");
        close(42, school.calculateFinalGrade("S1", "W"), "Rejected edits preserve the weighted result");
        Grade next = school.recordGrade(new Grade("S1", "W", "quiz", 1, 10, ""));
        check(next.id() == original.id() + 1, "Rejected operations do not consume automatic IDs");
    }

    private static void assignsStableIdsAndPreservesThemAcrossReload() throws Exception {
        SchoolFacade school = school();
        Grade first = school.recordGrade(new Grade("S1", "P", "quiz", 1, 10, ""));
        Grade imported = school.recordGrade(new Grade(40, "S1", "P", "exam", 9, 10, "Imported"));
        Grade next = school.recordGrade(new Grade("S2", "P", "quiz", 7, 10, ""));
        check(first.id() == 1 && imported.id() == 40 && next.id() == 41,
                "Automatic IDs start at one and advance past imported IDs");
        school.updateGrade(imported.id(), "exam", 10, 10, "Fixed");
        Path path = Files.createTempFile("school-grade-identities-", ".properties");
        try {
            FileSchoolStore store = new FileSchoolStore();
            store.save(school, path);
            SchoolFacade loaded = store.load(path);
            check(loaded.getGrades().equals(school.getGrades()), "Reload preserves IDs, order and corrections");
            check(loaded.findGrade(40).comment().equals("Fixed"), "Reloaded ID lookup finds the corrected grade");
            Grade added = loaded.recordGrade(new Grade("S1", "P", "quiz", 10, 10, "After reload"));
            check(added.id() == 42, "Reloaded allocator continues beyond the highest saved ID");
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private static void handlesIdExhaustionWithoutWrapping() {
        SchoolFacade school = school();
        school.recordGrade(new Grade(Long.MAX_VALUE, "S1", "P", "exam", 1, 1, ""));
        var grades = school.getGrades();
        var events = school.getActivityLog();
        expectInvalid(() -> school.recordGrade(new Grade("S1", "P", "quiz", 1, 1, "")));
        check(school.getGrades().equals(grades) && school.getActivityLog().equals(events),
                "ID exhaustion fails before mutation without integer wraparound");
        school.updateGrade(Long.MAX_VALUE, "exam", 0, 1, "Corrected");
        check(school.findGrade(Long.MAX_VALUE).earned() == 0, "Existing assessments remain editable after ID exhaustion");
    }

    private static void expectInvalid(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    private static void close(double expected, double actual, String message) {
        check(Math.abs(expected - actual) < 0.000001, message + ": " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
