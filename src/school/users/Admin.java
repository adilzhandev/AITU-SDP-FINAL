package school.users;

public final class Admin extends User {
    public Admin(String id, String name, String email) {
        super(id, name, email, Role.ADMIN);
    }
}
