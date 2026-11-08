package school.courses;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import school.grades.GradingMode;

/** Immutable product of CourseBuilder. */
public final class Course {
    private final String id;
    private final String title;
    private final String teacherId;
    private final String schedule;
    private final GradingMode gradingMode;
    private final int capacity;
    private final Map<String, Double> weights;

    Course(String id, String title, String teacherId, String schedule,
           GradingMode gradingMode, int capacity, Map<String, Double> weights) {
        this.id = id;
        this.title = title;
        this.teacherId = teacherId;
        this.schedule = schedule;
        this.gradingMode = gradingMode;
        this.capacity = capacity;
        this.weights = Collections.unmodifiableMap(new TreeMap<>(weights));
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getTeacherId() { return teacherId; }
    public String getSchedule() { return schedule; }
    public GradingMode getGradingMode() { return gradingMode; }
    public int getCapacity() { return capacity; }
    public Map<String, Double> getWeights() { return weights; }

    @Override
    public String toString() {
        return id + " | " + title + " | teacher=" + teacherId + " | " + gradingMode;
    }
}
