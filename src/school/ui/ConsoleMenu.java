package school.ui;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import school.application.Attendance;
import school.application.DemoData;
import school.application.SchoolFacade;
import school.courses.Course;
import school.courses.CourseBuilder;
import school.grades.Grade;
import school.grades.GradingMode;
import school.storage.FileSchoolStore;
import school.users.Role;

/** Console adapter only: business rules live in the Java domain and facade. */
public final class ConsoleMenu {
    private final Scanner input;
    private final PrintStream out;
    private final SchoolFacade school;
    private final FileSchoolStore store;
    private final Path dataFile;

    public ConsoleMenu(Scanner input, PrintStream out, SchoolFacade school, FileSchoolStore store, Path dataFile) {
        this.input = input;
        this.out = out;
        this.school = school;
        this.store = store;
        this.dataFile = dataFile;
    }

    public boolean run() {
        out.println("SCHOOL MANAGEMENT SYSTEM | Pure Java | Six design patterns");
        out.println(dataFile == null ? "Memory mode: data is not saved between runs."
                : "Data file: " + dataFile.toAbsolutePath());
        boolean running = true;
        boolean savedOnExit = true;
        while (running) {
            out.println("\n1 Users             2 Create user       3 Courses");
            out.println("4 Create course     5 Enroll student    6 Course roster");
            out.println("7 Add grade         8 View grades       9 Mark attendance");
            out.println("10 Student report   11 Notifications    12 Activity log");
            out.println("13 Load demo data   14 Save             0 Save and exit");
            try {
                int action = integer("Choose: ");
                boolean changed = false;
                switch (action) {
                    case 0 -> running = !save();
                    case 1 -> listUsers();
                    case 2 -> { createUser(); changed = true; }
                    case 3 -> listCourses();
                    case 4 -> { createCourse(); changed = true; }
                    case 5 -> { enroll(); changed = true; }
                    case 6 -> roster();
                    case 7 -> { addGrade(); changed = true; }
                    case 8 -> viewGrades();
                    case 9 -> { markAttendance(); changed = true; }
                    case 10 -> report();
                    case 11 -> notifications();
                    case 12 -> {
                        if (school.getActivityLog().isEmpty()) out.println("No grade events yet.");
                        else school.getActivityLog().forEach(out::println);
                    }
                    case 13 -> { DemoData.populate(school); out.println("Demo loaded: users A1/T1/S1/S2, courses C1/C2."); changed = true; }
                    case 14 -> save();
                    default -> out.println("Choose a number from the menu.");
                }
                if (changed && dataFile != null) save();
            } catch (NoSuchElementException end) {
                out.println("\nInput ended.");
                savedOnExit = save();
                if (!savedOnExit) recoverAfterEndOfInput();
                running = false;
            } catch (IllegalArgumentException | java.time.DateTimeException error) {
                out.println("Error: " + error.getMessage());
            }
        }
        out.println("Goodbye.");
        return savedOnExit;
    }

    private void listUsers() {
        if (school.getUsers().isEmpty()) { out.println("No users. Create users or load demo data."); return; }
        school.getUsers().forEach(user -> out.printf("%s | %s | %s | %s%n",
                user.getId(), user.getRole(), user.getName(), user.getEmail()));
    }

    private void createUser() {
        Role role = switch (integer("Role (1 Student, 2 Teacher, 3 Admin): ")) {
            case 1 -> Role.STUDENT;
            case 2 -> Role.TEACHER;
            case 3 -> Role.ADMIN;
            default -> throw new IllegalArgumentException("Role must be 1, 2 or 3.");
        };
        var user = school.addUser(role, line("ID: "), line("Name: "), line("Email: "));
        out.println("Created " + user.getRole() + " " + user.getId() + " using Factory Method.");
    }

    private void listCourses() {
        if (school.getCourses().isEmpty()) { out.println("No courses. Create a teacher, then a course."); return; }
        for (Course course : school.getCourses()) {
            long enrolled = school.getEnrollments().stream().filter(item -> item.courseId().equals(course.getId())).count();
            out.printf("%s | %s | teacher %s | %s | %s | %d/%d students%n",
                    course.getId(), course.getTitle(), course.getTeacherId(), course.getSchedule(),
                    course.getGradingMode(), enrolled, course.getCapacity());
            if (!course.getWeights().isEmpty()) out.println("  Category weights (%): " + course.getWeights());
        }
    }

    private void createCourse() {
        listUsers();
        CourseBuilder builder = new CourseBuilder().id(line("Course ID: ")).title(line("Title: "))
                .teacherId(line("Teacher ID: "));
        String schedule = line("Schedule (blank = Not scheduled): ");
        if (!schedule.isBlank()) builder.schedule(schedule);
        String capacity = line("Capacity (blank = 30): ");
        if (!capacity.isBlank()) builder.capacity(parseInteger(capacity));
        int mode = integer("Grading (1 Points, 2 Weighted categories): ");
        if (mode == 1) builder.gradingMode(GradingMode.POINTS);
        else if (mode == 2) {
            builder.gradingMode(GradingMode.WEIGHTED);
            out.println("Weights must add up to 100. Example: homework=40,exam=60");
            builder.weights(parseWeights(line("Weights: ")));
        } else throw new IllegalArgumentException("Grading mode must be 1 or 2.");
        Course course = builder.build();
        school.addCourse(course);
        out.println("Created course " + course.getId() + " using Builder.");
    }

