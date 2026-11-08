package school.notifications;

/** An event concerning one student and the teacher responsible for a course. */
public record SchoolEvent(String studentId, String teacherId, String courseId, String message) {
    public SchoolEvent {
        studentId = requiredText(studentId, "studentId");
        teacherId = requiredText(teacherId, "teacherId");
        courseId = requiredText(courseId, "courseId");
        message = requiredText(message, "message");
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
