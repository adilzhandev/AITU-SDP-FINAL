package school;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import school.application.SchoolFacade;
import school.courses.CourseBuilder;
import school.grades.Grade;
import school.grades.GradingMode;
import school.storage.FileSchoolStore;
import school.users.Role;
import school.validation.TextRules;

/** Regression coverage for values that previously entered memory but could not be saved. */
public final class TextValidationTest {
    private TextValidationTest() { }

    public static void run() throws Exception {
        SchoolFacade school = new SchoolFacade();
        for (String whitespace : new String[] {"\u2003", "\u00a0", "\u202f", "\t", "\n"}) {
            reject(() -> school.addUser(Role.STUDENT, "S1", "Student", "a@sch" + whitespace + "ool.test"));
            reject(() -> school.addUser(Role.STUDENT, "S1", "Student", "st" + whitespace + "udent@school.test"));
        }
        reject(() -> school.addUser(Role.STUDENT, "S1", "Broken\ud800", "s@school.test"));
        reject(() -> school.addUser(Role.STUDENT, "S1", "Broken\udc00", "s@school.test"));
        reject(() -> school.addUser(Role.STUDENT, "S1", "x".repeat(TextRules.NAME_MAX + 1), "s@school.test"));
        check(school.getUsers().isEmpty(), "Invalid text must not add profiles");

        school.addUser(Role.TEACHER, "T1", "Teacher", "t@school.test");
        school.addUser(Role.STUDENT, "S1", "Әлия \ud83c\udf93", "s@school.test");
        school.addCourse(new CourseBuilder().id("C1").title("Математика").teacherId("T1").build());
        school.enroll("S1", "C1");
        String comment = "First line\nSecond: \"excellent\", Әлия \ud83c\udf93";
        Grade grade = school.recordGrade(new Grade("S1", "C1", "exam", 9, 10, comment));
        int events = school.getActivityLog().size();
        reject(() -> school.updateGrade(grade.id(), "exam", 10, 10, "bad\ud800"));
        reject(() -> school.updateGrade(grade.id(), "exam", 10, 10, "x".repeat(TextRules.COMMENT_MAX + 1)));
        check(school.findGrade(grade.id()).equals(grade), "Invalid correction must retain the original grade");
        check(school.getActivityLog().size() == events, "Invalid text must not publish events");

        // Turkish capital dotted I expands to two code units in Locale.ROOT lowercase.
        reject(() -> new Grade("S1", "C1", "\u0130".repeat(TextRules.CATEGORY_MAX), 1, 1, ""));
        reject(() -> new CourseBuilder().id("C2").title("Course").teacherId("T1")
                .gradingMode(GradingMode.WEIGHTED)
                .weights(Map.of("\u0130".repeat(TextRules.CATEGORY_MAX), 100.0)).build());

        Path directory = Files.createTempDirectory("school-unicode-");
        Path snapshot = directory.resolve("school.properties");
        try {
            FileSchoolStore store = new FileSchoolStore();
            store.save(school, snapshot);
            SchoolFacade loaded = store.load(snapshot);
            check(loaded.findUser("S1").getName().equals("Әлия \ud83c\udf93"), "Valid Unicode name survives reload");
            check(loaded.findGrade(grade.id()).comment().equals(comment), "Unicode and multiline comment survive reload");
        } finally {
            Files.deleteIfExists(snapshot);
            Files.deleteIfExists(directory);
        }
    }

    private static void reject(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid text was accepted");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
