package school;

import java.util.List;

import school.notifications.ActivityLogObserver;
import school.notifications.EventPublisher;
import school.notifications.Notification;
import school.notifications.NotificationCenter;
import school.notifications.SchoolEvent;
import school.notifications.SchoolObserver;
import school.users.Admin;
import school.users.AdminFactory;
import school.users.Role;
import school.users.Student;
import school.users.StudentFactory;
import school.users.Teacher;
import school.users.TeacherFactory;
import school.users.User;
import school.users.UserFactory;

public final class UsersNotificationsTest {
    private UsersNotificationsTest() {
    }

    public static void run() {
        factoriesCreateExpectedProducts();
        userInputIsValidatedAndNormalized();
        observersReceiveIndependentUpdates();
        notificationsStayScopedAndCanBeRestored();
        observerCollectionsAreSnapshots();
        invalidNotificationInputsAreRejected();
    }

    public static void main(String[] args) {
        run();
        System.out.println("UsersNotificationsTest passed");
    }

    private static void factoriesCreateExpectedProducts() {
        check(UserFactory.forRole(Role.STUDENT) instanceof StudentFactory, "Student creator selected");
        check(UserFactory.forRole(Role.TEACHER) instanceof TeacherFactory, "Teacher creator selected");
        check(UserFactory.forRole(Role.ADMIN) instanceof AdminFactory, "Admin creator selected");

        User student = new StudentFactory().create("s1", "Student", "student@school.test");
        User teacher = new TeacherFactory().create("t1", "Teacher", "teacher@school.test");
        User admin = new AdminFactory().create("a1", "Admin", "admin@school.test");
        check(student instanceof Student && student.getRole() == Role.STUDENT, "Student product");
        check(teacher instanceof Teacher && teacher.getRole() == Role.TEACHER, "Teacher product");
        check(admin instanceof Admin && admin.getRole() == Role.ADMIN, "Admin product");
    }

    private static void userInputIsValidatedAndNormalized() {
        UserFactory factory = UserFactory.forRole(Role.STUDENT);
        User user = factory.create(" s1 ", " Ada Student ", " ada@school.test ");
        check(user.getId().equals("s1"), "ID is trimmed");
        check(user.getName().equals("Ada Student"), "Name is trimmed");
        check(user.getEmail().equals("ada@school.test"), "Email is trimmed");
        expectThrows(IllegalArgumentException.class, () -> factory.create(" ", "Ada", "a@b.test"));
        expectThrows(IllegalArgumentException.class, () -> factory.create("s1", null, "a@b.test"));
        for (String email : List.of("invalid", "a@@b.test", "a b@c.test", "a@.test", "a@b.")) {
            expectThrows(IllegalArgumentException.class, () -> factory.create("s1", "Ada", email));
        }
        expectThrows(IllegalArgumentException.class, () -> factory.create("s1", "Ada", null));
        expectThrows(IllegalArgumentException.class, () -> new Student("s1", " ", "a@b.test"));
        expectThrows(NullPointerException.class, () -> UserFactory.forRole(null));
    }

    private static void observersReceiveIndependentUpdates() {
        EventPublisher publisher = new EventPublisher();
        NotificationCenter center = new NotificationCenter();
        ActivityLogObserver activityLog = new ActivityLogObserver();
        publisher.subscribe(center);
        publisher.subscribe(center);
        publisher.subscribe(activityLog);

        SchoolEvent event = new SchoolEvent("s1", "t1", "c1", "Grade recorded: 85/100");
        publisher.publish(event);
        check(center.getAll().size() == 2, "Duplicate subscriptions do not duplicate delivery");
        check(activityLog.getEntries().size() == 1, "Independent log observer receives event");
        check(activityLog.getEntries().get(0).contains("c1"), "Activity identifies course");

        publisher.unsubscribe(center);
        publisher.unsubscribe(center);
        publisher.publish(new SchoolEvent("s1", "t1", "c1", "Grade updated: 90/100"));
        check(center.getAll().size() == 2, "Unsubscribed center receives no new event");
        check(activityLog.getEntries().size() == 2, "Other observer continues receiving events");
    }

