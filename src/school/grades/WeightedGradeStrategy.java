package school.grades;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy implementation: each category contributes its configured percentage.
 * A category with no assessments contributes zero; its weight is not redistributed.
 */
public final class WeightedGradeStrategy implements GradeStrategy {
    @Override
    public double calculate(List<Grade> grades, Map<String, Double> weights) {
        GradingRules.validateGrades(grades);
        Map<String, Double> configured = GradingRules.validatedWeights(weights);
        Map<String, BigDecimal> earned = new HashMap<>();
        Map<String, BigDecimal> possible = new HashMap<>();
        for (Grade grade : grades) {
            if (!configured.containsKey(grade.category())) {
                throw new IllegalArgumentException("Unknown weighted category: " + grade.category());
            }
            earned.merge(grade.category(), BigDecimal.valueOf(grade.earned()), BigDecimal::add);
            possible.merge(grade.category(), BigDecimal.valueOf(grade.possible()), BigDecimal::add);
        }
        BigDecimal weightedTotal = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (Map.Entry<String, Double> entry : configured.entrySet()) {
            BigDecimal weight = BigDecimal.valueOf(entry.getValue());
            totalWeight = totalWeight.add(weight);
            if (possible.containsKey(entry.getKey())) {
                BigDecimal fraction = earned.get(entry.getKey())
                        .divide(possible.get(entry.getKey()), MathContext.DECIMAL128);
                weightedTotal = weightedTotal.add(fraction.multiply(weight));
            }
        }
        // Normalizes only tiny accepted rounding differences in a nominal 100% sum.
        return weightedTotal.divide(totalWeight, MathContext.DECIMAL128)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
    }
}
