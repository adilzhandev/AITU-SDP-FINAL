package school.notifications;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** An observer that delivers messages to the event's student and teacher. */
public final class NotificationCenter implements SchoolObserver {
    private final List<Notification> notifications = new ArrayList<>();

    @Override
    public synchronized void onEvent(SchoolEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        notifications.add(new Notification(event.studentId(), event.message()));
        if (!event.teacherId().equals(event.studentId())) {
            notifications.add(new Notification(event.teacherId(), event.message()));
        }
    }

    public synchronized List<Notification> getNotifications(String recipientId) {
        if (recipientId == null || recipientId.isBlank()) {
            throw new IllegalArgumentException("recipientId must not be blank");
        }
        String recipient = recipientId.strip();
        return notifications.stream()
                .filter(notification -> notification.recipientId().equals(recipient))
                .toList();
    }

    public synchronized List<Notification> getAll() {
        return List.copyOf(notifications);
    }

    /** Loads a saved notification without publishing a new school event. */
    public synchronized void restore(Notification notification) {
        notifications.add(Objects.requireNonNull(notification, "notification must not be null"));
    }
}
