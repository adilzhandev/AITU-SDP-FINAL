package school.notifications;

@FunctionalInterface
public interface SchoolObserver {
    void onEvent(SchoolEvent event);
}
