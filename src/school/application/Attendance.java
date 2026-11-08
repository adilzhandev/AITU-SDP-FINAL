package school.application;

import java.time.LocalDate;
import java.util.Objects;

public record Attendance(String studentId, String courseId, LocalDate date, boolean present) {
    public Attendance {
        if (studentId == null || studentId.isBlank() || courseId == null || courseId.isBlank()) {
            throw new IllegalArgumentException("Student and course IDs are required.");
        }
        studentId = studentId.strip();
        courseId = courseId.strip();
        Objects.requireNonNull(date, "Attendance date is required.");
    }
}
