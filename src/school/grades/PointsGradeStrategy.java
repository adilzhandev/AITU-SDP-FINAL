package school.grades;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;

/** Strategy implementation: total earned points / total possible points. */
public final class PointsGradeStrategy implements GradeStrategy {
    @Override
    public double calculate(List<Grade> grades, Map<String, Double> weights) {
        GradingRules.validateGrades(grades);
        if (grades.isEmpty()) {
            return 0;
        }
        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal possible = BigDecimal.ZERO;
        for (Grade grade : grades) {
            earned = earned.add(BigDecimal.valueOf(grade.earned()));
            possible = possible.add(BigDecimal.valueOf(grade.possible()));
        }
        return earned.divide(possible, MathContext.DECIMAL128)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
    }
}
