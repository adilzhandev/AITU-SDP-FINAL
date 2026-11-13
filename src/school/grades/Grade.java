package school.grades;

import java.util.Locale;
import school.validation.TextRules;

/** One assessment result. Categories use a stable, case-insensitive identifier. */
public record Grade(long id, String studentId, String courseId, String category,
                    double earned, double possible, String comment) {
    public Grade {
        if (id < 0) throw new IllegalArgumentException("Grade ID must not be negative.");
        studentId = TextRules.required(studentId, "Student ID", TextRules.ID_MAX);
        courseId = TextRules.required(courseId, "Course ID", TextRules.ID_MAX);
        category = TextRules.required(category, "Category", TextRules.CATEGORY_MAX).toLowerCase(Locale.ROOT);
        category = TextRules.required(category, "Category", TextRules.CATEGORY_MAX);
        if (!Double.isFinite(earned) || !Double.isFinite(possible)) {
            throw new IllegalArgumentException("Grade points must be finite numbers.");
        }
        if (possible <= 0 || earned < 0 || earned > possible) {
            throw new IllegalArgumentException(
                    "Possible points must be positive; earned points must be between 0 and possible points.");
        }
        comment = TextRules.optional(comment, "Comment", TextRules.COMMENT_MAX);
    }

    /** New assessments receive a stable positive ID when recorded by the facade. */
    public Grade(String studentId, String courseId, String category,
                 double earned, double possible, String comment) {
        this(0, studentId, courseId, category, earned, possible, comment);
    }
}
