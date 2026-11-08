package school.grades;

import java.util.List;
import java.util.Map;

/** Strategy: calculates a percentage in the inclusive range 0..100. */
public interface GradeStrategy {
    double calculate(List<Grade> grades, Map<String, Double> weights);
}
