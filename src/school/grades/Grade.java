package school.grades;

import java.util.Locale;

/** One assessment result. Categories use a stable, case-insensitive identifier. */
public record Grade(String studentId, String courseId, String category,
                    double earned, double possible, String comment) {
    public Grade {
        studentId = required(studentId, "Student ID");
        courseId = required(courseId, "Course ID");
        category = required(category, "Category").toLowerCase(Locale.ROOT);
        if (!Double.isFinite(earned) || !Double.isFinite(possible)) {
            throw new IllegalArgumentException("Grade points must be finite numbers.");
        }
        if (possible <= 0 || earned < 0 || earned > possible) {
            throw new IllegalArgumentException(
                    "Possible points must be positive; earned points must be between 0 and possible points.");
        }
        comment = comment == null ? "" : comment.strip();
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.strip();
    }
}
