package school.application;

import java.time.LocalDate;
import java.util.Map;
import school.courses.CourseBuilder;
import school.grades.Grade;
import school.grades.GradingMode;
import school.users.Role;

/** Reproducible, fictional data for the defense. Never loads over existing records. */
public final class DemoData {
    private DemoData() { }

    public static void populate(SchoolFacade school) {
        if (!school.getUsers().isEmpty() || !school.getCourses().isEmpty()) {
            throw new IllegalArgumentException("Demo data can only be loaded into an empty school.");
        }
        school.addUser(Role.ADMIN, "A1", "School Administrator", "admin@example.test");
        school.addUser(Role.TEACHER, "T1", "Dana Teacher", "dana@example.test");
        school.addUser(Role.STUDENT, "S1", "Aruzhan Student", "aruzhan@example.test");
        school.addUser(Role.STUDENT, "S2", "Timur Student", "timur@example.test");
        school.addCourse(new CourseBuilder().id("C1").title("Software Design Patterns")
                .teacherId("T1").schedule("Monday 10:00").capacity(25)
                .gradingMode(GradingMode.WEIGHTED).weights(Map.of("homework", 40.0, "exam", 60.0)).build());
        school.addCourse(new CourseBuilder().id("C2").title("Java Programming")
                .teacherId("T1").schedule("Wednesday 14:00").gradingMode(GradingMode.POINTS).build());
        school.enroll("S1", "C1");
        school.enroll("S1", "C2");
        school.enroll("S2", "C1");
        school.recordGrade(new Grade("S1", "C1", "homework", 90, 100, "Clear explanation of the patterns."));
        school.recordGrade(new Grade("S1", "C1", "exam", 70, 100, "Review the Decorator class diagram."));
        school.recordGrade(new Grade("S1", "C2", "homework", 90, 100, "Good Java fundamentals."));
        school.recordGrade(new Grade("S1", "C2", "exam", 70, 100, ""));
        school.recordAttendance(new Attendance("S1", "C1", LocalDate.of(2026, 11, 1), true));
        school.recordAttendance(new Attendance("S1", "C1", LocalDate.of(2026, 11, 2), false));
    }
}
