package school.users;

public final class Teacher extends User {
    public Teacher(String id, String name, String email) {
        super(id, name, email, Role.TEACHER);
    }
}
