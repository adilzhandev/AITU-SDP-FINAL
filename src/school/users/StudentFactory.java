package school.users;

public final class StudentFactory extends UserFactory {
    @Override
    protected User createUser(String id, String name, String email) {
        return new Student(id, name, email);
    }
}
