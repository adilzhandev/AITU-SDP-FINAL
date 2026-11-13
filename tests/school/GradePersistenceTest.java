package school;

import school.application.SchoolFacade;
import school.courses.CourseBuilder;
import school.grades.Grade;
import school.grades.GradingMode;
import school.storage.FileSchoolStore;
import school.storage.SchoolFileLock;
import school.users.Role;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

/** Exercises grade ID migration, corrections and rejection of unsafe snapshots. */
public final class GradePersistenceTest {
    private GradePersistenceTest() { }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("GradePersistenceTest passed.");
    }

    public static void run() throws Exception {
        Path directory = Files.createTempDirectory("school-grade-persistence-");
        try {
            FileSchoolStore store = new FileSchoolStore();
            SchoolFacade school = fixture();
            school.recordGrade(new Grade("S1", "C1", "homework", 8, 10, "Original comment"));
            school.recordGrade(new Grade("S1", "C1", "exam", 45, 50, "Exam comment"));
            Path current = directory.resolve("current.properties");
            store.save(school, current);
            Properties legacy = read(current);
            legacy.setProperty("version", "1");
            legacy.remove("grades.0.id");
            legacy.remove("grades.1.id");
            Path oldFile = directory.resolve("legacy.properties");
            write(oldFile, legacy);
            byte[] legacyBytes = Files.readAllBytes(oldFile);

            SchoolFacade migrated = store.load(oldFile);
            check(migrated.getGrades().get(0).id() == 1 && migrated.getGrades().get(1).id() == 2,
                    "Legacy grades receive deterministic IDs in their saved order");
            check(migrated.generateReport("S1", true, true).equals(school.generateReport("S1", true, true)),
                    "Migration preserves the student report");
            check(Arrays.equals(legacyBytes, Files.readAllBytes(oldFile)), "Loading does not rewrite a legacy file");
            migrated.updateGrade(1, "homework", 10, 10, "Исправлено: excellent");
            check(migrated.getGrades().size() == 2, "A correction replaces one assessment");
            near(migrated.calculateFinalGrade("S1", "C1"), 94, "Correction changes weighted result");
            check(migrated.getActivityLog().size() == 3, "Correction is recorded during the session");
            store.save(migrated, oldFile);
            Properties saved = read(oldFile);
            check("2".equals(saved.getProperty("version")), "Saving migrates schema to version 2");
            check("1".equals(saved.getProperty("grades.0.id")), "Assigned IDs are serialized");
            SchoolFacade reloaded = store.load(oldFile);
            check(reloaded.getGrades().equals(migrated.getGrades()), "Edited grades and IDs survive restart");
            near(reloaded.calculateFinalGrade("S1", "C1"), 94, "Edited result survives restart");
            check(reloaded.generateReport("S1", true, true).equals(migrated.generateReport("S1", true, true)),
                    "Edited report survives restart");
            check(reloaded.getActivityLog().size() == 2, "Reload reconstructs current grades, not correction history");
            check(reloaded.recordGrade(new Grade("S1", "C1", "exam", 10, 10, "Next")).id() == 3,
                    "New grade IDs continue after migrated records");

            SchoolFacade explicit = fixture();
            explicit.recordGrade(new Grade(102, "S1", "C1", "homework", 8, 10, ""));
            explicit.recordGrade(new Grade(40, "S1", "C1", "exam", 45, 50, ""));
            store.save(explicit, current);
            SchoolFacade loadedExplicit = store.load(current);
            check(loadedExplicit.findGrade(40).earned() == 45, "Non-sequential IDs survive loading");
            check(loadedExplicit.recordGrade(new Grade("S1", "C1", "exam", 9, 10, "")).id() == 103,
                    "Automatic ID uses maximum loaded ID rather than count or final record");

            Path damaged = directory.resolve("damaged.properties");
            for (String invalid : new String[] {"0", "-1", "not-a-number", "9223372036854775808", "1.0"}) {
                rejectMutation(store, current, damaged, data -> data.setProperty("grades.0.id", invalid));
            }
            rejectMutation(store, current, damaged,
                    data -> data.setProperty("grades.1.id", data.getProperty("grades.0.id")));
            rejectMutation(store, current, damaged, data -> data.remove("grades.0.id"));
            rejectMutation(store, current, damaged, data -> data.setProperty("extra", "unexpected"));
            rejectMutation(store, current, damaged, data -> data.setProperty("version", "1"));
            String validContent = Files.readString(current, StandardCharsets.UTF_8);
            for (String escaped : new String[] {"users.1.name=" + "\\" + "uD800",
                    "users.1.name=" + "\\" + "uDC00", "\\" + "uD800=value"}) {
                Files.writeString(damaged, validContent + "\n" + escaped + "\n", StandardCharsets.UTF_8);
                byte[] before = Files.readAllBytes(damaged);
                expectIOException(() -> store.load(damaged));
                check(Arrays.equals(before, Files.readAllBytes(damaged)), "Invalid Unicode remains untouched");
            }
            rejectDataSymlinks(directory, store, current, school);
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static SchoolFacade fixture() {
        SchoolFacade school = new SchoolFacade();
        school.addUser(Role.TEACHER, "T1", "Teacher", "teacher@school.test");
        school.addUser(Role.STUDENT, "S1", "Әли Ученик", "student@school.test");
        school.addCourse(new CourseBuilder().id("C1").title("Java").teacherId("T1")
                .gradingMode(GradingMode.WEIGHTED).weights(Map.of("homework", 40.0, "exam", 60.0)).build());
        school.enroll("S1", "C1");
        return school;
    }

    private static void rejectDataSymlinks(Path directory, FileSchoolStore store,
                                           Path target, SchoolFacade school) throws Exception {
        Path link = directory.resolve("alias.properties");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (UnsupportedOperationException exception) {
            return; // This filesystem does not implement symbolic links.
        }
        byte[] original = Files.readAllBytes(target);
        expectIOException(() -> store.load(link));
        expectIOException(() -> store.save(school, link));
        expectIOException(() -> { try (SchoolFileLock ignored = SchoolFileLock.acquire(link)) { } });
        check(Files.isSymbolicLink(link), "A rejected data symlink must remain a link");
        check(Arrays.equals(original, Files.readAllBytes(target)), "Symlink operations must preserve the target");
        check(!Files.exists(directory.resolve("alias.properties.lock")), "Rejected aliases must not create a lock file");
        Path dangling = directory.resolve("dangling.properties");
        Files.createSymbolicLink(dangling, Path.of("missing-target.properties"));
        expectIOException(() -> store.load(dangling));
        expectIOException(() -> store.save(school, dangling));
        expectIOException(() -> { try (SchoolFileLock ignored = SchoolFileLock.acquire(dangling)) { } });
        check(Files.isSymbolicLink(dangling), "Dangling links are not replaced or treated as new schools");

        Path parentAlias = directory.resolve("parent-alias");
        Files.createSymbolicLink(parentAlias, directory);
        check(store.load(parentAlias.resolve(target.getFileName())).getGrades().size() == 2,
                "A symbolic link to a parent directory remains supported");
        try (SchoolFileLock ignored = SchoolFileLock.acquire(target)) {
            expectIOException(() -> {
                try (SchoolFileLock duplicate = SchoolFileLock.acquire(parentAlias.resolve(target.getFileName()))) { }
            });
        }
        normalizeBeforeResolvingParentLinks(directory, store);
    }

    private static void normalizeBeforeResolvingParentLinks(Path directory, FileSchoolStore store) throws Exception {
        Path local = Files.createDirectory(directory.resolve("local"));
        Path remote = Files.createDirectories(directory.resolve("remote/sub"));
        Files.createSymbolicLink(local.resolve("link"), remote);
        Path normalizedFile = local.resolve("school.properties");
        Path unnormalizedFile = remote.getParent().resolve("school.properties");
        Path inputPath = local.resolve("link/../school.properties");
        SchoolFacade localSchool = fixture();
        localSchool.recordGrade(new Grade("S1", "C1", "homework", 8, 10, "Local snapshot"));
        SchoolFacade remoteSchool = fixture();
        remoteSchool.recordGrade(new Grade("S1", "C1", "homework", 9, 10, "Different snapshot"));
        store.save(localSchool, normalizedFile);
        store.save(remoteSchool, unnormalizedFile);
        byte[] remoteBytes = Files.readAllBytes(unnormalizedFile);
        try (SchoolFileLock ignored = SchoolFileLock.acquire(inputPath)) {
            expectIOException(() -> {
                try (SchoolFileLock duplicate = SchoolFileLock.acquire(normalizedFile)) { }
            });
            try (SchoolFileLock separate = SchoolFileLock.acquire(unnormalizedFile)) {
                SchoolFacade loaded = store.load(inputPath);
                check(loaded.findGrade(1).earned() == 8,
                        "Load normalizes dot segments before following parent symlinks, matching the lock");
                loaded.updateGrade(1, "homework", 10, 10, "Corrected local snapshot");
                store.save(loaded, inputPath);
                check(store.load(normalizedFile).findGrade(1).earned() == 10,
                        "Save updates the same normalized snapshot selected by load and lock");
                check(Arrays.equals(remoteBytes, Files.readAllBytes(unnormalizedFile)),
                        "The other snapshot behind an unnormalized parent symlink is not changed");
            }
        }
    }

    private static void rejectMutation(FileSchoolStore store, Path valid, Path damaged,
                                       Consumer<Properties> mutation) throws Exception {
        Properties data = read(valid);
        mutation.accept(data);
        write(damaged, data);
        byte[] before = Files.readAllBytes(damaged);
        expectIOException(() -> store.load(damaged));
        check(Arrays.equals(before, Files.readAllBytes(damaged)), "Rejected snapshot must remain untouched");
    }

    private static Properties read(Path file) throws IOException {
        Properties result = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.load(reader); }
        return result;
    }

    private static void write(Path file, Properties data) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) { data.store(writer, "Test fixture"); }
    }

    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 0.000001, message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectIOException(Action action) throws Exception {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Expected IOException");
    }

    @FunctionalInterface
    private interface Action { void run() throws Exception; }
}
