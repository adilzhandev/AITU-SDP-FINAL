package school;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import school.courses.Course;
import school.courses.CourseBuilder;
import school.grades.Grade;
import school.grades.GradingMode;
import school.grades.GradingStrategies;
import school.grades.PointsGradeStrategy;
import school.grades.WeightedGradeStrategy;

/** Dependency-free behavioral tests; run() is also called by the project test suite. */
public final class CoursesGradesTest {
    private CoursesGradesTest() {
    }

    public static void main(String[] args) {
        run();
        System.out.println("CoursesGradesTest passed.");
    }

    public static void run() {
        buildsImmutableCourses();
        normalizesUnicodeWhitespace();
        rejectsInvalidCourses();
        calculatesDifferentStrategies();
        handlesWeightedCategories();
        rejectsInvalidGrades();
        calculatesLargeFiniteValues();
    }

    private static CourseBuilder validBuilder() {
        return new CourseBuilder().id("C1").title("Software Design Patterns").teacherId("T1");
    }

    private static void buildsImmutableCourses() {
        Course basic = validBuilder().build();
        check(basic.getCapacity() == 30, "Default capacity");
        check(basic.getSchedule().equals("Not scheduled"), "Default schedule");
        check(basic.getGradingMode() == GradingMode.POINTS, "Default grading mode");
        check(basic.getWeights().isEmpty(), "POINTS has no category weights");

        Map<String, Double> input = new LinkedHashMap<>();
        input.put(" Quiz ", 40.0);
        input.put("EXAM", 60.0);
        CourseBuilder builder = validBuilder().gradingMode(GradingMode.WEIGHTED).weights(input);
        input.put("EXAM", 10.0);
        Course weighted = builder.schedule(" Monday 10:00 ").capacity(20).build();
        check(weighted.getWeights().equals(Map.of("quiz", 40.0, "exam", 60.0)),
                "Builder owns its map and normalizes categories");
        check(weighted.getSchedule().equals("Monday 10:00"), "Schedule surrounding whitespace is removed");
        expect(UnsupportedOperationException.class, () -> weighted.getWeights().put("exam", 10.0));
        builder.weights(Map.of("project", 100.0)).title("Changed course");
        check(weighted.getTitle().equals("Software Design Patterns"), "Built course is immutable");
        check(weighted.getWeights().containsKey("quiz"), "Reusing builder cannot change previous course");
    }

    private static void normalizesUnicodeWhitespace() {
        String space = "\u2003";
        Course course = new CourseBuilder().id(space + "C1" + space)
                .title(space + "Design Patterns" + space).teacherId(space + "T1" + space)
                .schedule(space + "Monday" + space).gradingMode(GradingMode.WEIGHTED)
                .weights(Map.of(space + " Quiz " + space, 40.0, space + "EXAM" + space, 60.0))
                .build();
        check(course.getId().equals("C1") && course.getTeacherId().equals("T1"),
                "Unicode whitespace is stripped from IDs for cross-module lookups");
        check(course.getTitle().equals("Design Patterns") && course.getSchedule().equals("Monday"),
                "Unicode whitespace is stripped from course text");
        check(course.getWeights().equals(Map.of("quiz", 40.0, "exam", 60.0)),
                "Unicode whitespace is stripped from weight keys");
        Grade grade = new Grade(space + "S1" + space, space + "C1" + space,
                space + "QUIZ" + space, 10, 10, space + "Good" + space);
        check(grade.studentId().equals("S1") && grade.courseId().equals("C1")
                        && grade.category().equals("quiz") && grade.comment().equals("Good"),
                "Unicode whitespace is stripped from grade IDs, category and comment");
        close(40, new WeightedGradeStrategy().calculate(List.of(grade), course.getWeights()),
                "Unicode-padded category matches normalized builder weights");
        expect(IllegalArgumentException.class,
                () -> validBuilder().gradingMode(GradingMode.WEIGHTED)
                        .weights(Map.of("quiz", 50.0, space + "QUIZ" + space, 50.0)).build());
        expect(IllegalArgumentException.class, () -> validBuilder().id(space).build());
        expect(IllegalArgumentException.class, () -> new Grade("S1", "C1", space, 5, 10, ""));
    }

    private static void rejectsInvalidCourses() {
        expect(IllegalArgumentException.class, () -> new CourseBuilder().build());
        expect(IllegalArgumentException.class, () -> validBuilder().teacherId(" ").build());
        expect(IllegalArgumentException.class, () -> validBuilder().capacity(0).build());
        expect(IllegalArgumentException.class, () -> validBuilder().gradingMode(null).build());
        expect(IllegalArgumentException.class,
                () -> validBuilder().gradingMode(GradingMode.WEIGHTED).build());
        expect(IllegalArgumentException.class,
                () -> validBuilder().gradingMode(GradingMode.WEIGHTED).weights(Map.of("exam", 80.0)).build());
        expect(IllegalArgumentException.class,
                () -> validBuilder().gradingMode(GradingMode.WEIGHTED).weights(Map.of("exam", Double.NaN)).build());
        expect(IllegalArgumentException.class,
                () -> validBuilder().gradingMode(GradingMode.WEIGHTED)
                        .weights(Map.of("exam", 100.0, "quiz", 0.0)).build());
        expect(IllegalArgumentException.class,
                () -> validBuilder().gradingMode(GradingMode.WEIGHTED)
                        .weights(Map.of("Exam", 50.0, " exam ", 50.0)).build());
        expect(IllegalArgumentException.class,
                () -> validBuilder().weights(Map.of("exam", 100.0)).build());
    }

