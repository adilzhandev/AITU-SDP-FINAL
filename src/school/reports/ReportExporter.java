package school.reports;

import school.application.Attendance;
import school.application.Enrollment;
import school.application.SchoolFacade;
import school.courses.Course;
import school.grades.Grade;
import school.users.Role;
import school.users.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

/** Exports reports as UTF-8 text or structured CSV without changing school data. */
public final class ReportExporter {
    /** Creates a new report file; existing files and symbolic links are never overwritten. */
    public Path export(SchoolFacade school, String studentId, boolean includeAttendance,
                       boolean includeComments, ExportFormat format, Path destination) throws IOException {
        Objects.requireNonNull(school, "School is required.");
        Objects.requireNonNull(format, "Export format is required.");
        Objects.requireNonNull(destination, "Destination is required.");
        User student = school.findUser(studentId);
        if (student.getRole() != Role.STUDENT) {
            throw new IllegalArgumentException("Report requires a student.");
        }
        Path target = destination.toAbsolutePath().normalize();
        Path directory = target.getParent();
        if (directory == null || target.getFileName() == null) {
            throw new IOException("A report file path is required.");
        }
        requireNewFile(target);
        String content = switch (format) {
            case TXT -> school.generateReport(student.getId(), includeAttendance, includeComments);
            case CSV -> csv(school, student, includeAttendance, includeComments);
        };
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".school-report-", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            // Do not request ATOMIC_MOVE: providers may replace its target even without
            // REPLACE_EXISTING. A regular non-replacing move preserves existing reports.
            Files.move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }

    private static String csv(SchoolFacade school, User student, boolean includeAttendance,
                              boolean includeComments) {
        StringBuilder output = new StringBuilder();
        row(output, "section", "student_id", "student_name", "course_id", "course_title",
                "grade_id", "category", "earned", "possible", "result_percent", "date", "present", "comment");
        row(output, "student", text(student.getId()), text(student.getName()), "", "", "", "", "", "", "", "", "", "");
        for (Enrollment enrollment : school.getEnrollments()) {
            if (!enrollment.studentId().equals(student.getId())) continue;
            Course course = school.findCourse(enrollment.courseId());
            String result = school.getStudentGrades(student.getId(), course.getId()).isEmpty() ? ""
                    : String.format(Locale.ROOT, "%.2f", school.calculateFinalGrade(student.getId(), course.getId()));
            row(output, "course", text(student.getId()), text(student.getName()), text(course.getId()),
                    text(course.getTitle()), "", "", "", "", result, "", "", "");
        }
        if (includeAttendance) {
            for (Attendance entry : school.getAttendance().stream()
                    .filter(item -> item.studentId().equals(student.getId()))
                    .sorted(Comparator.comparing(Attendance::date).thenComparing(Attendance::courseId)).toList()) {
                Course course = school.findCourse(entry.courseId());
                row(output, "attendance", text(student.getId()), text(student.getName()), text(course.getId()),
                        text(course.getTitle()), "", "", "", "", "", entry.date().toString(),
                        Boolean.toString(entry.present()), "");
            }
        }
        if (includeComments) {
            for (Grade grade : school.getGrades()) {
                if (!grade.studentId().equals(student.getId()) || grade.comment().isBlank()) continue;
                Course course = school.findCourse(grade.courseId());
                row(output, "comment", text(student.getId()), text(student.getName()), text(course.getId()),
                        text(course.getTitle()), Long.toString(grade.id()), text(grade.category()),
                        Double.toString(grade.earned()), Double.toString(grade.possible()), "", "", "", text(grade.comment()));
            }
        }
        return output.toString();
    }

    /** Prefix formula-like text with an apostrophe for safe spreadsheet import. */
    private static String text(String value) {
        int first = 0;
        while (first < value.length()) {
            char character = value.charAt(first);
            if (character > ' ' && !Character.isWhitespace(character) && !Character.isSpaceChar(character)) break;
            first++;
        }
        boolean controlPrefix = !value.isEmpty()
                && (value.charAt(0) == '\t' || value.charAt(0) == '\r' || value.charAt(0) == '\n');
        if (controlPrefix || first < value.length() && "=+-@".indexOf(value.charAt(first)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    private static void row(StringBuilder output, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i != 0) output.append(',');
            String field = fields[i];
            if (field.indexOf(',') >= 0 || field.indexOf('"') >= 0
                    || field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0) {
                output.append('"').append(field.replace("\"", "\"\"")).append('"');
            } else {
                output.append(field);
            }
        }
        output.append("\r\n");
    }

    private static void requireNewFile(Path target) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(target.toString(), null,
                    "Report destination already exists. Choose another file name.");
        }
    }
}
