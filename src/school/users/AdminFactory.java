package school.users;

public final class AdminFactory extends UserFactory {
    @Override
    protected User createUser(String id, String name, String email) {
        return new Admin(id, name, email);
    }
}
