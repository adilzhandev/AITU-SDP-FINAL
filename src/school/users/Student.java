package school.users;

public final class Student extends User {
    public Student(String id, String name, String email) {
        super(id, name, email, Role.STUDENT);
    }
}
