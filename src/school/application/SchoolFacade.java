package school.application;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import school.courses.Course;
import school.grades.Grade;
import school.grades.GradingMode;
import school.grades.GradingStrategies;
import school.notifications.ActivityLogObserver;
import school.notifications.EventPublisher;
import school.notifications.Notification;
import school.notifications.NotificationCenter;
import school.notifications.SchoolEvent;
import school.reports.AttendanceReportDecorator;
import school.reports.BasicStudentReport;
import school.reports.CommentsReportDecorator;
import school.reports.CourseResult;
import school.reports.StudentReport;
import school.users.Role;
import school.users.User;
import school.users.UserFactory;
import school.validation.TextRules;

/** Facade: coordinates user creation, enrollment, grading, events and report composition. */
public final class SchoolFacade {
    private final Map<String, User> users = new LinkedHashMap<>();
    private final Set<String> emails = new LinkedHashSet<>();
    private final Map<String, Course> courses = new LinkedHashMap<>();
    private final Set<Enrollment> enrollments = new LinkedHashSet<>();
    private final Map<String, Integer> enrollmentCounts = new LinkedHashMap<>();
    private final Map<Long, Grade> grades = new LinkedHashMap<>();
    private final Map<Enrollment, LinkedHashMap<Long, Grade>> gradesByEnrollment = new LinkedHashMap<>();
    private final Map<AttendanceKey, Attendance> attendance = new LinkedHashMap<>();
    private final SnapshotBudget snapshotBudget = new SnapshotBudget();
    private long nextGradeId = 1;
    private final EventPublisher events = new EventPublisher();
    private final NotificationCenter notifications = new NotificationCenter();
    private final ActivityLogObserver activityLog = new ActivityLogObserver();

    public SchoolFacade() {
        events.subscribe(notifications);
        events.subscribe(activityLog);
    }

    public User addUser(Role role, String id, String name, String email) {
        User user = UserFactory.forRole(role).create(id, name, email);
        if (users.containsKey(user.getId())) throw new IllegalArgumentException("User ID already exists.");
        String emailKey = emailKey(user.getEmail());
        if (emails.contains(emailKey)) {
            throw new IllegalArgumentException("Email already belongs to another user.");
        }
        snapshotBudget.add(SnapshotBudget.cost(user));
        users.put(user.getId(), user);
        emails.add(emailKey);
        return user;
    }

    public void addCourse(Course course) {
        Objects.requireNonNull(course, "Course is required.");
        requireRole(course.getTeacherId(), Role.TEACHER);
        if (courses.containsKey(course.getId())) throw new IllegalArgumentException("Course ID already exists.");
        snapshotBudget.add(SnapshotBudget.cost(course));
        courses.put(course.getId(), course);
    }

    public void enroll(String studentId, String courseId) {
        Enrollment enrollment = new Enrollment(studentId, courseId);
        requireRole(enrollment.studentId(), Role.STUDENT);
        Course course = findCourse(enrollment.courseId());
        if (enrollments.contains(enrollment)) throw new IllegalArgumentException("Student is already enrolled.");
        int size = enrollmentCounts.getOrDefault(course.getId(), 0);
        if (size >= course.getCapacity()) throw new IllegalArgumentException("Course is full.");
        snapshotBudget.add(SnapshotBudget.cost(enrollment));
        enrollments.add(enrollment);
        enrollmentCounts.put(course.getId(), size + 1);
        gradesByEnrollment.put(enrollment, new LinkedHashMap<>());
    }

    public Grade recordGrade(Grade grade) {
        Objects.requireNonNull(grade, "Grade is required.");
        Enrollment enrollment = requireEnrollment(grade.studentId(), grade.courseId());
        Course course = findCourse(grade.courseId());
        validateCategory(course, grade.category());
        if (grade.id() == 0) {
            if (nextGradeId == 0) throw new IllegalArgumentException("No automatic grade IDs remain.");
            grade = new Grade(nextGradeId, grade.studentId(), grade.courseId(), grade.category(),
                    grade.earned(), grade.possible(), grade.comment());
        }
        if (grades.containsKey(grade.id())) throw new IllegalArgumentException("Grade ID already exists.");
        String message = String.format(Locale.ROOT, "%s: %s received %.2f/%.2f in %s",
                course.getTitle(), findUser(grade.studentId()).getName(), grade.earned(), grade.possible(), grade.category());
        SchoolEvent event = new SchoolEvent(grade.studentId(), course.getTeacherId(), course.getId(), message);
        snapshotBudget.add(SnapshotBudget.cost(grade));
        grades.put(grade.id(), grade);
        gradesByEnrollment.get(enrollment).put(grade.id(), grade);
        if (nextGradeId != 0 && grade.id() >= nextGradeId) {
            nextGradeId = grade.id() == Long.MAX_VALUE ? 0 : grade.id() + 1;
        }
        events.publish(event);
        return grade;
    }

    public Grade findGrade(long id) {
        Grade grade = grades.get(id);
        if (grade == null) throw new IllegalArgumentException("Grade not found: " + id);
        return grade;
    }

