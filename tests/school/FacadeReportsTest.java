package school;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import school.application.Attendance;
import school.application.DemoData;
import school.application.SchoolFacade;
import school.courses.CourseBuilder;
import school.grades.Grade;
import school.grades.GradingMode;
import school.users.Role;

/** Integration checks across the facade and all six operational design patterns. */
public final class FacadeReportsTest {
    private FacadeReportsTest() { }

    public static void run() throws Exception {
        rejectsDuplicateUsersAndInvalidTeachers();
        enforcesEnrollmentRules();
        rejectedGradesHaveNoSideEffects();
        demoUsesBothStrategiesAndScopedObservers();
        composesEveryReportCombination();
        exposesImmutableSnapshots();
        normalizesUnicodeWhitespaceAcrossModules();
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("FacadeReportsTest passed.");
    }

    private static SchoolFacade schoolWithUsers() {
        SchoolFacade school = new SchoolFacade();
        school.addUser(Role.TEACHER, "T1", "Teacher", "teacher@school.test");
        school.addUser(Role.STUDENT, "S1", "First Student", "first@school.test");
        school.addUser(Role.STUDENT, "S2", "Second Student", "second@school.test");
        school.addUser(Role.ADMIN, "A1", "Administrator", "admin@school.test");
        return school;
    }

    private static CourseBuilder course(String id) {
        return new CourseBuilder().id(id).title("Course " + id).teacherId("T1");
    }

    private static void rejectsDuplicateUsersAndInvalidTeachers() {
        SchoolFacade school = schoolWithUsers();
        var originalUsers = school.getUsers();
        expect(IllegalArgumentException.class,
                () -> school.addUser(Role.TEACHER, " S1 ", "Duplicate ID", "different@school.test"));
        expect(IllegalArgumentException.class,
                () -> school.addUser(Role.STUDENT, "S3", "Duplicate Email", " FIRST@SCHOOL.TEST "));
        expect(IllegalArgumentException.class,
                () -> school.addUser(Role.STUDENT, "S3", "Invalid Email", "invalid"));
        check(school.getUsers().equals(originalUsers), "Rejected users must not change stored users");

        expect(IllegalArgumentException.class, () -> school.addCourse(course("C1").teacherId("S1").build()));
        expect(IllegalArgumentException.class, () -> school.addCourse(course("C1").teacherId("A1").build()));
        expect(IllegalArgumentException.class, () -> school.addCourse(course("C1").teacherId("unknown").build()));
        check(school.getCourses().isEmpty(), "Only a teacher can be assigned to a new course");
        school.addCourse(course("C1").build());
        expect(IllegalArgumentException.class, () -> school.addCourse(course(" C1 ").title("Replacement").build()));
        check(school.getCourses().size() == 1 && school.findCourse("C1").getTitle().equals("Course C1"),
                "Duplicate course IDs must not replace the existing course");
    }

    private static void enforcesEnrollmentRules() {
        SchoolFacade school = schoolWithUsers();
        school.addCourse(course("C1").capacity(1).build());
        expect(IllegalArgumentException.class, () -> school.enroll("T1", "C1"));
        expect(IllegalArgumentException.class, () -> school.enroll("S1", "missing"));
        check(school.getEnrollments().isEmpty(), "Rejected enrollment must not add a record");
        school.enroll(" S1 ", " C1 ");
        expect(IllegalArgumentException.class, () -> school.enroll("S1", "C1"));
        expect(IllegalArgumentException.class, () -> school.enroll("S2", "C1"));
        check(school.getEnrollments().size() == 1, "Duplicate/full enrollment must not exceed capacity");
        check(school.getEnrollments().get(0).studentId().equals("S1"), "Existing enrollment must be preserved");
        expect(IllegalArgumentException.class, () -> school.calculateFinalGrade("S2", "C1"));
    }

