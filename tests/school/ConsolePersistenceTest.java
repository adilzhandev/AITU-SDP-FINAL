package school;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import school.application.DemoData;
import school.application.SchoolFacade;
import school.storage.FileSchoolStore;
import school.ui.ConsoleMenu;

public final class ConsolePersistenceTest {
    private ConsolePersistenceTest() { }

    public static void run() throws Exception {
        Path blocked = Files.createTempDirectory("school-save-blocked-");
        Path sentinel = blocked.resolve("sentinel.txt");
        Files.writeString(sentinel, "keep");
        SchoolFacade school = new SchoolFacade();
        DemoData.populate(school);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        boolean success;
        try (Scanner scanner = new Scanner("0\n1\n");
             PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            success = new ConsoleMenu(scanner, stream, school, new FileSchoolStore(), blocked).run();
        }
        String text = output.toString(StandardCharsets.UTF_8);
        check(!success, "EOF after failed save must signal failure");
        check(text.contains("SAVE FAILED"), "Save error must be visible");
        check(text.contains("A1 | ADMIN"), "Failed exit must keep the menu running");
        check(Files.readString(sentinel).equals("keep"), "Existing destination is untouched");
        String prefix = "Recovery snapshot saved to: ";
        String recoveryLine = text.lines().filter(line -> line.startsWith(prefix)).findFirst()
                .orElseThrow(() -> new AssertionError("A recovery file should be written after EOF"));
        Path recovery = Path.of(recoveryLine.substring(prefix.length()));
        try {
            SchoolFacade restored = new FileSchoolStore().load(recovery);
            check(restored.getGrades().equals(school.getGrades()), "Recovery keeps all grades");
            check(restored.generateReport("S1", true, true).equals(school.generateReport("S1", true, true)),
                    "Recovery keeps the composed report");
        } finally {
            Files.deleteIfExists(recovery);
            Files.deleteIfExists(recovery.getParent());
            Files.deleteIfExists(sentinel);
            Files.deleteIfExists(blocked);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
