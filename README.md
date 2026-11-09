# School Management System

A console and Swing desktop application written in pure Java for the Software Design Patterns final
project. Six design patterns work together in one application. It uses only the
JDK standard library: no framework, external database, or third-party dependency.

## Requirements and quick start

Install **JDK 17 or newer** and make its `java` command available on `PATH`.
Run the following commands from the project directory. They work with the same
Java launcher on Windows, macOS, and Linux.

```text
java Run.java
java Run.java gui --demo
java Run.java gui
java Run.java demo
java Run.java test
java Run.java build
```

- No arguments: compile and start the interactive menu with persistent storage.
- `gui --demo`: open the desktop interface with fictional sample data in memory.
- `gui`: open the desktop interface with the same persistent storage as the console.
- `demo`: run a reproducible demonstration of all six patterns without reading or
  changing saved data.
- `test`: compile the application and tests, then run `school.AllTests`.
- `build`: compile the application into `out` without starting it.

The launcher uses the compiler provided by the JDK, targets Java 17, and forwards
the launched application's exit status. No shell script or package manager is needed.

**Быстрый запуск:** установите JDK 17 или новее, откройте терминал в папке проекта
и выполните `java Run.java gui --demo` для наглядной демонстрации в окне.
Для сохранения данных используйте `java Run.java gui`, для консоли —
`java Run.java`, для проверок — `java Run.java test`. Оба интерфейса написаны
на Java, без сторонних библиотек.

## Desktop interface

The desktop uses Swing and the bundled Nimbus light theme from the JDK, so its
controls remain readable with either a light or dark operating-system theme.
It is a local application window, not a
website, and requires a graphical desktop. The six existing patterns and
`SchoolFacade` are shared with the console.

```text
java Run.java gui --demo
java Run.java gui --memory
java Run.java gui --data "data/my-school.properties"
java Run.java gui --help
```

Use the tabs to inspect the dashboard, create profiles and courses, enroll
students, record grades and attendance, compose reports, and view notifications.
**Load demo data** is available only for an empty school. `gui --demo` starts
with the sample school without reading or writing your normal snapshot.

Persistent mode saves after each successful change. The status bar reports
unsaved changes if saving fails; **Save now** retries. **Save backup copy** lets
you choose a destination and exports a complete snapshot there. Existing files
require overwrite confirmation, and an in-use destination is rejected.
Closing with unsaved changes offers backup, discard, or cancel. A console and
desktop window cannot edit the same data file simultaneously because they use
the same exclusive session lock.

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
- Add assessment results, calculate final grades, and record attendance.
- View student/teacher notifications and an independent activity log.
- Compose a student report with optional attendance and teacher-comment sections.
- Load sample data into an empty school for an interactive demonstration.

This is a **single-operator educational application**. User roles describe school
profiles; there is no login system or authentication. Grade records are
**append-only**: each new entry is another assessment. Attendance replaces an
existing entry for the same student, course, and date.

Points grading uses total earned points divided by total possible points.
Weighted grading applies the configured category percentages; categories without
grades contribute **zero** until graded. Category weights must total 100%.

## Patterns and assigned work

| Participant | Pattern | Implementation |
| --- | --- | --- |
| `Nurlan-byte` | Factory Method | `UserFactory` and concrete factories create student, teacher, and admin profiles. |
| `Nurlan-byte` | Observer | `EventPublisher` notifies `NotificationCenter` and an independent `ActivityLogObserver` after a valid grade is recorded. |
| `AsylStyle` | Builder | `CourseBuilder` constructs an immutable course and validates its configuration. |
| `AsylStyle` | Strategy | `PointsGradeStrategy` and `WeightedGradeStrategy` calculate interchangeable grading algorithms. |
| `adilzhandev` | Facade | `SchoolFacade` coordinates enrollment, grade validation, events, and reports. |
| `adilzhandev` | Decorator | `AttendanceReportDecorator` and `CommentsReportDecorator` extend a basic student report. |

Implementation is AI-assisted. Each assigned team member should review, test,
and be able to explain their two patterns and their interaction with the project.

## Structure and documentation

```text
Run.java                       Java build/test/run launcher
src/school/Main.java           Application entry point
src/school/GuiMain.java        Swing desktop entry point
src/school/ui/                 Console menu, Swing view and desktop session
src/school/users/              User profiles and factories
src/school/courses/            Course and explicit builder
src/school/grades/             Assessment records and grading strategies
src/school/notifications/      Events and observers
src/school/application/        Facade, enrollment, attendance, demo data
src/school/reports/            Basic report and decorators
src/school/storage/            Snapshot persistence and session file lock
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
from saved grade records when loading.

## Verification and defense materials

Run `java Run.java test` to check module behavior, facade integration, report
composition, console input, file persistence, and file locking. The tests also
cover desktop autosave, failed-save recovery, and lock release. The executable
demo is a short defense scenario: create profiles, build courses, enroll a
student, record grades, notify observers, and compose a report.

The pattern notes support preparation for the defense. The final PDF report,
application screenshots, and submission ZIP are separate deliverables; this
README does not claim they are already prepared.
