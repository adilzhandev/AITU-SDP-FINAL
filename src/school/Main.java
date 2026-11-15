package school;

import java.io.Console;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Scanner;
import school.application.DemoData;
import school.application.SchoolFacade;
import school.storage.FileSchoolStore;
import school.storage.SchoolFileLock;
import school.ui.ConsoleMenu;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println("Usage: java -cp out school.Main [--memory | --demo | --data FILE]");
            System.out.println("Default: interactive menu, autosave to data/school.properties.");
            System.out.println("--memory: interactive menu without persistence.");
            System.out.println("--demo: reproducible demonstration; never reads or writes your saved data.");
            return;
        }
        if (args.length == 1 && args[0].equals("--demo")) {
            SchoolFacade school = new SchoolFacade();
            DemoData.populate(school);
            System.out.println("SCHOOL MANAGEMENT SYSTEM - SIX-PATTERN DEMO");
            System.out.println("Factory Method: Student, Teacher, Admin created.");
            System.out.println("Builder: two courses configured; Strategy: weighted 78%, points 80%.\n");
            System.out.println("Facade + Decorator: composed student report\n");
            System.out.print(school.generateReport("S1", true, true));
            System.out.println("\nObserver: student inbox");
            school.getNotifications("S1").forEach(item -> System.out.println("- " + item.message()));
            System.out.println("\nObserver: independent activity log");
            school.getActivityLog().forEach(System.out::println);
            return;
        }
        Path file;
        if (args.length == 0) file = Path.of("data", "school.properties");
        else if (args.length == 1 && args[0].equals("--memory")) file = null;
        else if (args.length == 2 && args[0].equals("--data")) file = Path.of(args[1]);
        else {
            System.err.println("Unknown arguments. Run with --help.");
            System.exit(2);
            return;
        }
        int status = 0;
        try (SchoolFileLock lock = file == null ? null : SchoolFileLock.acquire(file)) {
            FileSchoolStore store = new FileSchoolStore();
            SchoolFacade school = file == null ? new SchoolFacade() : store.load(file);
            try (Scanner input = new Scanner(System.in, inputCharset())) {
                if (!new ConsoleMenu(input, System.out, school, store, file).run()) status = 1;
            }
        } catch (IOException error) {
            System.err.println("Cannot access school data: " + error.getMessage());
            System.err.println("Check the file and other running sessions, or choose --data ANOTHER_FILE / --memory.");
            status = 1;
        }
        if (status != 0) System.exit(status);
    }

    /**
     * An interactive console delivers keystrokes in its own code page (for example
     * IBM866 in a Russian-locale Windows console), so decode input with that charset.
     * Redirected input has no console and is read as UTF-8.
     */
    private static Charset inputCharset() {
        Console console = System.console();
        return console != null ? console.charset() : StandardCharsets.UTF_8;
    }
}
