package school.reports;

import java.util.List;
import school.application.Attendance;

public final class AttendanceReportDecorator extends ReportDecorator {
    private final List<Attendance> attendance;

    public AttendanceReportDecorator(StudentReport wrapped, List<Attendance> attendance) {
        super(wrapped);
        this.attendance = List.copyOf(attendance);
    }

    @Override
    public String render() {
        StringBuilder text = new StringBuilder(wrapped.render()).append("\nATTENDANCE\n");
        if (attendance.isEmpty()) text.append("No attendance recorded.\n");
        else {
            long present = attendance.stream().filter(Attendance::present).count();
            text.append("Present: ").append(present).append(" / ").append(attendance.size()).append("\n");
            for (Attendance entry : attendance) {
                text.append(entry.date()).append(" | ").append(entry.courseId()).append(" | ")
                        .append(entry.present() ? "present" : "absent").append("\n");
            }
        }
        return text.toString();
    }
}
