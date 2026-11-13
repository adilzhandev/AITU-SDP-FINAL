# Defense walkthrough

## Prepare

Install a JDK 17 or newer. From the repository root run `java Run.java test` and then `java Run.java demo`.
The demo uses fictional data and never changes the normal saved school.

## Live console walkthrough

Start `java Run.java --memory`, then select:

1. **13**: seed demo data. Factory Method creates all three user types; Builder configures two courses.
2. **1** and **3**: inspect users, teachers, schedules, capacity and grading categories.
3. **8**, student **S1**, course **C1**: weighted result **78.00%** (90% homework at 40%; 70% exam at 60%).
4. **8**, student **S1**, course **C2**: points result **80.00%**, using the same earned/possible values.
5. **7**: add a valid assessment for S2/C1, category homework, earned80, maximum100, optional comment.
6. **11**, **S2**: view the new student notification. **11**, **T1**: view teacher notifications. **12**: show the second independent Observer, the activity log.
7. **10**, **S1**, **n**, **n**: base report. Repeat with **y/n**, **n/y** and **y/y** to show the two composable Decorators.
8. **5**, **S1**, **C1**: duplicate enrollment is rejected. **7** with category **unknown** for C1 is rejected without extra notifications.
9. **0**: exit. For persistence use normal mode instead of `--memory`, then restart and verify the records remain.

## Ownership

| Member | Explain and demonstrate |
| --- | --- |
| Nurlan-byte | Factory Method: creators/products; Observer: publish/subscribe, recipient inbox and independent activity log |
| Asylstyle | Builder: required/optional values and immutable result; Strategy: interchangeable grading algorithms and partial results |
| adilzhandev | Facade: coordinated workflow and validation; Decorator: wrapping and optional report sections; application integration |

Each member should explain their classes, why the pattern fits, its tradeoffs, and one successful and one rejected scenario.

## Submission materials

The repository contains source code, tests, pattern explanations and Mermaid UML source. The final course submission additionally needs the team's PDF report (Introduction, Body, Conclusion, Further work, code, UML and actual console screenshots) and a ZIP of source. These documents describe the implemented application; they do not claim that the final PDF has already been submitted.
