package school.notifications;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Independent observer recording one activity entry per published event. */
public final class ActivityLogObserver implements SchoolObserver {
    private final List<String> entries = new ArrayList<>();

    @Override
    public synchronized void onEvent(SchoolEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        entries.add("Course " + event.courseId() + ": " + event.message()
                + " [student=" + event.studentId() + ", teacher=" + event.teacherId() + "]");
    }

    public synchronized List<String> getEntries() {
        return List.copyOf(entries);
    }
}
