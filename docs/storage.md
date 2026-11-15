# File storage

The snapshot is a UTF-8 Java Properties file with format version `2`. It stores users, courses, grading weights, enrollments, individual grades with stable positive IDs, and attendance. Properties escaping preserves Unicode names, newlines and punctuation. Weight category names are stored as values of indexed entries, so dots or punctuation in category names cannot change the file structure.

Version `1` snapshots are also readable when their data passes the current validation rules. Their grades receive IDs `1..N` in stored order. Loading does not rewrite the file; the next successful save writes version `2`. Subsequent new grades use an ID above the largest stored ID. Duplicate, zero or negative stored grade IDs are invalid. Editing preserves the ID and updates the same record rather than appending another assessment.

`load(Path)` returns a new `SchoolFacade`. A missing file means a new empty school. An existing empty, incomplete, unsupported or invalid file raises `IOException`; it is never silently replaced with an empty school. Validation follows the same facade operations used by the console: users first, then courses, enrollments, grades and attendance. Invalid references therefore fail without partially changing an existing school or changing the source file.

Notifications and the activity log are derived from grades, and are not stored separately. Loading replays each current grade once through the facade, recreating its notification event once. Correction history is available only in the session where the edit occurred; restarting does not restore previous values or correction events. Read/unread state is not part of this format.

Saving builds the entire snapshot before touching the destination. A temporary file is written in the same directory and moved over the destination atomically when the filesystem supports it. Otherwise a replacement move is used. Temporary files are removed after success or failure. The format limits the complete file to 10 MiB, all record counts together to 100,000, and grading categories per course to 1,000. These limits are checked on both read and write; malformed UTF-8, negative counts and non-finite numbers are rejected.

The facade also reserves a conservative serialized-size budget **before** every mutation. A rejected insertion or edit changes neither records nor notifications. Shortening an existing comment releases budget for later data. Because the budget reserves space for property keys and headers, it can refuse additions before the actual file reaches 10 MiB. This prevents accepting data that cannot be saved within the format limits; filesystem errors such as a full disk can still prevent a save.

Text limits after trimming are 64 UTF-16 code units for IDs and categories, 200 for names/titles/schedules, 254 for email addresses and 4,000 for comments. Category limits also apply after lowercase normalization. Unpaired UTF-16 surrogates are rejected, including escaped surrogate values in Properties files. Email addresses cannot contain Unicode whitespace. Valid Unicode, punctuation and multiline comments remain supported.

Unknown fields, record counts that leave unconsumed entries, and duplicate attendance records for the same student/course/date are rejected rather than silently losing data.

`SchoolFileLock.acquire(dataFile)` provides an exclusive session lock. The application must acquire it before loading and keep it until the interactive session and final save finish. A second application instance opening the same snapshot fails with a clear message. The lock is held on a stable `.lock` sidecar, not on the snapshot that saving replaces. The sidecar stays on disk after the session ends. Directory paths are resolved to their real location so directory aliases share a lock. This protects cooperating application instances, not edits by unrelated external programs. Memory mode and the standalone demo do not need a file lock.

A data-file symlink is rejected on locking, loading and saving, so opening an alias cannot bypass the session lock or replace the symlink with a separate snapshot. Use the target file path instead. Parent-directory symlinks still resolve to the same lock location.
All three operations first normalize the absolute path, including `.` and `..`, so they consistently read, lock and write the same destination.

Repeated acquisitions within one JVM are rejected before opening a second file descriptor. This matters on POSIX systems where closing that second descriptor could release the process's existing lock. The lock tests check both same-process rejection and a separate Java process, including that a failed same-process acquisition leaves the original operating-system lock intact.

The storage tests cover Unicode and special characters, weighted grading metadata, attendance, regenerated notifications, repeated save/load, missing files, invalid versions, missing fields, invalid references, invalid booleans, excessive or inconsistent counts, duplicate attendance records, oversized files, malformed UTF-8, replacing existing snapshots, and cleanup of temporary files.

## Report export

Menu **16** exports a selected student's report with independently selected attendance and comment sections. TXT uses exactly the report text shown by menu **10**. CSV is UTF-8 with one header and 13 columns:

```text
section,student_id,student_name,course_id,course_title,grade_id,category,earned,possible,result_percent,date,present,comment
```

CSV contains a student row even when the student has no enrollments, a summary row for each enrolled course, optional attendance rows, and optional rows for grades with nonempty comments. Numeric values use a dot independent of the computer's locale. Commas, double quotes and newlines are quoted correctly. Text beginning with a spreadsheet formula marker is prefixed with an apostrophe for safer spreadsheet import.

Export creates parent directories when needed and writes a new destination through a temporary file. Existing files, including symlinks, are rejected without replacement; the console also blocks the current snapshot path. Reports are separate output files and do not modify school data. `GradePersistenceTest`, `ReportExportTest`, `CapacityTest` and console tests cover migration, editing/reloading, export options, escaping, overwrite protection and rejection at capacity.
