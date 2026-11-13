package school.users;

import java.util.Objects;
import java.util.regex.Pattern;
import school.validation.TextRules;

/** Immutable base product created by the user factories. */
public abstract class User {
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+",
            Pattern.UNICODE_CHARACTER_CLASS);

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
        return TextRules.required(value, field,
                field.equals("id") ? TextRules.ID_MAX : TextRules.NAME_MAX);
    }

    public static String validatedEmail(String value) {
        String email = TextRules.required(value, "email", TextRules.EMAIL_MAX);
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("email must have a valid address format");
        }
        return email;
    }
}