    /** Corrects one assessment without changing its identity, student or course. */
    public Grade updateGrade(long id, String category, double earned, double possible, String comment) {
        Grade previous = findGrade(id);
        Grade updated = new Grade(id, previous.studentId(), previous.courseId(), category, earned, possible, comment);
        Course course = findCourse(previous.courseId());
        validateCategory(course, updated.category());
        String message = String.format(Locale.ROOT, "%s: grade #%d for %s corrected to %.2f/%.2f in %s",
                course.getTitle(), id, findUser(updated.studentId()).getName(),
                updated.earned(), updated.possible(), updated.category());
        SchoolEvent event = new SchoolEvent(updated.studentId(), course.getTeacherId(), course.getId(), message);
        snapshotBudget.replace(SnapshotBudget.cost(previous), SnapshotBudget.cost(updated));
        grades.put(id, updated);
        gradesByEnrollment.get(new Enrollment(updated.studentId(), updated.courseId())).put(id, updated);
        events.publish(event);
        return updated;
    }

    public void recordAttendance(Attendance entry) {
        Objects.requireNonNull(entry, "Attendance is required.");
        requireEnrollment(entry.studentId(), entry.courseId());
        AttendanceKey key = new AttendanceKey(entry.studentId(), entry.courseId(), entry.date());
        Attendance previous = attendance.get(key);
        if (previous == null) snapshotBudget.add(SnapshotBudget.cost(entry));
        else snapshotBudget.replace(SnapshotBudget.cost(previous), SnapshotBudget.cost(entry));
        attendance.remove(key);
        attendance.put(key, entry);
    }

    public double calculateFinalGrade(String studentId, String courseId) {
        requireEnrollment(studentId, courseId);
        Course course = findCourse(courseId);
        return GradingStrategies.forMode(course.getGradingMode())
                .calculate(getStudentGrades(studentId, courseId), course.getWeights());
    }

    public String generateReport(String studentId, boolean includeAttendance, boolean includeComments) {
        User student = requireRole(studentId, Role.STUDENT);
        List<CourseResult> results = enrollments.stream().filter(item -> item.studentId().equals(student.getId()))
                .map(item -> new CourseResult(item.courseId(), findCourse(item.courseId()).getTitle(),
                        calculateFinalGrade(student.getId(), item.courseId()),
                        getStudentGrades(student.getId(), item.courseId()).size())).toList();
        StudentReport report = new BasicStudentReport(student, results);
        if (includeAttendance) report = new AttendanceReportDecorator(report,
                attendance.values().stream().filter(item -> item.studentId().equals(student.getId())).toList());
        if (includeComments) report = new CommentsReportDecorator(report,
                grades.values().stream().filter(item -> item.studentId().equals(student.getId())).toList());
        return report.render();
    }

    public User findUser(String id) {
        User user = users.get(normalizeId(id));
        if (user == null) throw new IllegalArgumentException("User not found: " + id);
        return user;
    }

    public Course findCourse(String id) {
        Course course = courses.get(normalizeId(id));
        if (course == null) throw new IllegalArgumentException("Course not found: " + id);
        return course;
    }

    public List<User> getUsers() { return List.copyOf(users.values()); }
    public List<Course> getCourses() { return List.copyOf(courses.values()); }
    public List<Enrollment> getEnrollments() { return List.copyOf(enrollments); }
    public List<Grade> getGrades() { return List.copyOf(grades.values()); }
    public List<Attendance> getAttendance() { return List.copyOf(attendance.values()); }
    public List<String> getActivityLog() { return activityLog.getEntries(); }

    public List<Notification> getNotifications(String userId) {
        return notifications.getNotifications(findUser(userId).getId());
    }

    public List<Grade> getStudentGrades(String studentId, String courseId) {
        Enrollment enrollment = requireEnrollment(studentId, courseId);
        return List.copyOf(gradesByEnrollment.get(enrollment).values());
    }

    private User requireRole(String id, Role role) {
        User user = findUser(id);
        if (user.getRole() != role) throw new IllegalArgumentException("User must have role " + role + ".");
        return user;
    }

    private Enrollment requireEnrollment(String studentId, String courseId) {
        Enrollment enrollment = new Enrollment(studentId, courseId);
        if (!enrollments.contains(enrollment)) throw new IllegalArgumentException("Student is not enrolled in this course.");
        return enrollment;
    }

    private static String normalizeId(String id) {
        return TextRules.required(id, "ID", TextRules.ID_MAX);
    }

    private static void validateCategory(Course course, String category) {
        if (course.getGradingMode() == GradingMode.WEIGHTED && !course.getWeights().containsKey(category)) {
            throw new IllegalArgumentException("Category must be one of: " + course.getWeights().keySet());
        }
    }

    private static String emailKey(String email) {
        // Match equalsIgnoreCase without locale-sensitive expansion (including Unicode I variants).
        StringBuilder key = new StringBuilder(email.length());
        email.codePoints().forEach(codePoint ->
                key.appendCodePoint(Character.toLowerCase(Character.toUpperCase(codePoint))));
        return key.toString();
    }

    private record AttendanceKey(String studentId, String courseId, LocalDate date) { }
}
