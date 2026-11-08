package school.users;

import java.util.Objects;

/** Creator: validation is shared; subclasses implement the factory method. */
public abstract class UserFactory {
    public final User create(String id, String name, String email) {
        return createUser(
                User.requiredText(id, "id"),
                User.requiredText(name, "name"),
                User.validatedEmail(email));
    }

    protected abstract User createUser(String id, String name, String email);

    /** Selects a creator; concrete creators determine the product they construct. */
    public static UserFactory forRole(Role role) {
        Objects.requireNonNull(role, "role must not be null");
        return switch (role) {
            case STUDENT -> new StudentFactory();
            case TEACHER -> new TeacherFactory();
            case ADMIN -> new AdminFactory();
        };
    }
}
