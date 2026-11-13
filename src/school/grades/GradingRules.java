package school.grades;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import school.validation.TextRules;

/** Shared validation used by the course builder and the weighted strategy. */
public final class GradingRules {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.000001");

    private GradingRules() {
    }

    /** Returns an immutable, normalized copy; the caller's map is never modified. */
    public static Map<String, Double> validatedWeights(Map<String, Double> weights) {
        if (weights == null || weights.isEmpty()) {
            throw new IllegalArgumentException("Weighted grading requires category weights.");
        }
        if (weights.size() > TextRules.MAX_WEIGHTS) {
            throw new IllegalArgumentException("Too many weight categories.");
        }
        Map<String, Double> normalized = new TreeMap<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            String category = entry.getKey();
            Double weight = entry.getValue();
            category = TextRules.required(category, "Weight category", TextRules.CATEGORY_MAX)
                    .toLowerCase(Locale.ROOT);
            category = TextRules.required(category, "Weight category", TextRules.CATEGORY_MAX);
            if (weight == null || !Double.isFinite(weight) || weight <= 0) {
                throw new IllegalArgumentException("Category weights must be positive finite numbers.");
            }
            if (normalized.putIfAbsent(category, weight) != null) {
                throw new IllegalArgumentException("Duplicate weight category: " + category);
            }
            sum = sum.add(BigDecimal.valueOf(weight));
        }
        if (sum.subtract(HUNDRED).abs().compareTo(WEIGHT_TOLERANCE) > 0) {
            throw new IllegalArgumentException("Category weights must add up to 100%.");
        }
        return Collections.unmodifiableMap(normalized);
    }

    static void validateGrades(List<Grade> grades) {
        if (grades == null) {
            throw new IllegalArgumentException("Grades must not be null.");
        }
        String studentId = null;
        String courseId = null;
        for (Grade grade : grades) {
            if (grade == null) {
                throw new IllegalArgumentException("Grades must not contain null entries.");
            }
            if (studentId == null) {
                studentId = grade.studentId();
                courseId = grade.courseId();
            } else if (!studentId.equals(grade.studentId()) || !courseId.equals(grade.courseId())) {
                throw new IllegalArgumentException("Calculate a grade for one student and one course at a time.");
            }
        }
    }
}
