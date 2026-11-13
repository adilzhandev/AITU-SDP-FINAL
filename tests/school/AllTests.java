package school;

/** Standard-library test runner: assertions throw even without the -ea JVM flag. */
public final class AllTests {
    private AllTests() { }

    public static void main(String[] args) throws Exception {
        UsersNotificationsTest.run();
        System.out.println("PASS Factory Method and Observer");
        CoursesGradesTest.run();
        System.out.println("PASS Builder and Strategy");
        FacadeReportsTest.run();
        System.out.println("PASS Facade, Decorator and integrated business rules");
        StorageTest.run();
        System.out.println("PASS file persistence and corrupt-data protection");
        FileLockTest.run();
        System.out.println("PASS session locking (including a second JVM)");
        ConsoleTest.run();
        System.out.println("PASS interactive console and input recovery");
        ConsolePersistenceTest.run();
        System.out.println("PASS failed-save exit and recovery");
        System.out.println("ALL 7 TEST SUITES PASSED");
    }
}
