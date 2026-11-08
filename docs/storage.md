# File storage

`FileSchoolStore` is part of Adil's application/integration work. It uses only the Java standard library.

The snapshot is a UTF-8 Java Properties file with format version `1`. It stores users, courses, grading weights, enrollments, individual grades and attendance. Properties escaping preserves Unicode names, newlines and punctuation. Weight category names are stored as values of indexed entries, so dots or punctuation in category names cannot change the file structure.

`load(Path)` returns a new `SchoolFacade`. A missing file means a new empty school. An existing empty, incomplete, unsupported or invalid file raises `IOException`; it is never silently replaced with an empty school. Validation follows the same facade operations used by the console: users first, then courses, enrollments, grades and attendance. Invalid references therefore fail without partially changing an existing school or changing the source file.

Notifications and the activity log are derived from grades, and are not stored separately. Loading replays each grade once through the facade, recreating its notification event once. Read/unread state is not part of this format.

Saving builds the entire snapshot before touching the destination. A temporary file is written in the same directory and moved over the destination atomically when the filesystem supports it. Otherwise a replacement move is used. Temporary files are removed after success or failure. The format limits the complete file to 10 MiB, all record counts together to 100,000, and grading categories per course to 1,000. These limits are checked on both read and write; malformed UTF-8, negative counts and non-finite numbers are rejected.

Unknown fields, record counts that leave unconsumed entries, and duplicate attendance records for the same student/course/date are rejected rather than silently losing data.

`SchoolFileLock.acquire(dataFile)` provides an exclusive session lock. The application must acquire it before loading and keep it until the interactive session and final save finish. A second application instance opening the same snapshot fails with a clear message. The lock is held on a stable `.lock` sidecar, not on the snapshot that saving replaces. The sidecar is deliberately retained after the lock is released; an empty sidecar does not mean the application is still running. Directory paths are resolved to their real location so directory aliases share a lock. This protects cooperating application instances, not edits by unrelated external programs. Memory mode and the standalone demo do not need a file lock.

Repeated acquisitions within one JVM are rejected before opening a second file descriptor. This matters on POSIX systems where closing that second descriptor could release the process's existing lock. The lock tests check both same-process rejection and a separate Java process, including that a failed same-process acquisition leaves the original operating-system lock intact.

The storage tests cover Unicode and special characters, weighted grading metadata, attendance, regenerated notifications, repeated save/load, missing files, invalid versions, missing fields, invalid references, invalid booleans, excessive or inconsistent counts, duplicate attendance records, oversized files, malformed UTF-8, replacing existing snapshots, and cleanup of temporary files.
