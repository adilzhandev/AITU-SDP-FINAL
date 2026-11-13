package school;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import school.application.DemoData;
import school.application.SchoolFacade;
import school.grades.GradingMode;
import school.storage.FileSchoolStore;
import school.ui.ConsoleMenu;
import school.users.Role;

/** Drives the real console adapter through injected input/output without touching saved files. */
public final class ConsoleTest {
    private ConsoleTest() { }

    public static void run() throws Exception {
        recoversFromInvalidMenuAndActionInput();
        runsDemoReportAndNotificationFlow();
        eofDoesNotPartiallyCreateRecords();
        eofDuringGradeDoesNotNotifyOrSaveGrade();
        validatesNormalizedWeightCategoriesAndRecovers();
        retriesYesNoWithoutLosingTheAction();
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("ConsoleTest passed.");
    }

    private static String runConsole(SchoolFacade school, String input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (Scanner scanner = new Scanner(input);
             PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            new ConsoleMenu(scanner, stream, school, new FileSchoolStore(), null).run();
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static void recoversFromInvalidMenuAndActionInput() throws Exception {
        SchoolFacade school = new SchoolFacade();
        String output = runConsole(school, "banana\n99\n2\n8\n1\nS1\nStudent\ninvalid-email\nstudent@school.test\n1\n0\n");
        check(output.contains("Enter a whole number."), "Non-numeric menu choice is explained");
        check(output.contains("Choose a number from the menu."), "Unknown menu action is handled");
        check(output.contains("Role must be 1, 2 or 3."), "Invalid role prompts again");
        check(output.contains("email must have a valid address format"), "Invalid email is explained");
        check(output.contains("S1 | STUDENT | Student | student@school.test"), "Corrected form retains role, ID and name");
        check(output.contains("Goodbye."), "Console exits normally after invalid input");
        check(school.getUsers().size() == 1, "Correcting an invalid field creates exactly one user");
    }

    private static void runsDemoReportAndNotificationFlow() throws Exception {
        SchoolFacade school = new SchoolFacade();
        String output = runConsole(school, "13\n10\nS1\ny\ny\n11\nS1\n0\n");
        check(output.contains("Demo loaded"), "Demo menu option loads the school");
        check(output.contains("STUDENT REPORT") && output.contains("78.00%") && output.contains("80.00%"),
                "Console displays the composed report with both strategy results");
        check(output.contains("ATTENDANCE") && output.contains("TEACHER COMMENTS"), "Both report decorators appear");
        check(output.contains("- Software Design Patterns: Aruzhan Student received 90.00/100.00 in homework"),
                "Recipient inbox is displayed after report action");
        check(output.contains("Goodbye.") && !output.contains("Error:"), "Demo flow completes without errors");
        check(school.getGrades().size() == 4 && school.getNotifications("S1").size() == 4,
                "Viewing reports and notifications must not add grades or events");
    }

    private static void eofDoesNotPartiallyCreateRecords() throws Exception {
        SchoolFacade users = new SchoolFacade();
        String userOutput = runConsole(users, "2\n1\nS1\nIncomplete Student\n");
        check(users.getUsers().isEmpty(), "EOF before email must not partially create user");
        check(userOutput.contains("Input ended.") && userOutput.contains("Goodbye."), "User-action EOF exits cleanly");

        SchoolFacade courses = new SchoolFacade();
        courses.addUser(Role.TEACHER, "T1", "Teacher", "teacher@school.test");
        String courseOutput = runConsole(courses, "4\nC1\nIncomplete Course\nT1\n");
        check(courses.getCourses().isEmpty() && courses.getUsers().size() == 1,
                "EOF while building course preserves existing users and adds no course");
        check(courseOutput.contains("Input ended.") && courseOutput.contains("Goodbye."), "Course-action EOF exits cleanly");

        String emptyOutput = runConsole(new SchoolFacade(), "");
        check(emptyOutput.contains("Input ended.") && emptyOutput.contains("Goodbye."), "Empty input exits cleanly");
    }

    private static void eofDuringGradeDoesNotNotifyOrSaveGrade() throws Exception {
        SchoolFacade school = new SchoolFacade();
        DemoData.populate(school);
        var gradesBefore = school.getGrades();
        var notificationsBefore = school.getNotifications("S1");
        var activityBefore = school.getActivityLog();
        String output = runConsole(school, "7\nS1\nC1\nexam\n95\n100\n");
        check(output.contains("Input ended.") && output.contains("Goodbye."), "Grade-action EOF exits cleanly");
        check(school.getGrades().equals(gradesBefore), "EOF before comment must not save a partial grade");
        check(school.getNotifications("S1").equals(notificationsBefore), "Incomplete grade must not notify student");
        check(school.getActivityLog().equals(activityBefore), "Incomplete grade must not enter activity log");
    }

    private static void validatesNormalizedWeightCategoriesAndRecovers() throws Exception {
        SchoolFacade school = new SchoolFacade();
        school.addUser(Role.TEACHER, "T1", "Teacher", "teacher@school.test");
        String input = "4\nC2\nWeighted Course\nT1\n\n\n2\nExam=50, exam =50\nexam=90\n HOMEWORK =40, EXAM=60\n3\n0\n";
        String output = runConsole(school, input);
        check(output.contains("Duplicate category: exam"), "Weights reject duplicates after normalization");
        check(school.getCourses().size() == 1 && school.findCourse("C2").getGradingMode() == GradingMode.WEIGHTED,
                "Corrected weights preserve the course form and create one course");
        check(school.findCourse("C2").getWeights().get("homework") == 40.0
                && school.findCourse("C2").getWeights().get("exam") == 60.0,
                "Valid categories are normalized and keep their entered weights");
        check(output.contains("Created course C2 using Builder.") && output.contains("Category weights (%):"),
                "Menu recovers and lists the newly created course");
    }

    private static void retriesYesNoWithoutLosingTheAction() throws Exception {
        SchoolFacade school = new SchoolFacade();
        DemoData.populate(school);
        String output = runConsole(school, "10\nS1\nmaybe\nY\nнет\n0\n");
        check(output.contains("Enter y or n."), "Invalid yes/no answer prompts again");
        check(output.contains("STUDENT REPORT") && output.contains("ATTENDANCE")
                && !output.contains("TEACHER COMMENTS"), "Retried answer keeps the report action and choices");
        check(output.contains("Goodbye."), "Console exits after the corrected report action");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
