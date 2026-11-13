package school;

/** Standard-library test runner: assertions throw even without the -ea JVM flag. */
public final class AllTests {
    private AllTests() { }

    public static void main(String[] args) throws Exception {
        UsersNotificationsTest.run();
        System.out.println("PASS Factory Method and Observer");
        CoursesGradesTest.run();
        System.out.println("PASS Builder and Strategy");
        TextValidationTest.run();
        System.out.println("PASS Unicode, email and text validation");
        FacadeReportsTest.run();
        System.out.println("PASS Facade, Decorator and integrated business rules");
        GradeEditingTest.run();
        System.out.println("PASS stable grade IDs and assessment corrections");
        CapacityTest.run();
        System.out.println("PASS snapshot capacity and rejected-mutation integrity");
        StorageTest.run();
        System.out.println("PASS file persistence and corrupt-data protection");
        GradePersistenceTest.run();
        System.out.println("PASS legacy migration, grade IDs and corrected-grade persistence");
        ReportExportTest.run();
        System.out.println("PASS TXT/CSV export and overwrite protection");
        FileLockTest.run();
        System.out.println("PASS session locking (including a second JVM)");
        ConsoleTest.run();
        System.out.println("PASS interactive console and input recovery");
        ConsoleFeaturesTest.run();
        System.out.println("PASS console editing, export, cancellation and field retries");
        ConsolePersistenceTest.run();
        System.out.println("PASS failed-save exit and recovery");
        System.out.println("ALL 13 TEST SUITES PASSED");
    }
}
