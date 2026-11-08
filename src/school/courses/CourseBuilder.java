package school.courses;

import java.util.LinkedHashMap;
import java.util.Map;
import school.grades.GradingMode;
import school.grades.GradingRules;

/** Builder: assembles a valid course from required and optional configuration. */
public final class CourseBuilder {
    private String id;
    private String title;
    private String teacherId;
    private String schedule = "Not scheduled";
    private GradingMode gradingMode = GradingMode.POINTS;
    private int capacity = 30;
    private Map<String, Double> weights = Map.of();

    public CourseBuilder id(String value) { id = value; return this; }
    public CourseBuilder title(String value) { title = value; return this; }
    public CourseBuilder teacherId(String value) { teacherId = value; return this; }
    public CourseBuilder schedule(String value) { schedule = value; return this; }
    public CourseBuilder gradingMode(GradingMode value) { gradingMode = value; return this; }
    public CourseBuilder capacity(int value) { capacity = value; return this; }

    public CourseBuilder weights(Map<String, Double> value) {
        if (value == null) {
            throw new IllegalArgumentException("Weights must not be null.");
        }
        weights = new LinkedHashMap<>(value);
        return this;
    }

    public Course build() {
        String courseId = required(id, "Course ID");
        String courseTitle = required(title, "Course title");
        String teacher = required(teacherId, "Teacher ID");
        if (gradingMode == null) {
            throw new IllegalArgumentException("Grading mode is required.");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Course capacity must be positive.");
        }
        String courseSchedule = schedule == null || schedule.isBlank()
                ? "Not scheduled" : schedule.strip();
        Map<String, Double> courseWeights;
        if (gradingMode == GradingMode.WEIGHTED) {
            courseWeights = GradingRules.validatedWeights(weights);
        } else {
            if (!weights.isEmpty()) {
                throw new IllegalArgumentException("POINTS grading does not use category weights.");
            }
            courseWeights = Map.of();
        }
        return new Course(courseId, courseTitle, teacher, courseSchedule,
                gradingMode, capacity, courseWeights);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.strip();
    }
}