    private static void notificationsStayScopedAndCanBeRestored() {
        NotificationCenter center = new NotificationCenter();
        center.onEvent(new SchoolEvent("s1", "t1", "c1", "First grade"));
        center.onEvent(new SchoolEvent("s2", "t1", "c1", "Second grade"));
        check(center.getNotifications("s1").equals(List.of(new Notification("s1", "First grade"))),
                "Student sees only their own message");
        check(center.getNotifications("t1").size() == 2, "Teacher receives both course events");
        check(center.getNotifications("unknown").isEmpty(), "Unrelated user has no messages");

        NotificationCenter restored = new NotificationCenter();
        for (Notification notification : center.getAll()) {
            restored.restore(notification);
        }
        check(restored.getAll().equals(center.getAll()), "Restoration preserves recipients and order");

        center.onEvent(new SchoolEvent("same", "same", "c2", "One recipient"));
        check(center.getNotifications("same").size() == 1, "Same student/teacher ID receives one message");
    }

    private static void observerCollectionsAreSnapshots() {
        NotificationCenter center = new NotificationCenter();
        ActivityLogObserver activityLog = new ActivityLogObserver();
        List<Notification> oldNotifications = center.getAll();
        List<String> oldEntries = activityLog.getEntries();
        SchoolEvent event = new SchoolEvent("s1", "t1", "c1", "Grade recorded");
        center.onEvent(event);
        activityLog.onEvent(event);
        check(oldNotifications.isEmpty() && oldEntries.isEmpty(), "Snapshots do not change later");
        expectThrows(UnsupportedOperationException.class, () -> center.getAll().clear());
        expectThrows(UnsupportedOperationException.class, () -> center.getNotifications("s1").clear());
        expectThrows(UnsupportedOperationException.class, () -> activityLog.getEntries().clear());

        EventPublisher publisher = new EventPublisher();
        int[] deliveryCount = {0};
        SchoolObserver lateObserver = ignored -> deliveryCount[0]++;
        publisher.subscribe(ignored -> publisher.subscribe(lateObserver));
        publisher.publish(event);
        check(deliveryCount[0] == 0, "A new subscription starts with the next event");
        publisher.publish(event);
        check(deliveryCount[0] == 1, "Later event reaches the new observer once");
    }

    private static void invalidNotificationInputsAreRejected() {
        EventPublisher publisher = new EventPublisher();
        NotificationCenter center = new NotificationCenter();
        ActivityLogObserver activityLog = new ActivityLogObserver();
        expectThrows(NullPointerException.class, () -> publisher.subscribe(null));
        expectThrows(NullPointerException.class, () -> publisher.unsubscribe(null));
        expectThrows(NullPointerException.class, () -> publisher.publish(null));
        expectThrows(NullPointerException.class, () -> center.onEvent(null));
        expectThrows(NullPointerException.class, () -> center.restore(null));
        expectThrows(NullPointerException.class, () -> activityLog.onEvent(null));
        expectThrows(IllegalArgumentException.class, () -> center.getNotifications(" "));
        expectThrows(IllegalArgumentException.class, () -> new SchoolEvent("s1", "t1", " ", "Grade"));
        expectThrows(IllegalArgumentException.class, () -> new Notification("s1", " "));
        SchoolEvent event = new SchoolEvent(" s1 ", " t1 ", " c1 ", " Grade ");
        check(event.equals(new SchoolEvent("s1", "t1", "c1", "Grade")), "Event values are trimmed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectThrows(Class<? extends Throwable> expected, Runnable action) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return;
            }
            throw new AssertionError("Expected " + expected.getSimpleName()
                    + " but got " + thrown.getClass().getSimpleName(), thrown);
        }
        throw new AssertionError("Expected " + expected.getSimpleName() + " to be thrown");
    }
}
