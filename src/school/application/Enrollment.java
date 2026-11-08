package school.application;

/** A student can be enrolled in a course only once. */
public record Enrollment(String studentId, String courseId) {
    public Enrollment {
        if (studentId == null || studentId.isBlank() || courseId == null || courseId.isBlank()) {
            throw new IllegalArgumentException("Student and course IDs are required.");
        }
        studentId = studentId.strip();
        courseId = courseId.strip();
    }
}
