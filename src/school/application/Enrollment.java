package school.application;

import school.validation.TextRules;

/** A student can be enrolled in a course only once. */
public record Enrollment(String studentId, String courseId) {
    public Enrollment {
        studentId = TextRules.required(studentId, "Student ID", TextRules.ID_MAX);
        courseId = TextRules.required(courseId, "Course ID", TextRules.ID_MAX);
    }
}
