package school.storage;

import school.application.Attendance;
import school.application.Enrollment;
import school.application.SchoolFacade;
import school.courses.Course;
import school.courses.CourseBuilder;
import school.grades.Grade;
import school.grades.GradingMode;
import school.users.Role;
import school.users.User;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/** Stores a complete school snapshot using only the Java standard library. */
public final class FileSchoolStore {
    private static final String VERSION = "1";
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_RECORDS = 100_000;
    private static final int MAX_WEIGHTS = 1_000;

    /** Serializes completely before replacing the destination file. */
    public void save(SchoolFacade school, Path path) throws IOException {
        Objects.requireNonNull(school, "school");
        Objects.requireNonNull(path, "path");
        Properties data = new Properties();
        data.setProperty("version", VERSION);
        try {
            List<User> users = school.getUsers();
            List<Course> courses = school.getCourses();
            List<Enrollment> enrollments = school.getEnrollments();
            List<Grade> grades = school.getGrades();
            List<Attendance> attendance = school.getAttendance();
            checkTotal(users.size(), courses.size(), enrollments.size(), grades.size(), attendance.size());
            put(data, "users.count", users.size());
            for (int i = 0; i < users.size(); i++) {
                User user = users.get(i);
                String key = "users." + i + ".";
                put(data, key + "role", user.getRole());
                put(data, key + "id", user.getId());
                put(data, key + "name", user.getName());
                put(data, key + "email", user.getEmail());
            }
            put(data, "courses.count", courses.size());
            for (int i = 0; i < courses.size(); i++) {
                Course course = courses.get(i);
                String key = "courses." + i + ".";
                put(data, key + "id", course.getId());
                put(data, key + "title", course.getTitle());
                put(data, key + "teacherId", course.getTeacherId());
                put(data, key + "schedule", course.getSchedule());
                put(data, key + "mode", course.getGradingMode());
                put(data, key + "capacity", course.getCapacity());
                Map<String, Double> weights = course.getWeights();
                if (weights.size() > MAX_WEIGHTS) {
                    throw new IOException("Too many grading categories");
                }
                put(data, key + "weights.count", weights.size());
                int index = 0;
                for (Map.Entry<String, Double> entry : weights.entrySet()) {
                    put(data, key + "weights." + index + ".category", entry.getKey());
                    put(data, key + "weights." + index + ".value", entry.getValue());
                    index++;
                }
            }
            put(data, "enrollments.count", enrollments.size());
            for (int i = 0; i < enrollments.size(); i++) {
                Enrollment enrollment = enrollments.get(i);
                String key = "enrollments." + i + ".";
                put(data, key + "studentId", enrollment.studentId());
                put(data, key + "courseId", enrollment.courseId());
            }
            put(data, "grades.count", grades.size());
            for (int i = 0; i < grades.size(); i++) {
                Grade grade = grades.get(i);
                String key = "grades." + i + ".";
                put(data, key + "studentId", grade.studentId());
                put(data, key + "courseId", grade.courseId());
                put(data, key + "category", grade.category());
                put(data, key + "earned", grade.earned());
                put(data, key + "possible", grade.possible());
                put(data, key + "comment", grade.comment());
            }
            put(data, "attendance.count", attendance.size());
            for (int i = 0; i < attendance.size(); i++) {
                Attendance entry = attendance.get(i);
                String key = "attendance." + i + ".";
                put(data, key + "studentId", entry.studentId());
                put(data, key + "courseId", entry.courseId());
                put(data, key + "date", entry.date());
                put(data, key + "present", entry.present());
            }
        } catch (RuntimeException exception) {
            throw new IOException("Could not serialize school data", exception);
        }

        StringWriter buffer = new StringWriter();
        data.store(buffer, "School Management System - UTF-8 snapshot");
        String content = buffer.toString();
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IOException("School data exceeds the 10 MiB file limit");
        }
        Path destination = path.toAbsolutePath().normalize();
        Path directory = destination.getParent();
        if (directory == null || destination.getFileName() == null) {
            throw new IOException("A file path is required");
        }
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".school-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(content);
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Loads into a new facade, so an invalid file never partially changes live data. */
    public SchoolFacade load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        } catch (NoSuchFileException exception) {
            return new SchoolFacade();
        }
        if (bytes.length > MAX_BYTES) {
            throw new IOException("School data exceeds the 10 MiB file limit");
        }
        String content = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        Properties data = new Properties();
        try {
            data.load(new StringReader(content));
            if (!VERSION.equals(required(data, "version"))) {
                throw new IOException("Unsupported school data version");
            }
            int userCount = count(data, "users.count", MAX_RECORDS);
            int courseCount = count(data, "courses.count", MAX_RECORDS);
            int enrollmentCount = count(data, "enrollments.count", MAX_RECORDS);
            int gradeCount = count(data, "grades.count", MAX_RECORDS);
            int attendanceCount = count(data, "attendance.count", MAX_RECORDS);
            checkTotal(userCount, courseCount, enrollmentCount, gradeCount, attendanceCount);
            long expectedFields = 6 + 4L * userCount + 7L * courseCount
                    + 2L * enrollmentCount + 6L * gradeCount + 4L * attendanceCount;
            SchoolFacade school = new SchoolFacade();
            for (int i = 0; i < userCount; i++) {
                String key = "users." + i + ".";
                school.addUser(Role.valueOf(required(data, key + "role")),
                        required(data, key + "id"), required(data, key + "name"),
                        required(data, key + "email"));
            }
            for (int i = 0; i < courseCount; i++) {
                String key = "courses." + i + ".";
                int weightCount = count(data, key + "weights.count", MAX_WEIGHTS);
                expectedFields += 2L * weightCount;
                Map<String, Double> weights = new LinkedHashMap<>();
                for (int j = 0; j < weightCount; j++) {
                    String weightKey = key + "weights." + j + ".";
                    String category = required(data, weightKey + "category");
                    Double previous = weights.put(category, number(data, weightKey + "value"));
                    if (previous != null) {
                        throw new IOException("Duplicate grading category: " + category);
                    }
                }
                school.addCourse(new CourseBuilder().id(required(data, key + "id"))
                        .title(required(data, key + "title"))
                        .teacherId(required(data, key + "teacherId"))
                        .schedule(required(data, key + "schedule"))
                        .gradingMode(GradingMode.valueOf(required(data, key + "mode")))
                        .capacity(positiveInt(data, key + "capacity"))
                        .weights(weights).build());
            }
            for (int i = 0; i < enrollmentCount; i++) {
                String key = "enrollments." + i + ".";
                school.enroll(required(data, key + "studentId"), required(data, key + "courseId"));
            }
            for (int i = 0; i < gradeCount; i++) {
                String key = "grades." + i + ".";
                school.recordGrade(new Grade(required(data, key + "studentId"),
                        required(data, key + "courseId"), required(data, key + "category"),
                        number(data, key + "earned"), number(data, key + "possible"),
                        required(data, key + "comment")));
            }
            Set<AttendanceKey> attendanceKeys = new HashSet<>();
            for (int i = 0; i < attendanceCount; i++) {
                String key = "attendance." + i + ".";
                String present = required(data, key + "present");
                if (!present.equals("true") && !present.equals("false")) {
                    throw new IOException("Invalid attendance boolean: " + key + "present");
                }
                Attendance entry = new Attendance(required(data, key + "studentId"),
                        required(data, key + "courseId"),
                        LocalDate.parse(required(data, key + "date")), Boolean.parseBoolean(present));
                if (!attendanceKeys.add(new AttendanceKey(entry.studentId(), entry.courseId(), entry.date()))) {
                    throw new IOException("Duplicate attendance entry: " + key);
                }
                school.recordAttendance(entry);
            }
            if (data.size() != expectedFields) {
                throw new IOException("Unexpected school data fields or inconsistent record counts");
            }
            return school;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid school data: " + exception.getMessage(), exception);
        }
    }

    private static void put(Properties data, String key, Object value) {
        data.setProperty(key, Objects.requireNonNull(value, key).toString());
    }

    private static String required(Properties data, String key) throws IOException {
        String value = data.getProperty(key);
        if (value == null) {
            throw new IOException("Missing school data field: " + key);
        }
        return value;
    }

    private static int count(Properties data, String key, int maximum) throws IOException {
        int count = Integer.parseInt(required(data, key));
        if (count < 0 || count > maximum) {
            throw new IOException("Invalid record count: " + key);
        }
        return count;
    }

    private static int positiveInt(Properties data, String key) throws IOException {
        int value = Integer.parseInt(required(data, key));
        if (value <= 0) {
            throw new IOException("Expected a positive integer: " + key);
        }
        return value;
    }

    private static double number(Properties data, String key) throws IOException {
        double value = Double.parseDouble(required(data, key));
        if (!Double.isFinite(value)) {
            throw new IOException("Expected a finite number: " + key);
        }
        return value;
    }

    private static void checkTotal(int... counts) throws IOException {
        long total = 0;
        for (int count : counts) {
            if (count < 0) {
                throw new IOException("Invalid record count");
            }
            total += count;
        }
        if (total > MAX_RECORDS) {
            throw new IOException("School data exceeds the 100000 record limit");
        }
    }

    private record AttendanceKey(String studentId, String courseId, LocalDate date) { }
}
