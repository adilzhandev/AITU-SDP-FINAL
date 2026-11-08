package school.reports;

import java.util.List;
import school.grades.Grade;

public final class CommentsReportDecorator extends ReportDecorator {
    private final List<Grade> grades;

    public CommentsReportDecorator(StudentReport wrapped, List<Grade> grades) {
        super(wrapped);
        this.grades = List.copyOf(grades);
    }

    @Override
    public String render() {
        StringBuilder text = new StringBuilder(wrapped.render()).append("\nTEACHER COMMENTS\n");
        List<Grade> commented = grades.stream().filter(grade -> !grade.comment().isBlank()).toList();
        if (commented.isEmpty()) text.append("No comments.\n");
        for (Grade grade : commented) {
            text.append(grade.courseId()).append(" / ").append(grade.category()).append(": ")
                    .append(grade.comment()).append("\n");
        }
        return text.toString();
    }
}