    private static void rejectedGradesHaveNoSideEffects() {
        SchoolFacade school = schoolWithUsers();
        school.addCourse(course("C1").gradingMode(GradingMode.WEIGHTED)
                .weights(Map.of("exam", 100.0)).build());
        school.enroll("S1", "C1");
        school.recordGrade(new Grade("S1", "C1", "exam", 80, 100, "First assessment"));
        var gradesBefore = school.getGrades();
        var studentInboxBefore = school.getNotifications("S1");
        var teacherInboxBefore = school.getNotifications("T1");
        var activityBefore = school.getActivityLog();
        expect(IllegalArgumentException.class,
                () -> school.recordGrade(new Grade("S1", "C1", "unknown", 10, 10, "")));
        expect(IllegalArgumentException.class,
                () -> school.recordGrade(new Grade("S2", "C1", "exam", 10, 10, "")));
        expect(IllegalArgumentException.class,
                () -> school.recordGrade(new Grade("S1", "C1", "exam", Double.NaN, 10, "")));
        check(school.getGrades().equals(gradesBefore), "Rejected grade must not change saved grades");
        check(school.getNotifications("S1").equals(studentInboxBefore), "Rejected grade must not notify student");
        check(school.getNotifications("T1").equals(teacherInboxBefore), "Rejected grade must not notify teacher");
        check(school.getActivityLog().equals(activityBefore), "Rejected grade must not enter activity log");
        close(80, school.calculateFinalGrade("S1", "C1"), "Rejected grade must not affect final result");

        school.recordAttendance(new Attendance("S1", "C1", LocalDate.of(2026, 11, 1), true));
        var attendanceBefore = school.getAttendance();
        expect(IllegalArgumentException.class, () -> school.recordAttendance(
                new Attendance("S2", "C1", LocalDate.of(2026, 11, 1), false)));
        check(school.getAttendance().equals(attendanceBefore), "Rejected attendance must preserve previous data");
        school.recordAttendance(new Attendance("S1", "C1", LocalDate.of(2026, 11, 1), false));
        check(school.getAttendance().size() == 1 && !school.getAttendance().get(0).present(),
                "Attendance on the same date must replace the existing record");
    }

    private static void demoUsesBothStrategiesAndScopedObservers() {
        SchoolFacade school = new SchoolFacade();
        DemoData.populate(school);
        close(78, school.calculateFinalGrade("S1", "C1"), "Demo weighted result");
        close(80, school.calculateFinalGrade("S1", "C2"), "Demo points result");
        check(school.getNotifications("S1").size() == 4, "Student receives each of their four grade events");
        check(school.getNotifications("T1").size() == 4, "Responsible teacher receives all four grade events");
        check(school.getNotifications("S2").isEmpty(), "Another enrolled student must not receive S1 events");
        check(school.getNotifications("A1").isEmpty(), "Unrelated admin must not receive grade messages");
        check(school.getActivityLog().size() == 4, "Independent activity observer receives four events");
        check(school.generateReport("S2", false, false).contains("not graded"),
                "An enrolled student without assessments must be shown as not graded");
        var usersBefore = school.getUsers();
        var gradesBefore = school.getGrades();
        expect(IllegalArgumentException.class, () -> DemoData.populate(school));
        check(school.getUsers().equals(usersBefore) && school.getGrades().equals(gradesBefore),
                "Loading demo twice must preserve existing records");
    }

    private static void composesEveryReportCombination() {
        SchoolFacade school = new SchoolFacade();
        DemoData.populate(school);
        String basic = school.generateReport("S1", false, false);
        String withAttendance = school.generateReport("S1", true, false);
        String withComments = school.generateReport("S1", false, true);
        String withBoth = school.generateReport("S1", true, true);
        check(basic.contains("78.00%") && basic.contains("80.00%"), "Base report contains both course results");
        check(!basic.contains("ATTENDANCE") && !basic.contains("TEACHER COMMENTS"), "Base has no optional sections");
        check(withAttendance.startsWith(basic) && withAttendance.contains("Present: 1 / 2")
                && !withAttendance.contains("TEACHER COMMENTS"), "Attendance-only decoration");
        check(withComments.startsWith(basic) && withComments.contains("TEACHER COMMENTS")
                && withComments.contains("Clear explanation of the patterns.")
                && !withComments.contains("ATTENDANCE"), "Comments-only decoration");
        check(withBoth.startsWith(withAttendance) && withBoth.contains("TEACHER COMMENTS"),
                "Both decorators must preserve the base and previously added section");
        check(occurrences(withBoth, "ATTENDANCE") == 1 && occurrences(withBoth, "TEACHER COMMENTS") == 1,
                "Each optional section appears once");
        check(school.generateReport("S2", true, true).contains("No comments."),
                "S2 report must not include S1 teacher comments");
        expect(IllegalArgumentException.class, () -> school.generateReport("T1", true, true));
        school.addUser(Role.STUDENT, "S3", "New Student", "new@school.test");
        check(school.generateReport("S3", true, true).contains("No enrolled courses."),
                "Student with no enrollments still has a valid report");
    }

