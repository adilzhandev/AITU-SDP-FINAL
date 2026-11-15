# School Management System

A console application written in pure Java for the Software Design Patterns final
project. Six design patterns work together in one application. It uses only the
JDK standard library: no framework, external database, or third-party dependency.

## Requirements and quick start

Install **JDK 17 or newer** and make its `java` command available on `PATH`.
Run the following commands from the project directory. They work with the same
Java launcher on Windows, macOS, and Linux.

```text
java Run.java
java Run.java demo
java Run.java test
java Run.java build
```

- No arguments: compile and start the interactive menu with persistent storage.
- `demo`: run a reproducible demonstration of all six patterns without reading or
  changing saved data.
- `test`: compile the application and tests, then run `school.AllTests`.
- `build`: compile the application into `out` without starting it.

The launcher uses the compiler provided by the JDK, targets Java 17, and forwards
the launched application's exit status. No shell script or package manager is needed.

In IntelliJ IDEA, choose a JDK 17+ for the project and open the included module.
`src` is the production source root and `tests` is the test source root. Run
`school.Main` for the console or `school.AllTests` for tests. `Run.java` is the
terminal launcher; build output and saved data are excluded from source roots.

## Application modes

```text
java Run.java --memory
java Run.java --data "data/my-school.properties"
java Run.java --help
```

The default snapshot is `data/school.properties`. Successful changes are saved
automatically, and the menu also provides **Save** and **Save and exit** actions.
`--memory` keeps all data only until the program exits. `--data` selects another
snapshot file. A missing file starts an empty school; an invalid existing file
is reported rather than replaced with an empty school.

After compiling, the application can also be started directly:

```text
java -cp out school.Main --memory
```

## What the menu can do

- Create and list students, teachers, and administrators.
- Build courses with a teacher, schedule, capacity, and grading scheme.
- Enroll students and view course rosters; duplicate enrollment and capacity
  violations are rejected.
- Add assessment results, edit an existing assessment by its stable ID (menu **15**),
  calculate final grades, and record attendance.
- View student/teacher notifications and an independent activity log.
- Compose a student report with optional attendance and teacher-comment sections.
- Export the report as UTF-8 TXT or CSV (menu **16**).
- Load sample data into an empty school for an interactive demonstration.

This is a **single-operator educational application**. User roles describe school
profiles; there is no login system or authentication. Each new grade is another
assessment. Editing replaces its category, points and/or comment while preserving
its ID, student and course. Blank edit fields keep their existing values;
`/clear` removes the comment. Successful corrections recalculate results and
notify the student and teacher. Attendance replaces an existing entry for the
same student, course, and date.

Invalid field values are requested again without discarding earlier answers.
Enter `/cancel` at any prompt to return to the menu without applying the action.
Memory mode explicitly reports that saving school data is unavailable; report
export still works.

TXT exports match the chosen on-screen report. CSV exports contain a student
row, course summaries and the selected attendance/comment rows. Existing files
are never overwritten: choose a new destination for each export. See
[storage and export details](docs/storage.md) for format and capacity rules.

Points grading uses total earned points divided by total possible points.
Weighted grading applies the configured category percentages; categories without
grades contribute **zero** until graded. Category weights must total 100%.

## Patterns for the defense

| Presenter | Pattern | Implementation |
| --- | --- | --- |
| `Nurlan-byte` | Factory Method | `UserFactory` and concrete factories create student, teacher, and admin profiles. |
| `Nurlan-byte` | Observer | `EventPublisher` notifies `NotificationCenter` and an independent `ActivityLogObserver` after a valid grade is recorded or corrected. |
| `AsylStyle` | Builder | `CourseBuilder` constructs an immutable course and validates its configuration. |
| `AsylStyle` | Strategy | `PointsGradeStrategy` and `WeightedGradeStrategy` calculate interchangeable grading algorithms. |
| `adilzhandev` | Facade | `SchoolFacade` coordinates enrollment, grade validation, events, and reports. |
| `adilzhandev` | Decorator | `AttendanceReportDecorator` and `CommentsReportDecorator` extend a basic student report. |

## Structure and documentation

```text
Run.java                       Java build/test/run launcher
src/school/Main.java           Application entry point
src/school/ui/                 Console menu and input handling
src/school/users/              User profiles and factories
src/school/courses/            Course and explicit builder
src/school/grades/             Assessment records and grading strategies
src/school/notifications/      Events and observers
src/school/application/        Facade, enrollment, attendance, demo data
src/school/reports/            Basic report, decorators and TXT/CSV export
src/school/storage/            Snapshot persistence and session file lock
src/school/validation/         Shared text validation and storage limits
tests/school/                  Dependency-free test suite
docs/                         Pattern explanations and storage documentation
```

- [Factory Method and Observer](docs/nurlan-patterns.md)
- [Builder and Strategy](docs/asyl-patterns.md)
- [Facade and Decorator](docs/adil-patterns.md)
- [File storage and locking](docs/storage.md)
- [Step-by-step defense walkthrough](docs/defense-demo.md)

Snapshots are UTF-8 Java Properties files. A session holds an exclusive lock on a
stable `.lock` sidecar so another application instance cannot open the same data
file concurrently. The sidecar remains after exit; its presence does not mean a
lock is still held. Saving replaces the snapshot through a temporary file, using
an atomic move where supported. Notifications and activity logs are reconstructed
from the current saved grade records when loading; the history of individual
corrections is session-only. Version 1 snapshots are read and migrated to version
2, which stores stable grade IDs, on the next successful save.

## Verification and defense materials

Run `java Run.java test` to check module behavior, facade integration, report
composition, console input, file persistence, and file locking. The executable
demo is a short defense scenario: create profiles, build courses, enroll a
student, record grades, notify observers, and compose a report.

The final submission also includes a PDF report, console screenshots, and a ZIP of the source code.
