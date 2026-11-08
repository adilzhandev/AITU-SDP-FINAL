package school.notifications;

/** Immutable message addressed to a single school user. */
public record Notification(String recipientId, String message) {
    public Notification {
        if (recipientId == null || recipientId.isBlank()) {
            throw new IllegalArgumentException("recipientId must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        recipientId = recipientId.strip();
        message = message.strip();
    }
}