    private static void exposesImmutableSnapshots() {
        SchoolFacade school = new SchoolFacade();
        DemoData.populate(school);
        var users = school.getUsers();
        var courses = school.getCourses();
        var enrollments = school.getEnrollments();
        var grades = school.getGrades();
        var studentGrades = school.getStudentGrades("S1", "C2");
        var attendance = school.getAttendance();
        var notifications = school.getNotifications("S1");
        var activity = school.getActivityLog();
        for (List<?> snapshot : List.of(users, courses, enrollments, grades, studentGrades,
                attendance, notifications, activity)) {
            expect(UnsupportedOperationException.class, snapshot::clear);
        }
        school.addUser(Role.STUDENT, "S3", "New Student", "new@school.test");
        school.addCourse(course("C3").build());
        school.enroll("S2", "C2");
        school.recordGrade(new Grade("S1", "C2", "quiz", 10, 10, "Later assessment"));
        school.recordAttendance(new Attendance("S1", "C1", LocalDate.of(2026, 11, 1), false));
        check(users.size() == 4 && courses.size() == 2 && enrollments.size() == 3,
                "User/course/enrollment snapshots stay unchanged after later mutations");
        check(grades.size() == 4 && studentGrades.size() == 2 && notifications.size() == 4 && activity.size() == 4,
                "Grade/event snapshots stay unchanged after a later grade");
        check(attendance.size() == 2 && attendance.get(0).present(), "Attendance snapshot preserves the old value");
    }

    private static void normalizesUnicodeWhitespaceAcrossModules() {
        String whitespace = "\u2003";
        SchoolFacade school = new SchoolFacade();
        school.addUser(Role.TEACHER, whitespace + "T1" + whitespace, "Teacher", "teacher@school.test");
        school.addUser(Role.STUDENT, whitespace + "S1" + whitespace, "Student", "student@school.test");
        check(school.findUser(whitespace + "S1" + whitespace).getId().equals("S1"), "User lookup uses normalized ID");
        school.addCourse(course(whitespace + "C1" + whitespace).teacherId(whitespace + "T1" + whitespace)
                .gradingMode(GradingMode.WEIGHTED).weights(Map.of(whitespace + "EXAM" + whitespace, 100.0)).build());
        check(school.findCourse(whitespace + "C1" + whitespace).getId().equals("C1"), "Course ID is canonical");
        school.enroll(whitespace + "S1" + whitespace, whitespace + "C1" + whitespace);
        school.recordGrade(new Grade(whitespace + "S1" + whitespace, whitespace + "C1" + whitespace,
                whitespace + "EXAM" + whitespace, 9, 10, "Unicode input"));
        school.recordAttendance(new Attendance(whitespace + "S1" + whitespace, whitespace + "C1" + whitespace,
                LocalDate.of(2026, 11, 8), true));
        close(90, school.calculateFinalGrade(whitespace + "S1" + whitespace, whitespace + "C1" + whitespace),
                "Normalized IDs and categories work across the complete grade workflow");
        check(school.getNotifications(whitespace + "S1" + whitespace).size() == 1,
                "Notification lookup uses the same canonical student ID");
        check(school.generateReport(whitespace + "S1" + whitespace, true, true).contains("90.00%"),
                "Report resolves Unicode-wrapped student ID");
    }

    private static int occurrences(String text, String target) {
        int count = 0;
        for (int index = text.indexOf(target); index >= 0; index = text.indexOf(target, index + target.length())) count++;
        return count;
    }

    private static void close(double expected, double actual, String message) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) < 0.000001,
                message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Expected " + type.getSimpleName() + ", got " + failure, failure);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