    private void enroll() {
        listUsers(); listCourses();
        String student = line("Student ID: ");
        String course = line("Course ID: ");
        school.enroll(student, course);
        out.println("Enrollment completed.");
    }

    private void roster() {
        Course course = school.findCourse(line("Course ID: "));
        var entries = school.getEnrollments().stream().filter(item -> item.courseId().equals(course.getId())).toList();
        if (entries.isEmpty()) out.println("No students enrolled.");
        for (var entry : entries) {
            out.println(entry.studentId() + " | " + school.findUser(entry.studentId()).getName());
        }
    }

    private void addGrade() {
        String student = line("Student ID: ");
        Course course = school.findCourse(line("Course ID: "));
        if (!course.getWeights().isEmpty()) out.println("Allowed categories: " + course.getWeights().keySet());
        String category = line("Category: ");
        double earned = decimal("Earned points: ");
        double possible = decimal("Maximum points: ");
        String comment = line("Teacher comment (optional): ");
        school.recordGrade(new Grade(student, course.getId(), category, earned, possible, comment));
        out.printf(Locale.ROOT, "Grade saved. Current result: %.2f%%. Student and teacher notified.%n",
                school.calculateFinalGrade(student, course.getId()));
    }

    private void viewGrades() {
        String student = line("Student ID: ");
        Course course = school.findCourse(line("Course ID: "));
        var grades = school.getStudentGrades(student, course.getId());
        if (grades.isEmpty()) { out.println("No grades yet."); return; }
        for (Grade grade : grades) out.printf(Locale.ROOT, "%s | %.2f/%.2f | %s%n",
                grade.category(), grade.earned(), grade.possible(), grade.comment());
        out.printf(Locale.ROOT, "Final result (%s strategy): %.2f%%%n", course.getGradingMode(),
                school.calculateFinalGrade(student, course.getId()));
        if (course.getGradingMode() == GradingMode.WEIGHTED) out.println("Categories without grades contribute 0 until graded.");
    }

    private void markAttendance() {
        String student = line("Student ID: ");
        String course = line("Course ID: ");
        LocalDate date = LocalDate.parse(line("Date (YYYY-MM-DD): "));
        school.recordAttendance(new Attendance(student, course, date, yesNo("Present? (y/n): ")));
        out.println("Attendance saved (replaces the entry for the same student, course and date).");
    }

    private void report() {
        String student = line("Student ID: ");
        boolean attendance = yesNo("Add attendance section? (y/n): ");
        boolean comments = yesNo("Add teacher comments? (y/n): ");
        out.println(school.generateReport(student, attendance, comments));
    }

    private void notifications() {
        var items = school.getNotifications(line("Recipient user ID: "));
        if (items.isEmpty()) out.println("No notifications for this user.");
        for (var notification : items) out.println("- " + notification.message());
    }

    private boolean save() {
        if (dataFile == null) return true;
        try {
            store.save(school, dataFile);
            out.println("Saved.");
            return true;
        } catch (IOException error) {
            out.println("SAVE FAILED: " + error.getMessage());
            out.println("Changes are still in memory. Check the path/permissions and use option 14 to retry.");
            return false;
        }
    }

    private void recoverAfterEndOfInput() {
        out.println("Input is closed; the requested data file could not be saved.");
        try {
            Path recovery = Files.createTempDirectory("school-recovery-").resolve("school.properties");
            store.save(school, recovery);
            out.println("Recovery snapshot saved to: " + recovery);
            out.println("Copy this file to a safe location before cleaning temporary files.");
        } catch (IOException error) {
            out.println("Recovery also failed. Unsaved changes will be lost: " + error.getMessage());
        }
    }

    private String line(String prompt) { out.print(prompt); out.flush(); return input.nextLine().strip(); }
    private int integer(String prompt) { return parseInteger(line(prompt)); }
    private static int parseInteger(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Enter a whole number."); }
    }
    private double decimal(String prompt) {
        try { return Double.parseDouble(line(prompt)); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Enter a number using a decimal point, e.g. 85.5."); }
    }
    private boolean yesNo(String prompt) {
        while (true) {
            String value = line(prompt).toLowerCase(Locale.ROOT);
            if (value.equals("y") || value.equals("yes") || value.equals("да")) return true;
            if (value.equals("n") || value.equals("no") || value.equals("нет")) return false;
            out.println("Enter y or n.");
        }
    }
    private static Map<String, Double> parseWeights(String value) {
        Map<String, Double> weights = new LinkedHashMap<>();
        for (String item : value.split(",", -1)) {
            String[] pair = item.split("=", -1);
            if (pair.length != 2 || pair[0].isBlank()) throw new IllegalArgumentException("Use category=weight pairs.");
            String category = pair[0].strip().toLowerCase(Locale.ROOT);
            double weight;
            try { weight = Double.parseDouble(pair[1].strip()); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("Category weights must be numbers."); }
            if (weights.putIfAbsent(category, weight) != null) throw new IllegalArgumentException("Duplicate category: " + category);
        }
        return weights;
    }
}
