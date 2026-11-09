package school;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import school.application.DemoData;
import school.application.SchoolFacade;
import school.storage.FileSchoolStore;
import school.storage.SchoolFileLock;
import school.ui.DesktopSession;

/** Session behavior can be verified without opening a desktop window. */
public final class DesktopSessionTest {
    private DesktopSessionTest() { }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("DesktopSessionTest passed.");
    }

    public static void run() throws Exception {
        Path directory = Files.createTempDirectory("school-desktop-test-");
        try {
            persistentSession(directory.resolve("persistent.properties"));
            failedLoadReleasesLock(directory.resolve("corrupt.properties"));
            failedSaveCanRecoverAndRetry(directory.resolve("blocked.properties"));
            persistentBackupCopy(directory.resolve("backup.properties"));
            memorySession();
        } finally {
            deleteTree(directory);
        }
    }

    private static void persistentSession(Path file) throws Exception {
        String report;
        DesktopSession session = DesktopSession.open(file);
        try {
            check(!session.isDirty(), "New sessions start clean");
            check(session.file().equals(file.toAbsolutePath().normalize()), "Persistent path is explicit");
            DemoData.populate(session.school());
            session.changed();
            check(!session.isDirty(), "Successful autosave clears dirty state");
            report = session.school().generateReport("S1", true, true);
            check(new FileSchoolStore().load(file).generateReport("S1", true, true).equals(report),
                    "Autosave persists the complete composed report");
            expectIOException(() -> DesktopSession.open(file));
            expectIOException(() -> SchoolFileLock.acquire(file));
        } finally {
            session.close();
            session.close();
        }
        expectIOException(session::save);
        try (DesktopSession reopened = DesktopSession.open(file)) {
            check(reopened.school().generateReport("S1", true, true).equals(report),
                    "Closing releases the lock and reopening restores state");
            check(reopened.school().getNotifications("S1").size() == 4,
                    "Restoration does not duplicate notifications");
        }
    }

    private static void failedLoadReleasesLock(Path file) throws Exception {
        Files.writeString(file, "version=unsupported\n");
        expectIOException(() -> DesktopSession.open(file));
        check(Files.readString(file).equals("version=unsupported\n"), "Corrupt data is preserved");
        try (SchoolFileLock ignored = SchoolFileLock.acquire(file)) {
            // A failed open must not leave its lock held.
        }
    }

    private static void failedSaveCanRecoverAndRetry(Path file) throws Exception {
        try (DesktopSession session = DesktopSession.open(file)) {
            Files.createDirectory(file);
            Path sentinel = file.resolve("keep.txt");
            Files.writeString(sentinel, "original data");
            DemoData.populate(session.school());
            expectIOException(session::changed);
            check(session.isDirty(), "A failed autosave retains unsaved state");
            check(Files.readString(sentinel).equals("original data"), "Failed save preserves destination");
            Path recovery = session.saveRecovery();
            try {
                SchoolFacade restored = new FileSchoolStore().load(recovery);
                check(restored.getGrades().equals(session.school().getGrades()), "Recovery includes all grades");
                check(restored.generateReport("S1", true, true)
                                .equals(session.school().generateReport("S1", true, true)),
                        "Recovery preserves reports and attendance");
                check(session.isDirty(), "Recovery does not claim the original file was saved");
            } finally {
                deleteTree(recovery.getParent());
            }
            Files.delete(sentinel);
            Files.delete(file);
            session.saveCopy(file.getParent().resolve(".").resolve(file.getFileName()));
            check(!session.isDirty(), "Copying to the normalized active path reuses its lock and clears dirty state");
            check(new FileSchoolStore().load(file).getGrades().size() == 4, "Retry saves the retained state");
        }
    }

    private static void persistentBackupCopy(Path destination) throws Exception {
        try (DesktopSession session = DesktopSession.open(null)) {
            DemoData.populate(session.school());
            session.changed();
            Files.writeString(destination, "preserve this existing destination");
            try (SchoolFileLock ignored = SchoolFileLock.acquire(destination)) {
                expectIOException(() -> session.saveCopy(destination));
                check(Files.readString(destination).equals("preserve this existing destination"),
                        "A held backup lock prevents overwriting the destination");
            }
            session.saveCopy(destination);
            SchoolFacade restored = new FileSchoolStore().load(destination);
            check(restored.generateReport("S1", true, true)
                            .equals(session.school().generateReport("S1", true, true)),
                    "A chosen backup path preserves the complete report");
            check(restored.getGrades().equals(session.school().getGrades()), "Backup retains every grade");
            check(restored.getNotifications("S1").size() == 4, "Backup restores notifications once");
            check(session.file() == null && session.isDirty(),
                    "A separate copy does not replace the active file or clear its dirty state");
            try (SchoolFileLock ignored = SchoolFileLock.acquire(destination)) {
                // A completed copy releases its destination lock.
            }
        }
    }

    private static void memorySession() throws Exception {
        try (DesktopSession session = DesktopSession.open(null)) {
            check(session.file() == null, "Memory mode has no active file");
            DemoData.populate(session.school());
            session.changed();
            check(session.isDirty(), "Memory changes require an exit decision");
            expectIOException(session::save);
            check(session.isDirty(), "Unavailable in-place saving does not clear dirty state");
            Path recovery = session.saveRecovery();
            try {
                check(new FileSchoolStore().load(recovery).getUsers().size() == 4,
                        "Memory sessions can export a full snapshot");
            } finally {
                deleteTree(recovery.getParent());
            }
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        try (var entries = Files.walk(directory)) {
            for (Path path : entries.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static void expectIOException(IoAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected an IOException");
        } catch (IOException expected) {
            // Expected persistence failure.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface IoAction { void run() throws Exception; }
}
