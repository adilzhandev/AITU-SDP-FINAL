package school.reports;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import school.users.User;

public final class BasicStudentReport implements StudentReport {
    private final User student;
    private final List<CourseResult> results;

    public BasicStudentReport(User student, List<CourseResult> results) {
        this.student = Objects.requireNonNull(student);
        this.results = List.copyOf(results);
    }

    @Override
    public String render() {
        StringBuilder text = new StringBuilder("STUDENT REPORT\n");
        text.append(student.getName()).append(" [").append(student.getId()).append("]\n");
        if (results.isEmpty()) text.append("No enrolled courses.\n");
        for (CourseResult result : results) {
            text.append(result.courseId()).append(" - ").append(result.title()).append(": ");
            if (result.gradeCount() == 0) text.append("not graded");
            else text.append(String.format(Locale.ROOT, "%.2f%%", result.percentage()));
            text.append("\n");
        }
        return text.toString();
    }
}
