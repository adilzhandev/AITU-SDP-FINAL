package school.application;

import java.time.LocalDate;
import java.util.Objects;
import school.validation.TextRules;

public record Attendance(String studentId, String courseId, LocalDate date, boolean present) {
    public Attendance {
        studentId = TextRules.required(studentId, "Student ID", TextRules.ID_MAX);
        courseId = TextRules.required(courseId, "Course ID", TextRules.ID_MAX);
        Objects.requireNonNull(date, "Attendance date is required.");
    }
}
