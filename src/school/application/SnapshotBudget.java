package school.application;

import school.courses.Course;
import school.grades.Grade;
import school.users.User;
import school.validation.TextRules;

/**
 * Incremental upper bound for the UTF-8 version 2 Properties snapshot.
 * Each property gets 64 bytes for its ASCII key, separator and line ending;
 * keys remain shorter than this under the record/category limits. Values use
 * their actual Properties escaping and UTF-8 cost. The 1024-byte fixed reserve
 * covers the version, five counts, comment and timestamp. Consequently every
 * accepted mutation remains serializable within the file and record limits.
 */
final class SnapshotBudget {
    private static final long PROPERTY_OVERHEAD = 64;
    private long bytes = 1024;
    private int records;

    void add(long cost) {
        if (records >= TextRules.MAX_RECORDS) {
            throw new IllegalArgumentException("School data exceeds the 100000 record limit.");
        }
        checkBytes(bytes + cost);
        bytes += cost;
        records++;
    }

    void replace(long previousCost, long nextCost) {
        long nextBytes = bytes - previousCost + nextCost;
        checkBytes(nextBytes);
        bytes = nextBytes;
    }

    private static void checkBytes(long value) {
        if (value > TextRules.MAX_FILE_BYTES) {
            throw new IllegalArgumentException(
                    "School data has reached the 10 MiB snapshot limit. Shorten existing comments before adding more data.");
        }
    }

    static long cost(User user) {
        return properties(user.getRole(), user.getId(), user.getName(), user.getEmail());
    }

    static long cost(Course course) {
        if (course.getWeights().size() > TextRules.MAX_WEIGHTS) {
            throw new IllegalArgumentException("Too many grading categories.");
        }
        long cost = properties(course.getId(), course.getTitle(), course.getTeacherId(),
                course.getSchedule(), course.getGradingMode(), course.getCapacity(), course.getWeights().size());
        for (var entry : course.getWeights().entrySet()) {
            cost += properties(entry.getKey(), entry.getValue());
        }
        return cost;
    }

    static long cost(Enrollment enrollment) {
        return properties(enrollment.studentId(), enrollment.courseId());
    }

    static long cost(Grade grade) {
        return properties(grade.id(), grade.studentId(), grade.courseId(), grade.category(),
                grade.earned(), grade.possible(), grade.comment());
    }

    static long cost(Attendance attendance) {
        return properties(attendance.studentId(), attendance.courseId(), attendance.date(), attendance.present());
    }

    private static long properties(Object... values) {
        long total = PROPERTY_OVERHEAD * values.length;
        for (Object value : values) total += escapedValueBytes(value.toString());
        return total;
    }

    /** Properties.store(Writer, ...) escapes punctuation but retains Unicode. */
    private static long escapedValueBytes(String value) {
        long total = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' || ch == '\t' || ch == '\n' || ch == '\r' || ch == '\f'
                    || ch == '=' || ch == ':' || ch == '#' || ch == '!' || (ch == ' ' && i == 0)) {
                total += 2;
            } else if (ch <= 0x7f) {
                total++;
            } else if (ch <= 0x7ff) {
                total += 2;
            } else if (Character.isHighSurrogate(ch)) {
                // Domain constructors reject malformed surrogate pairs before this point.
                total += 4;
                i++;
            } else {
                total += 3;
            }
        }
        return total;
    }
}
