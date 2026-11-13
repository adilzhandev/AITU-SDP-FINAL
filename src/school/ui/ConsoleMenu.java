package school.ui;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.function.Function;
import school.application.Attendance;
import school.application.DemoData;
import school.application.SchoolFacade;
import school.courses.Course;
import school.courses.CourseBuilder;
import school.grades.Grade;
import school.grades.GradingMode;
import school.grades.GradingRules;
import school.reports.ExportFormat;
import school.reports.ReportExporter;
import school.storage.FileSchoolStore;
import school.users.Role;
import school.users.User;
import school.validation.TextRules;

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
        out.println("Enter /cancel at any prompt to cancel the current action.");
        boolean running = true;
        boolean savedOnExit = true;
        while (running) {
            out.println("\n1 Users             2 Create user       3 Courses");
            out.println("4 Create course     5 Enroll student    6 Course roster");
            out.println("7 Add grade         8 View grades       9 Mark attendance");
            out.println("10 Student report   11 Notifications    12 Activity log");
            out.println("13 Load demo data   14 Save             " + (dataFile == null ? "0 Exit" : "0 Save and exit"));
            out.println("15 Edit grade       16 Export student report");
            try {
                int action = read("Choose: ", ConsoleMenu::parseInteger);
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
                    case 14 -> {
                        if (dataFile == null) out.println("Saving is unavailable in memory mode. Use option 16 to export a report, or restart with a data file to keep school data.");
                        else save();
                    }
                    case 15 -> changed = editGrade();
                    case 16 -> exportReport();
                    default -> out.println("Choose a number from the menu.");
                }
                if (changed && dataFile != null) save();
            } catch (CancelledAction cancelled) {
                out.println("Action cancelled. No changes were made.");
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
        Role role = read("Role (1 Student, 2 Teacher, 3 Admin): ", value -> switch (parseInteger(value)) {
            case 1 -> Role.STUDENT;
            case 2 -> Role.TEACHER;
            case 3 -> Role.ADMIN;
            default -> throw new IllegalArgumentException("Role must be 1, 2 or 3.");
        });
        String id = read("ID: ", value -> {
            String candidate = TextRules.required(value, "ID", TextRules.ID_MAX);
            if (school.getUsers().stream().anyMatch(user -> user.getId().equals(candidate))) {
                throw new IllegalArgumentException("User ID already exists.");
            }
            return candidate;
        });
        String name = required("Name: ", "Name", TextRules.NAME_MAX);
        String email = read("Email: ", value -> {
            String candidate = TextRules.required(value, "Email", TextRules.EMAIL_MAX);
            candidate = User.validatedEmail(candidate);
            String validated = candidate;
            if (school.getUsers().stream().anyMatch(user -> user.getEmail().equalsIgnoreCase(validated))) {
                throw new IllegalArgumentException("Email already belongs to another user.");
            }
            return candidate;
        });
        var user = school.addUser(role, id, name, email);
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
        String id = read("Course ID: ", value -> {
            String candidate = TextRules.required(value, "Course ID", TextRules.ID_MAX);
            if (school.getCourses().stream().anyMatch(course -> course.getId().equals(candidate))) {
                throw new IllegalArgumentException("Course ID already exists.");
            }
            return candidate;
        });
        String title = required("Title: ", "Title", TextRules.TITLE_MAX);
        String teacher = userId("Teacher ID: ", Role.TEACHER);
        String schedule = read("Schedule (blank = Not scheduled): ", value ->
                value.isEmpty() ? "Not scheduled" : TextRules.required(value, "Schedule", TextRules.SCHEDULE_MAX));
        int capacity = read("Capacity (blank = 30): ", value -> {
            int number = value.isEmpty() ? 30 : parseInteger(value);
            if (number <= 0) throw new IllegalArgumentException("Capacity must be positive.");
            return number;
        });
        GradingMode mode = read("Grading (1 Points, 2 Weighted categories): ", value -> switch (parseInteger(value)) {
            case 1 -> GradingMode.POINTS;
            case 2 -> GradingMode.WEIGHTED;
            default -> throw new IllegalArgumentException("Grading mode must be 1 or 2.");
        });
        CourseBuilder builder = new CourseBuilder().id(id).title(title).teacherId(teacher)
                .schedule(schedule).capacity(capacity).gradingMode(mode);
        if (mode == GradingMode.WEIGHTED) {
            out.println("Weights must add up to 100. Example: homework=40,exam=60");
            builder.weights(read("Weights: ", ConsoleMenu::parseWeights));
        }
        Course course = builder.build();
        school.addCourse(course);
        out.println("Created course " + course.getId() + " using Builder.");
    }

    private void enroll() {
        listUsers(); listCourses();
        String student = userId("Student ID: ", Role.STUDENT);
        Course course = read("Course ID: ", value -> {
            Course candidate = school.findCourse(value);
            if (school.getEnrollments().stream().anyMatch(entry -> entry.studentId().equals(student)
                    && entry.courseId().equals(candidate.getId()))) {
                throw new IllegalArgumentException("Student is already enrolled.");
            }
            long enrolled = school.getEnrollments().stream().filter(entry -> entry.courseId().equals(candidate.getId())).count();
            if (enrolled >= candidate.getCapacity()) throw new IllegalArgumentException("Course is full.");
            return candidate;
        });
        school.enroll(student, course.getId());
        out.println("Enrollment completed.");
    }

    private void roster() {
        Course course = read("Course ID: ", school::findCourse);
        var entries = school.getEnrollments().stream().filter(item -> item.courseId().equals(course.getId())).toList();
        if (entries.isEmpty()) out.println("No students enrolled.");
        for (var entry : entries) out.println(entry.studentId() + " | " + school.findUser(entry.studentId()).getName());
    }

    private void addGrade() {
        String student = userId("Student ID: ", Role.STUDENT);
        Course course = enrolledCourse(student);
        showCategories(course);
        String category = read("Category: ", value -> category(value, course));
        double earned = read("Earned points: ", value -> earned(value, Double.MAX_VALUE));
        double possible = read("Maximum points: ", ConsoleMenu::possible);
        if (earned > possible) {
            out.println("Error: Earned points must not exceed maximum points.");
            earned = read("Earned points: ", value -> earned(value, possible));
        }
        String comment = read("Teacher comment (optional): ", value -> TextRules.optional(value, "Comment", TextRules.COMMENT_MAX));
        Grade stored = school.recordGrade(new Grade(student, course.getId(), category, earned, possible, comment));
        out.printf(Locale.ROOT, "Grade #%d saved. Current result: %.2f%%. Student and teacher notified.%n",
                stored.id(), school.calculateFinalGrade(student, course.getId()));
    }

    private void viewGrades() {
        String student = userId("Student ID: ", Role.STUDENT);
        Course course = enrolledCourse(student);
        var grades = school.getStudentGrades(student, course.getId());
        if (grades.isEmpty()) { out.println("No grades yet."); return; }
        grades.forEach(this::printGrade);
        out.printf(Locale.ROOT, "Final result (%s strategy): %.2f%%%n", course.getGradingMode(),
                school.calculateFinalGrade(student, course.getId()));
        if (course.getGradingMode() == GradingMode.WEIGHTED) out.println("Categories without grades contribute 0 until graded.");
    }

    private boolean editGrade() {
        String student = userId("Student ID: ", Role.STUDENT);
        Course course = enrolledCourse(student);
        var grades = school.getStudentGrades(student, course.getId());
        if (grades.isEmpty()) { out.println("No grades to edit for this student and course."); return false; }
        grades.forEach(this::printGrade);
        Grade old = read("Grade ID: ", value -> {
            long id;
            try { id = Long.parseLong(value); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("Enter a whole grade ID."); }
            return grades.stream().filter(grade -> grade.id() == id).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Choose a grade ID from the selected student and course."));
        });
        out.println("Editing grade #" + old.id() + ". Leave a value blank to keep it; /clear removes the comment.");
        showCategories(course);
        String category = read("Category [" + old.category() + "]: ", value -> value.isEmpty() ? old.category() : category(value, course));
        double earned = read("Earned points [" + old.earned() + "]: ", value -> value.isEmpty() ? old.earned() : earned(value, Double.MAX_VALUE));
        double possible = read("Maximum points [" + old.possible() + "]: ", value -> value.isEmpty() ? old.possible() : possible(value));
        if (earned > possible) {
            out.println("Error: Earned points must not exceed maximum points.");
            earned = read("Earned points (0 to " + possible + "): ", value ->
                    earned(value.isEmpty() ? Double.toString(old.earned()) : value, possible));
        }
        String comment = read("Teacher comment [" + old.comment() + "]: ", value ->
                value.isEmpty() ? old.comment() : value.equals("/clear") ? "" : TextRules.optional(value, "Comment", TextRules.COMMENT_MAX));
        if (old.category().equals(category) && old.earned() == earned && old.possible() == possible && old.comment().equals(comment)) {
            out.println("No changes to grade #" + old.id() + ".");
            return false;
        }
        school.updateGrade(old.id(), category, earned, possible, comment);
        out.printf(Locale.ROOT, "Grade #%d updated. Current result: %.2f%%. Student and teacher notified.%n",
                old.id(), school.calculateFinalGrade(student, course.getId()));
        return true;
    }

    private void printGrade(Grade grade) {
        out.printf(Locale.ROOT, "#%d | %s | %.2f/%.2f | %s%n", grade.id(), grade.category(), grade.earned(), grade.possible(), grade.comment());
    }

    private void markAttendance() {
        String student = userId("Student ID: ", Role.STUDENT);
        Course course = enrolledCourse(student);
        LocalDate date = read("Date (YYYY-MM-DD): ", value -> {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException("Enter a valid date in YYYY-MM-DD format.");
            try { return LocalDate.parse(value); }
            catch (java.time.DateTimeException error) { throw new IllegalArgumentException("Enter a valid date in YYYY-MM-DD format."); }
        });
        school.recordAttendance(new Attendance(student, course.getId(), date, yesNo("Present? (y/n): ")));
        out.println("Attendance saved (replaces the entry for the same student, course and date).");
    }

    private void report() {
        String student = userId("Student ID: ", Role.STUDENT);
        boolean attendance = yesNo("Add attendance section? (y/n): ");
        boolean comments = yesNo("Add teacher comments? (y/n): ");
        out.println(school.generateReport(student, attendance, comments));
    }

    private void exportReport() {
        String student = userId("Student ID: ", Role.STUDENT);
        boolean attendance = yesNo("Add attendance section? (y/n): ");
        boolean comments = yesNo("Add teacher comments? (y/n): ");
        ExportFormat format = read("Export format (1 TXT, 2 CSV): ", value -> switch (parseInteger(value)) {
            case 1 -> ExportFormat.TXT;
            case 2 -> ExportFormat.CSV;
            default -> throw new IllegalArgumentException("Export format must be 1 or 2.");
        });
        out.println("Enter a new file path. Existing files are never overwritten.");
        while (true) {
            Path path = read("Report file path: ", value -> {
                if (value.isBlank()) throw new IllegalArgumentException("A report file path is required.");
                Path candidate = Path.of(value).toAbsolutePath().normalize();
                if (dataFile != null) {
                    try {
                        if (resolveParentAliases(candidate).equals(resolveParentAliases(dataFile))) {
                            throw new IllegalArgumentException("Choose a report path different from the school data file.");
                        }
                    } catch (IOException error) {
                        throw new IllegalArgumentException("Cannot check the report path: " + error.getMessage());
                    }
                }
                return candidate;
            });
            try {
                Path written = new ReportExporter().export(school, student, attendance, comments, format, path);
                out.println("Report exported to: " + written);
                return;
            } catch (IOException error) {
                out.println("EXPORT FAILED: " + error.getMessage());
                out.println("Choose another file path, or enter /cancel.");
            }
        }
    }

    /** Resolves existing parent aliases even when the target file has not been created yet. */
    private static Path resolveParentAliases(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path ancestor = absolute.getParent();
        if (ancestor == null) return absolute.toRealPath();
        while (!Files.exists(ancestor)) {
            Path parent = ancestor.getParent();
            if (parent == null) return absolute;
            ancestor = parent;
        }
        return ancestor.toRealPath().resolve(ancestor.relativize(absolute)).normalize();
    }

    private void notifications() {
        String recipient = read("Recipient user ID: ", value -> school.findUser(value).getId());
        var items = school.getNotifications(recipient);
        if (items.isEmpty()) out.println("No notifications for this user.");
        for (var notification : items) out.println("- " + notification.message());
    }

    private String userId(String prompt, Role role) {
        return read(prompt, value -> {
            var user = school.findUser(value);
            if (user.getRole() != role) throw new IllegalArgumentException("User must have role " + role + ".");
            return user.getId();
        });
    }

    private Course enrolledCourse(String student) {
        return read("Course ID: ", value -> {
            Course course = school.findCourse(value);
            school.getStudentGrades(student, course.getId());
            return course;
        });
    }

    private void showCategories(Course course) {
        if (!course.getWeights().isEmpty()) out.println("Allowed categories: " + course.getWeights().keySet());
    }

    private static String category(String value, Course course) {
        String category = TextRules.required(value, "Category", TextRules.CATEGORY_MAX).toLowerCase(Locale.ROOT);
        category = TextRules.required(category, "Category", TextRules.CATEGORY_MAX);
        if (course.getGradingMode() == GradingMode.WEIGHTED && !course.getWeights().containsKey(category)) {
            throw new IllegalArgumentException("Category must be one of: " + course.getWeights().keySet());
        }
        return category;
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

    private String line(String prompt) {
        out.print(prompt); out.flush();
        String value = input.nextLine().strip();
        if (value.equals("/cancel")) throw new CancelledAction();
        return value;
    }

    /** Retries only the invalid field; EOF and cancellation propagate before any mutation. */
    private <T> T read(String prompt, Function<String, T> parser) {
        while (true) {
            try { return parser.apply(line(prompt)); }
            catch (IllegalArgumentException | java.time.DateTimeException error) { out.println("Error: " + error.getMessage()); }
        }
    }

    private String required(String prompt, String label, int max) {
        return read(prompt, value -> TextRules.required(value, label, max));
    }

    private static int parseInteger(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Enter a whole number."); }
    }

    private static double number(String value) {
        double number;
        try { number = Double.parseDouble(value); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Enter a number using a decimal point, e.g. 85.5."); }
        if (!Double.isFinite(number)) throw new IllegalArgumentException("Points must be finite numbers.");
        return number;
    }

    private static double earned(String value, double maximum) {
        double number = number(value);
        if (number < 0 || number > maximum) throw new IllegalArgumentException("Earned points must be between 0 and " + maximum + ".");
        return number;
    }

    private static double possible(String value) {
        double number = number(value);
        if (number <= 0) throw new IllegalArgumentException("Maximum points must be positive.");
        return number;
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
        String[] items = value.split(",", -1);
        if (items.length > TextRules.MAX_WEIGHTS) throw new IllegalArgumentException("Too many grading categories.");
        Map<String, Double> weights = new LinkedHashMap<>();
        for (String item : items) {
            String[] pair = item.split("=", -1);
            if (pair.length != 2 || pair[0].isBlank()) throw new IllegalArgumentException("Use category=weight pairs.");
            String category = TextRules.required(pair[0], "Category", TextRules.CATEGORY_MAX).toLowerCase(Locale.ROOT);
            double weight;
            try { weight = Double.parseDouble(pair[1].strip()); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("Category weights must be numbers."); }
            if (weights.putIfAbsent(category, weight) != null) throw new IllegalArgumentException("Duplicate category: " + category);
        }
        return GradingRules.validatedWeights(weights);
    }

    private static final class CancelledAction extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
