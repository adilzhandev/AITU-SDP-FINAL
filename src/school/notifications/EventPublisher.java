package school.notifications;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Synchronous Observer publisher with stable subscription order. */
public final class EventPublisher {
    private final Set<SchoolObserver> observers = new LinkedHashSet<>();

    public synchronized void subscribe(SchoolObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer must not be null"));
    }

    public synchronized void unsubscribe(SchoolObserver observer) {
        observers.remove(Objects.requireNonNull(observer, "observer must not be null"));
    }

    public void publish(SchoolEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        List<SchoolObserver> currentObservers;
        synchronized (this) {
            currentObservers = List.copyOf(observers);
        }
        for (SchoolObserver observer : currentObservers) {
            observer.onEvent(event);
        }
    }
}
