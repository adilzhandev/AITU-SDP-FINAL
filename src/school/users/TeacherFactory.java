package school.users;

public final class TeacherFactory extends UserFactory {
    @Override
    protected User createUser(String id, String name, String email) {
        return new Teacher(id, name, email);
    }
}
