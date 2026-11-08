package school.application;

import java.util.ArrayList;
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

/** Facade: coordinates user creation, enrollment, grading, events and report composition. */
public final class SchoolFacade {
    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Course> courses = new LinkedHashMap<>();
    private final Set<Enrollment> enrollments = new LinkedHashSet<>();
    private final List<Grade> grades = new ArrayList<>();
    private final List<Attendance> attendance = new ArrayList<>();
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
        if (users.values().stream().anyMatch(existing -> existing.getEmail().equalsIgnoreCase(user.getEmail()))) {
            throw new IllegalArgumentException("Email already belongs to another user.");
        }
        users.put(user.getId(), user);
        return user;
    }

    public void addCourse(Course course) {
        Objects.requireNonNull(course, "Course is required.");
        requireRole(course.getTeacherId(), Role.TEACHER);
        if (courses.containsKey(course.getId())) throw new IllegalArgumentException("Course ID already exists.");
        courses.put(course.getId(), course);
    }

    public void enroll(String studentId, String courseId) {
        Enrollment enrollment = new Enrollment(studentId, courseId);
        requireRole(enrollment.studentId(), Role.STUDENT);
        Course course = findCourse(enrollment.courseId());
        if (enrollments.contains(enrollment)) throw new IllegalArgumentException("Student is already enrolled.");
        long size = enrollments.stream().filter(item -> item.courseId().equals(course.getId())).count();
        if (size >= course.getCapacity()) throw new IllegalArgumentException("Course is full.");
        enrollments.add(enrollment);
    }

    public void recordGrade(Grade grade) {
        Objects.requireNonNull(grade, "Grade is required.");
        requireEnrollment(grade.studentId(), grade.courseId());
        Course course = findCourse(grade.courseId());
        if (course.getGradingMode() == GradingMode.WEIGHTED && !course.getWeights().containsKey(grade.category())) {
            throw new IllegalArgumentException("Category must be one of: " + course.getWeights().keySet());
        }
        // Calculate before changing state: invalid data must not create grades or notifications.
        List<Grade> prospective = new ArrayList<>(getStudentGrades(grade.studentId(), grade.courseId()));
        prospective.add(grade);
        GradingStrategies.forMode(course.getGradingMode()).calculate(prospective, course.getWeights());
        grades.add(grade);
        String message = String.format(Locale.ROOT, "%s: %s received %.2f/%.2f in %s",
                course.getTitle(), findUser(grade.studentId()).getName(), grade.earned(), grade.possible(), grade.category());
        events.publish(new SchoolEvent(grade.studentId(), course.getTeacherId(), course.getId(), message));
    }

    public void recordAttendance(Attendance entry) {
        Objects.requireNonNull(entry, "Attendance is required.");
        requireEnrollment(entry.studentId(), entry.courseId());
        attendance.removeIf(old -> old.studentId().equals(entry.studentId())
                && old.courseId().equals(entry.courseId()) && old.date().equals(entry.date()));
        attendance.add(entry);
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
                attendance.stream().filter(item -> item.studentId().equals(student.getId())).toList());
        if (includeComments) report = new CommentsReportDecorator(report,
                grades.stream().filter(item -> item.studentId().equals(student.getId())).toList());
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
    public List<Grade> getGrades() { return List.copyOf(grades); }
    public List<Attendance> getAttendance() { return List.copyOf(attendance); }
    public List<String> getActivityLog() { return activityLog.getEntries(); }

    public List<Notification> getNotifications(String userId) {
        return notifications.getNotifications(findUser(userId).getId());
    }

    public List<Grade> getStudentGrades(String studentId, String courseId) {
        Enrollment enrollment = requireEnrollment(studentId, courseId);
        return grades.stream().filter(grade -> grade.studentId().equals(enrollment.studentId())
                && grade.courseId().equals(enrollment.courseId())).toList();
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
        if (id == null || id.isBlank()) throw new IllegalArgumentException("ID is required.");
        return id.strip();
    }
}