    private static void calculatesDifferentStrategies() {
        List<Grade> grades = List.of(
                new Grade("S1", "C1", "Quiz", 40, 50, " Good "),
                new Grade("S1", "C1", "Exam", 90, 100, null));
        close(86.66666666666667, GradingStrategies.forMode(GradingMode.POINTS)
                .calculate(grades, Map.of()), "Points considers assessment point totals");
        close(86.0, GradingStrategies.forMode(GradingMode.WEIGHTED)
                .calculate(grades, Map.of("quiz", 40.0, "exam", 60.0)),
                "Weighted grading applies configured category shares");
        check(grades.get(0).category().equals("quiz"), "Grade category is normalized");
        check(grades.get(0).comment().equals("Good"), "Comment surrounding whitespace is removed");
        check(grades.get(1).comment().isEmpty(), "Optional null comment becomes empty");
        close(0, new PointsGradeStrategy().calculate(List.of(), Map.of()), "No grades means zero");
        expect(IllegalArgumentException.class, () -> GradingStrategies.forMode(null));
    }

    private static void handlesWeightedCategories() {
        WeightedGradeStrategy strategy = new WeightedGradeStrategy();
        Map<String, Double> weights = Map.of("quiz", 40.0, "exam", 60.0);
        close(0, strategy.calculate(List.of(), weights), "No weighted grades means zero");
        close(40, strategy.calculate(List.of(new Grade("S1", "C1", "quiz", 10, 10, "")), weights),
                "Missing exam contributes zero; quiz is not reweighted to 100%");
        close(20, strategy.calculate(List.of(
                new Grade("S1", "C1", "quiz", 10, 10, ""),
                new Grade("S1", "C1", "quiz", 0, 10, "")), weights),
                "Several assessments share one category");
        expect(IllegalArgumentException.class,
                () -> strategy.calculate(List.of(new Grade("S1", "C1", "project", 10, 10, "")), weights));
        expect(IllegalArgumentException.class, () -> strategy.calculate(List.of(), Map.of("exam", 50.0)));
        close(100, strategy.calculate(List.of(new Grade("S1", "C1", "exam", 10, 10, "")),
                Map.of("exam", 100.0000001)), "Accepted floating-point weight tolerance is normalized");
    }

    private static void rejectsInvalidGrades() {
        expect(IllegalArgumentException.class, () -> new Grade(" ", "C1", "quiz", 5, 10, ""));
        expect(IllegalArgumentException.class, () -> new Grade("S1", null, "quiz", 5, 10, ""));
        expect(IllegalArgumentException.class, () -> new Grade("S1", "C1", " ", 5, 10, ""));
        expect(IllegalArgumentException.class, () -> new Grade("S1", "C1", "quiz", -1, 10, ""));
        expect(IllegalArgumentException.class, () -> new Grade("S1", "C1", "quiz", 11, 10, ""));
        expect(IllegalArgumentException.class, () -> new Grade("S1", "C1", "quiz", 0, 0, ""));
        expect(IllegalArgumentException.class, () -> new Grade("S1", "C1", "quiz", Double.NaN, 10, ""));
        expect(IllegalArgumentException.class,
                () -> new Grade("S1", "C1", "quiz", 5, Double.POSITIVE_INFINITY, ""));
        PointsGradeStrategy strategy = new PointsGradeStrategy();
        expect(IllegalArgumentException.class, () -> strategy.calculate(null, Map.of()));
        List<Grade> nullEntry = new ArrayList<>();
        nullEntry.add(null);
        expect(IllegalArgumentException.class, () -> strategy.calculate(nullEntry, Map.of()));
        expect(IllegalArgumentException.class, () -> strategy.calculate(List.of(
                new Grade("S1", "C1", "quiz", 5, 10, ""),
                new Grade("S2", "C1", "quiz", 5, 10, "")), Map.of()));
    }

    private static void calculatesLargeFiniteValues() {
        List<Grade> grades = List.of(
                new Grade("S1", "C1", "exam", Double.MAX_VALUE / 2, Double.MAX_VALUE, ""),
                new Grade("S1", "C1", "exam", Double.MAX_VALUE / 2, Double.MAX_VALUE, ""));
        close(50, new PointsGradeStrategy().calculate(grades, Map.of()), "Large point sums stay finite");
        close(50, new WeightedGradeStrategy().calculate(grades, Map.of("exam", 100.0)),
                "Large weighted sums stay finite");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void close(double expected, double actual, String message) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void expect(Class<? extends Throwable> type, Runnable operation) {
        try {
            operation.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) {
                return;
            }
            throw new AssertionError("Expected " + type.getSimpleName() + ", got " + failure, failure);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
