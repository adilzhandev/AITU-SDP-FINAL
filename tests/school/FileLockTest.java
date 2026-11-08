package school;

import school.storage.SchoolFileLock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Checks session locking without dependencies or changes to school data. */
public final class FileLockTest {
    private FileLockTest() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("--probe")) {
            try (SchoolFileLock ignored = SchoolFileLock.acquire(Path.of(args[1]))) {
                System.out.println("ACQUIRED");
            } catch (IOException exception) {
                if (!exception.getMessage().contains("already in use")) throw exception;
                System.out.println("BLOCKED");
            }
            return;
        }
        run();
        System.out.println("FileLockTest passed.");
    }

    public static void run() throws Exception {
        Path directory = Files.createTempDirectory("school-lock-test-");
        try {
            Path dataFile = directory.resolve("school.properties");
            Path sidecar = directory.resolve("school.properties.lock");
            String original = "Data must remain unchanged: Әли\n";
            Files.writeString(dataFile, original, StandardCharsets.UTF_8);
            SchoolFileLock first = SchoolFileLock.acquire(dataFile);
            try {
                check(Files.exists(sidecar), "Lock sidecar is created");
                expectAlreadyInUse(dataFile);
                Path normalizedAlias = directory.resolve("unused").resolve("..").resolve("school.properties");
                expectAlreadyInUse(normalizedAlias);
                check(probeAnotherProcess(dataFile).equals("BLOCKED"),
                        "Failed same-JVM acquisition must not release the first process lock");
                check(Files.readString(dataFile, StandardCharsets.UTF_8).equals(original),
                        "Locking and failed acquisition must not modify data");
            } finally {
                first.close();
            }
            first.close();
            check(Files.exists(sidecar), "Sidecar must remain after closing");
            check(probeAnotherProcess(dataFile).equals("ACQUIRED"),
                    "A new process can acquire the lock after release");
            try (SchoolFileLock next = SchoolFileLock.acquire(dataFile)) {
                expectAlreadyInUse(dataFile);
            }
            check(Files.readString(dataFile, StandardCharsets.UTF_8).equals(original),
                    "Reacquiring must not modify data");

            Path missingData = directory.resolve("new/subdirectory/new-school.properties");
            try (SchoolFileLock ignored = SchoolFileLock.acquire(missingData)) {
                check(!Files.exists(missingData), "Lock acquisition must not create the data file");
                check(Files.exists(missingData.resolveSibling("new-school.properties.lock")),
                        "Lock acquisition creates required parent directories");
            }
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void expectAlreadyInUse(Path path) throws IOException {
        try (SchoolFileLock unexpected = SchoolFileLock.acquire(path)) {
            throw new AssertionError("A second lock must be rejected");
        } catch (IOException expected) {
            check(expected.getMessage().contains("already in use"),
                    "Second acquisition has an understandable error");
        }
    }

    private static String probeAnotherProcess(Path path) throws Exception {
        String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        Process process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                FileLockTest.class.getName(), "--probe", path.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        check(process.waitFor() == 0, "Lock probe failed: " + output);
        return output;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
