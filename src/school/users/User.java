package school.users;

import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable base product created by the user factories. */
public abstract class User {
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+");

    private final String id;
    private final String name;
    private final String email;
    private final Role role;

    protected User(String id, String name, String email, Role role) {
        this.id = requiredText(id, "id");
        this.name = requiredText(name, "name");
        this.email = validatedEmail(email);
        this.role = Objects.requireNonNull(role, "role must not be null");
    }

    public final String getId() {
        return id;
    }

    public final String getName() {
        return name;
    }

    public final String getEmail() {
        return email;
    }

    public final Role getRole() {
        return role;
    }

    static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    static String validatedEmail(String value) {
        String email = requiredText(value, "email");
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("email must have a valid address format");
        }
        return email;
    }
}
