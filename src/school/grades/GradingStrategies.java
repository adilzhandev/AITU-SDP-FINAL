package school.grades;

/** Selects the interchangeable algorithm chosen for a course. */
public final class GradingStrategies {
    private GradingStrategies() {
    }

    public static GradeStrategy forMode(GradingMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Grading mode is required.");
        }
        return switch (mode) {
            case POINTS -> new PointsGradeStrategy();
            case WEIGHTED -> new WeightedGradeStrategy();
        };
    }
}
