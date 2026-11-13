package school;

import school.application.Attendance;
import school.application.SchoolFacade;
import school.courses.CourseBuilder;
import school.grades.Grade;
import school.grades.GradingMode;
import school.storage.FileSchoolStore;
import school.users.Role;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

/** Behavioral storage tests requiring no test framework. */
public final class StorageTest {
    private StorageTest() { }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("StorageTest passed.");
    }

    public static void run() throws Exception {
        Path directory = Files.createTempDirectory("school-storage-test-");
        try {
            FileSchoolStore store = new FileSchoolStore();
            Path original = directory.resolve("school.properties");
            check(store.load(directory.resolve("missing.properties")).getUsers().isEmpty(),
                    "Missing file starts an empty school");
            SchoolFacade school = sampleSchool();
            store.save(school, original);
            SchoolFacade loaded = store.load(original);
            checkEquivalent(school, loaded);
            check(loaded.getNotifications("S1").size() == 2, "Student notifications restored once");
            check(loaded.getNotifications("T1").size() == 2, "Teacher notifications restored once");
            check(loaded.getActivityLog().size() == 2, "Activity log restored once");

            store.save(loaded, original);
            SchoolFacade reloaded = store.load(original);
            checkEquivalent(loaded, reloaded);
            check(reloaded.getNotifications("S1").size() == 2,
                    "Repeated save/load must not duplicate notifications");
            reloaded.addUser(Role.STUDENT, "S2", "Другой ученик", "second@school.test");
            store.save(reloaded, original);
            check(store.load(original).getUsers().size() == 3, "Existing snapshot is replaced");
            checkNoTemporaryFiles(directory);

            byte[] originalBytes = Files.readAllBytes(original);
            Path damaged = directory.resolve("damaged.properties");
            rejectMutation(store, original, damaged, p -> p.setProperty("version", "99"));
            rejectMutation(store, original, damaged, p -> p.remove("users.0.name"));
            rejectMutation(store, original, damaged, p -> p.setProperty("courses.0.teacherId", "missing"));
            rejectMutation(store, original, damaged, p -> p.setProperty("enrollments.0.studentId", "missing"));
            rejectMutation(store, original, damaged, p -> p.setProperty("grades.0.studentId", "S2"));
            rejectMutation(store, original, damaged, p -> p.setProperty("attendance.0.present", "maybe"));
            rejectMutation(store, original, damaged, p -> p.setProperty("users.count", "-1"));
            rejectMutation(store, original, damaged, p -> p.setProperty("users.count", "100001"));
            rejectMutation(store, original, damaged, p -> p.setProperty("grades.count", "0"));
            rejectMutation(store, original, damaged,
                    p -> p.setProperty("attendance.1.date", p.getProperty("attendance.0.date")));
            rejectMutation(store, original, damaged, p -> p.setProperty("grades.0.earned", "NaN"));
            rejectMutation(store, original, damaged, p -> p.remove("grades.0.comment"));

            Files.write(damaged, new byte[] {(byte) 0xc3, (byte) 0x28});
            expectIOException(() -> store.load(damaged));
            Files.write(damaged, new byte[10 * 1024 * 1024 + 1]);
            expectIOException(() -> store.load(damaged));
            Files.writeString(damaged, "", StandardCharsets.UTF_8);
            expectIOException(() -> store.load(damaged));
            check(Arrays.equals(originalBytes, Files.readAllBytes(original)),
                    "Failed loads must preserve the valid original file");

            SchoolFacade oversized = new SchoolFacade();
            try {
                oversized.addUser(Role.STUDENT, "BIG", "A".repeat(10 * 1024 * 1024 + 1), "big@school.test");
                throw new AssertionError("Oversized values must be rejected before mutation");
            } catch (IllegalArgumentException expected) {
                check(oversized.getUsers().isEmpty(), "Rejected values must not change live data");
            }
            check(Arrays.equals(originalBytes, Files.readAllBytes(original)),
                    "Rejected oversized input must preserve the destination file");
            checkNoTemporaryFiles(directory);

            Path nested = directory.resolve("new/subdirectory/snapshot.properties");
            store.save(new SchoolFacade(), nested);
            check(store.load(nested).getUsers().isEmpty(), "Empty snapshot and new directories work");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static SchoolFacade sampleSchool() {
        SchoolFacade school = new SchoolFacade();
        school.addUser(Role.TEACHER, "T1", "Айгүл Мұғалім", "teacher@school.test");
        school.addUser(Role.STUDENT, "S1", "Әли Ученик", "student@school.test");
        school.addCourse(new CourseBuilder().id("C1").title("Қазақ тілі = язык: #1")
                .teacherId("T1").schedule("Дүйсенбі 10:00").capacity(5)
                .gradingMode(GradingMode.WEIGHTED)
                .weights(Map.of("home.work", 40.0, "exam", 60.0)).build());
        school.enroll("S1", "C1");
        school.recordGrade(new Grade("S1", "C1", "home.work", 8, 10,
                "Жақсы!\nКомментарий = : # \\ путь"));
        school.recordGrade(new Grade("S1", "C1", "exam", 45, 50, ""));
        school.recordAttendance(new Attendance("S1", "C1", LocalDate.of(2026, 11, 8), true));
        school.recordAttendance(new Attendance("S1", "C1", LocalDate.of(2026, 11, 9), false));
        return school;
    }

    private static void checkEquivalent(SchoolFacade expected, SchoolFacade actual) {
        check(actual.getUsers().size() == expected.getUsers().size(), "User count roundtrip");
        for (var user : expected.getUsers()) {
            var loaded = actual.findUser(user.getId());
            check(loaded.getName().equals(user.getName()), "Unicode user name roundtrip");
            check(loaded.getEmail().equals(user.getEmail()), "User email roundtrip");
            check(loaded.getRole() == user.getRole(), "Concrete user role roundtrip");
        }
        check(actual.getCourses().size() == expected.getCourses().size(), "Course count roundtrip");
        for (var course : expected.getCourses()) {
            var loaded = actual.findCourse(course.getId());
            check(loaded.getTitle().equals(course.getTitle()), "Unicode course title roundtrip");
            check(loaded.getTeacherId().equals(course.getTeacherId()), "Teacher reference roundtrip");
            check(loaded.getSchedule().equals(course.getSchedule()), "Course schedule roundtrip");
            check(loaded.getCapacity() == course.getCapacity(), "Course capacity roundtrip");
            check(loaded.getGradingMode() == course.getGradingMode(), "Grading mode roundtrip");
            check(loaded.getWeights().equals(course.getWeights()), "Weights with dotted category roundtrip");
        }
        check(actual.getEnrollments().equals(expected.getEnrollments()), "Enrollments roundtrip");
        check(actual.getGrades().equals(expected.getGrades()), "Grades and escaped comments roundtrip");
        check(actual.getAttendance().equals(expected.getAttendance()), "Attendance roundtrip");
        check(Math.abs(actual.calculateFinalGrade("S1", "C1") - 86.0) < 0.000001,
                "Weighted calculation still works after loading");
        check(actual.generateReport("S1", true, true).equals(expected.generateReport("S1", true, true)),
                "Composed report is preserved by roundtrip");
    }

    private static void rejectMutation(FileSchoolStore store, Path valid, Path damaged,
                                       Consumer<Properties> mutation) throws Exception {
        Properties data = new Properties();
        try (Reader reader = Files.newBufferedReader(valid, StandardCharsets.UTF_8)) {
            data.load(reader);
        }
        mutation.accept(data);
        try (Writer writer = Files.newBufferedWriter(damaged, StandardCharsets.UTF_8)) {
            data.store(writer, "Damaged test fixture");
        }
        byte[] bytes = Files.readAllBytes(damaged);
        expectIOException(() -> store.load(damaged));
        check(Arrays.equals(bytes, Files.readAllBytes(damaged)), "A rejected file must remain unchanged");
    }

    private static void checkNoTemporaryFiles(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            check(files.noneMatch(path -> path.getFileName().toString().startsWith(".school-")),
                    "Saving must clean up temporary files");
        }
    }

    private static void expectIOException(CheckedAction action) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError("Expected IOException");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
