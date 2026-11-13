package school;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import school.application.Attendance;
import school.application.SchoolFacade;
import school.courses.CourseBuilder;
import school.grades.Grade;
import school.storage.FileSchoolStore;
import school.users.Role;
import school.validation.TextRules;

/** Capacity is checked before every mutation so valid in-memory data can be saved. */
public final class CapacityTest {
    private CapacityTest() { }

    public static void run() throws Exception {
        rejectsOversizedCommentsBeforeMutation();
        rejectsFullSnapshotMutationsAndAllowsRecoveryByEditing();
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("CapacityTest passed.");
    }

    private static SchoolFacade school() {
        SchoolFacade school = new SchoolFacade();
        school.addUser(Role.TEACHER, "T1", "Teacher", "teacher@school.test");
        school.addUser(Role.STUDENT, "S1", "Student", "student@school.test");
        school.addUser(Role.STUDENT, "S2", "Second Student", "second@school.test");
        school.addCourse(new CourseBuilder().id("C1").title("Course").teacherId("T1").build());
        school.enroll("S1", "C1");
        school.recordAttendance(new Attendance("S1", "C1", LocalDate.of(2026, 11, 1), true));
        return school;
    }

    private static void rejectsOversizedCommentsBeforeMutation() {
        SchoolFacade school = school();
        expectInvalid(() -> school.recordGrade(new Grade("S1", "C1", "exam", 1, 1,
                "x".repeat(TextRules.COMMENT_MAX + 1))));
        check(school.getGrades().isEmpty() && school.getActivityLog().isEmpty()
                && school.getNotifications("S1").isEmpty(), "Oversized comments cannot create unsavable records/events");
        Grade grade = school.recordGrade(new Grade("S1", "C1", "exam", 1, 1,
                "x".repeat(TextRules.COMMENT_MAX)));
        check(grade.id() == 1 && grade.comment().length() == TextRules.COMMENT_MAX,
                "The documented comment boundary remains usable");
    }

    private static void rejectsFullSnapshotMutationsAndAllowsRecoveryByEditing() throws Exception {
        SchoolFacade school = school();
        // Mix Unicode, supplementary code points, backslashes, punctuation and newlines
        // to exercise the same escaping/UTF-8 costs used by Properties.store(Writer).
        String comment = "界😀\\=\n!".repeat(500);
        int count = 0;
        while (true) {
            try {
                school.recordGrade(new Grade("S1", "C1", "exam", 8, 10, comment));
                count++;
            } catch (IllegalArgumentException full) {
                check(full.getMessage().contains("10 MiB"), "Large-comment loop reaches the snapshot budget");
                break;
            }
            check(count < 5000, "Capacity is enforced well before unbounded memory growth");
        }
        check(count > 100, "Quota permits realistic collections rather than just one record");

        // Consume remaining space with small records, then single-byte comment increments.
        Grade last = school.getGrades().get(school.getGrades().size() - 1);
        while (true) {
            try {
                last = school.recordGrade(new Grade("S1", "C1", "exam", 8, 10, ""));
            } catch (IllegalArgumentException full) {
                check(full.getMessage().contains("10 MiB"), "Small records stop at the same limit");
                break;
            }
        }
        // There is always at least one small record: a large comment costs far more than one record.
        check(last.comment().isEmpty(), "A small assessment fits the large-record remainder");
        long lastId = last.id();
        int padding = 0;
        while (true) {
            try {
                school.updateGrade(lastId, "exam", 8, 10, ".".repeat(padding + 1));
                padding++;
            } catch (IllegalArgumentException full) {
                check(full.getMessage().contains("10 MiB"), "One-byte edit detects the exact budget boundary");
                break;
            }
            check(padding < TextRules.COMMENT_MAX, "Remaining quota is smaller than one empty assessment");
        }

        var users = school.getUsers();
        var courses = school.getCourses();
        var enrollments = school.getEnrollments();
        var grades = school.getGrades();
        var attendance = school.getAttendance();
        var events = school.getActivityLog();
        var studentInbox = school.getNotifications("S1");
        var teacherInbox = school.getNotifications("T1");
        Grade first = grades.get(0);
        expectQuota(() -> school.addUser(Role.STUDENT, "S3", "Third", "third@school.test"));
        expectQuota(() -> school.addCourse(new CourseBuilder().id("C2").title("Second Course").teacherId("T1").build()));
        expectQuota(() -> school.enroll("S2", "C1"));
        expectQuota(() -> school.recordGrade(new Grade("S1", "C1", "exam", 10, 10, "")));
        expectQuota(() -> school.updateGrade(first.id(), "exam", 8, 10, comment + "."));
        expectQuota(() -> school.recordAttendance(new Attendance("S1", "C1", LocalDate.of(2026, 11, 2), true)));
        expectQuota(() -> school.recordAttendance(new Attendance("S1", "C1", LocalDate.of(2026, 11, 1), false)));
        check(school.getUsers().equals(users) && school.getCourses().equals(courses)
                && school.getEnrollments().equals(enrollments) && school.getGrades().equals(grades)
                && school.getAttendance().equals(attendance), "Every quota-rejected mutation preserves all domain data");
        check(school.getStudentGrades("S1", "C1").equals(grades)
                && school.getActivityLog().equals(events)
                && school.getNotifications("S1").equals(studentInbox)
                && school.getNotifications("T1").equals(teacherInbox), "Quota failures preserve indexes and observers");
        check(Math.abs(school.calculateFinalGrade("S1", "C1") - 80) < 0.000001,
                "Quota rejection leaves final results unchanged");

        Path path = Files.createTempFile("school-capacity-", ".properties");
        try {
            FileSchoolStore store = new FileSchoolStore();
            store.save(school, path);
            check(Files.size(path) <= TextRules.MAX_FILE_BYTES, "Accepted state fits the snapshot byte limit");
            SchoolFacade loaded = store.load(path);
            check(loaded.getGrades().equals(grades), "Full snapshot is reloadable with stable assessment IDs");

            school.updateGrade(first.id(), "exam", 8, 10, "Shortened");
            Grade added = school.recordGrade(new Grade("S1", "C1", "exam", 8, 10, "New assessment"));
            check(added.id() == lastId + 1, "Quota rejection does not consume an assessment ID");
            school.addUser(Role.STUDENT, "S3", "Third", "third@school.test");
            school.addCourse(new CourseBuilder().id("C2").title("Second Course").teacherId("T1").build());
            school.enroll("S2", "C1");
            school.recordAttendance(new Attendance("S1", "C1", LocalDate.of(2026, 11, 1), false));
            check(school.getUsers().size() == users.size() + 1 && school.getCourses().size() == courses.size() + 1
                    && school.getEnrollments().size() == enrollments.size() + 1,
                    "A shorter edit frees quota and failed inserts did not contaminate secondary indexes");
            store.save(school, path);
            check(store.load(path).getGrades().equals(school.getGrades()), "Recovered state still saves and reloads");
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private static void expectQuota(Runnable action) {
        IllegalArgumentException error = expectInvalid(action);
        check(error.getMessage().contains("10 MiB"), "Expected snapshot quota rejection: " + error.getMessage());
    }

    private static IllegalArgumentException expectInvalid(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return expected;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
