package school.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import school.application.Attendance;
import school.application.DemoData;
import school.application.Enrollment;
import school.application.SchoolFacade;
import school.courses.Course;
import school.courses.CourseBuilder;
import school.grades.Grade;
import school.grades.GradingMode;
import school.users.Role;
import school.users.User;

/** Swing presentation layer; all changes go through the existing school facade. */
public final class SchoolPanel extends JPanel {
    private static final Color INK = new Color(26, 48, 67);
    private static final Color TEAL = new Color(0, 111, 119);
    private static final Color MUTED = new Color(82, 101, 116);
    private static final Color BACKGROUND = new Color(242, 246, 249);
    private final SchoolFacade school;
    private final Runnable onMutation;
    private final JTabbedPane tabs = new JTabbedPane();
    private final DefaultTableModel users = model("ID", "Name", "Role", "Email");
    private final DefaultTableModel courses = model("ID", "Course", "Teacher", "Schedule", "Enrollment", "Grading");
    private final DefaultTableModel enrollments = model("Student", "Course", "Final result");
    private final DefaultTableModel results = model("Student", "Course", "Policy", "Assessments", "Final result");
    private final DefaultTableModel grades = model("Category", "Earned", "Possible", "Teacher comment");
    private final DefaultTableModel attendance = model("Date", "Status");
    private final JComboBox<EnrollmentPick> assessmentPair = new JComboBox<>();
    private final JComboBox<Pick> reportStudent = new JComboBox<>();
    private final JComboBox<Pick> recipient = new JComboBox<>();
    private final JCheckBox includeAttendance = new JCheckBox("Attendance", true);
    private final JCheckBox includeComments = new JCheckBox("Teacher comments", true);
    private final JTextArea report = textArea();
    private final JTextArea inbox = textArea();
    private final JTextArea activity = textArea();
    private final JLabel gradeSummary = new JLabel("Enroll a student to record assessments or attendance.");
    private final JLabel schoolSummary = new JLabel();
    private final JLabel[] counts = {new JLabel(), new JLabel(), new JLabel(), new JLabel()};
    private final JButton demo;
    private boolean refreshing;

    public SchoolPanel(SchoolFacade school, Runnable onMutation) {
        super(new BorderLayout(0, 16));
        requireEdt();
        this.school = Objects.requireNonNull(school, "school");
        this.onMutation = Objects.requireNonNull(onMutation, "onMutation");
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 22, 16, 22));
        JPanel heading = new JPanel(new BorderLayout(16, 0));
        heading.setOpaque(false);
        JLabel title = new JLabel("School Management");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 27f));
        title.setForeground(INK);
        schoolSummary.setForeground(MUTED);
        JPanel headingText = new JPanel(new GridLayout(2, 1, 0, 5));
        headingText.setOpaque(false);
        headingText.add(title);
        headingText.add(schoolSummary);
        demo = button("Load demo data", () -> mutate(() -> DemoData.populate(school)));
        demo.setToolTipText("Add fictional sample data to an empty school.");
        heading.add(headingText, BorderLayout.CENTER);
        heading.add(row(demo), BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);
        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 13f));
        tabs.addTab("Overview", overview());
        tabs.addTab("Users", users());
        tabs.addTab("Courses", courses());
        tabs.addTab("Grades & attendance", assessments());
        tabs.addTab("Reports", reports());
        tabs.addTab("Notifications", notifications());
        add(tabs, BorderLayout.CENTER);
        refresh();
    }

    private JPanel overview() {
        JPanel page = page();
        JPanel cards = new JPanel(new GridLayout(1, 4, 14, 0));
        cards.setOpaque(false);
        String[] labels = {"Students", "Teachers", "Courses", "Assessments"};
        for (int i = 0; i < labels.length; i++) {
            JPanel card = new JPanel(new BorderLayout(0, 8));
            card.setBackground(Color.WHITE);
            card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(219, 228, 234)),
                    BorderFactory.createEmptyBorder(14, 18, 14, 18)));
            JLabel label = new JLabel(labels[i]);
            label.setForeground(MUTED);
            counts[i].setFont(counts[i].getFont().deriveFont(Font.BOLD, 31f));
            counts[i].setForeground(TEAL);
            card.add(label, BorderLayout.NORTH);
            card.add(counts[i], BorderLayout.CENTER);
            cards.add(card);
        }
        page.add(cards, BorderLayout.NORTH);
        page.add(section("Student progress", table(results)), BorderLayout.CENTER);
        page.add(note("Start with demo data, or add a teacher and student, create a course, then enroll the student."), BorderLayout.SOUTH);
        return page;
    }

    private JPanel users() {
        JPanel page = page();
        page.add(row(button("Add user", this::addUser), note("Student, teacher and administrator profiles")), BorderLayout.NORTH);
        page.add(table(users), BorderLayout.CENTER);
        return page;
    }

    private JPanel courses() {
        JPanel page = page();
        page.add(row(button("Create course", this::addCourse), button("Enroll student", this::enroll)), BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, section("Courses", table(courses)),
                section("Enrollment roster", table(enrollments)));
        split.setResizeWeight(.55);
        split.setBorder(null);
        page.add(split, BorderLayout.CENTER);
        return page;
    }

    private JPanel assessments() {
        JPanel page = page();
        assessmentPair.setPreferredSize(new Dimension(460, 30));
        assessmentPair.addActionListener(event -> { if (!refreshing) refreshAssessments(); });
        JPanel controls = new JPanel(new BorderLayout(0, 10));
        controls.setOpaque(false);
        JPanel actions = new JPanel(new GridLayout(2, 1, 0, 10));
        actions.setOpaque(false);
        actions.add(row(new JLabel("Enrollment"), assessmentPair));
        actions.add(row(button("Add grade", this::addGrade), button("Mark attendance", this::markAttendance)));
        controls.add(actions, BorderLayout.NORTH);
        gradeSummary.setForeground(TEAL);
        controls.add(gradeSummary, BorderLayout.SOUTH);
        page.add(controls, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, section("Assessment results", table(grades)),
                section("Attendance", table(attendance)));
        split.setResizeWeight(.60);
        split.setBorder(null);
        page.add(split, BorderLayout.CENTER);
        page.add(note("Grades add a new assessment. Attendance replaces the entry for the selected date."), BorderLayout.SOUTH);
        return page;
    }

    private JPanel reports() {
        JPanel page = page();
        reportStudent.setPreferredSize(new Dimension(260, 30));
        reportStudent.addActionListener(event -> { if (!refreshing) refreshReport(); });
        includeAttendance.setOpaque(false);
        includeComments.setOpaque(false);
        includeAttendance.addActionListener(event -> refreshReport());
        includeComments.addActionListener(event -> refreshReport());
        page.add(row(new JLabel("Student"), reportStudent, includeAttendance, includeComments,
                button("Generate report", this::refreshReport)), BorderLayout.NORTH);
        page.add(new JScrollPane(report), BorderLayout.CENTER);
        page.add(note("Select either optional section to see the report update. You can select and copy the report text."), BorderLayout.SOUTH);
        return page;
    }

    private JPanel notifications() {
        JPanel page = page();
        recipient.setPreferredSize(new Dimension(330, 30));
        recipient.addActionListener(event -> { if (!refreshing) refreshInbox(); });
        page.add(row(new JLabel("Recipient"), recipient), BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, section("Inbox", new JScrollPane(inbox)),
                section("School activity", new JScrollPane(activity)));
        split.setResizeWeight(.5);
        split.setBorder(null);
        page.add(split, BorderLayout.CENTER);
        page.add(note("Recording a grade notifies its student and teacher and adds a school activity entry."), BorderLayout.SOUTH);
        return page;
    }

    private void addUser() {
        JTextField id = field(""), name = field(""), email = field("");
        JComboBox<Role> role = new JComboBox<>(Role.values());
        role.setSelectedItem(Role.STUDENT);
        confirm("Add school user", form("Role", role, "ID", id, "Full name", name, "Email", email),
                () -> school.addUser((Role) role.getSelectedItem(), id.getText(), name.getText(), email.getText()));
    }

    private void addCourse() {
        List<Pick> teachers = userPicks(Role.TEACHER);
        if (teachers.isEmpty()) { information("Add a teacher in Users before creating a course."); return; }
        JTextField id = field(""), title = field(""), schedule = field("Not scheduled");
        JTextField capacity = field("30"), weights = field("homework=40, exam=60");
        JComboBox<Pick> teacher = new JComboBox<>(teachers.toArray(Pick[]::new));
        JComboBox<GradingMode> mode = new JComboBox<>(GradingMode.values());
        mode.setSelectedItem(GradingMode.POINTS);
        weights.setEnabled(false);
        mode.addActionListener(event -> weights.setEnabled(mode.getSelectedItem() == GradingMode.WEIGHTED));
        confirm("Create course", form("Course ID", id, "Title", title, "Teacher", teacher,
                "Schedule", schedule, "Capacity", capacity, "Grading policy", mode,
                "Category weights (%)", weights), () -> {
            CourseBuilder builder = new CourseBuilder().id(id.getText()).title(title.getText())
                    .teacherId(selected(teacher, "teacher").id()).schedule(schedule.getText())
                    .capacity(integer(capacity.getText(), "Capacity")).gradingMode((GradingMode) mode.getSelectedItem());
            if (mode.getSelectedItem() == GradingMode.WEIGHTED) builder.weights(parseWeights(weights.getText()));
            school.addCourse(builder.build());
        });
    }

    private void enroll() {
        if (userPicks(Role.STUDENT).isEmpty() || school.getCourses().isEmpty()) {
            information("Add a student and a course before enrolling."); return;
        }
        JComboBox<Pick> student = new JComboBox<>(userPicks(Role.STUDENT).toArray(Pick[]::new));
        JComboBox<Pick> course = new JComboBox<>(coursePicks().toArray(Pick[]::new));
        confirm("Enroll student", form("Student", student, "Course", course),
                () -> school.enroll(selected(student, "student").id(), selected(course, "course").id()));
    }

    private void addGrade() {
        EnrollmentPick pair = (EnrollmentPick) assessmentPair.getSelectedItem();
        if (pair == null) { information("Enroll a student in a course first."); return; }
        Course course = school.findCourse(pair.courseId());
        JComboBox<String> category = new JComboBox<>(course.getGradingMode() == GradingMode.WEIGHTED
                ? course.getWeights().keySet().toArray(String[]::new) : new String[]{"homework", "exam", "quiz"});
        category.setEditable(course.getGradingMode() == GradingMode.POINTS);
        JTextField earned = field(""), possible = field("100"), comment = field("");
        confirm("Add grade", form("Enrollment", new JLabel(pair.toString()), "Category", category,
                "Earned points", earned, "Possible points", possible, "Teacher comment", comment),
                () -> school.recordGrade(new Grade(pair.studentId(), pair.courseId(),
                        Objects.toString(category.getSelectedItem(), ""), number(earned.getText(), "Earned points"),
                        number(possible.getText(), "Possible points"), comment.getText())));
    }

    private void markAttendance() {
        EnrollmentPick pair = (EnrollmentPick) assessmentPair.getSelectedItem();
        if (pair == null) { information("Enroll a student in a course first."); return; }
        JTextField date = field(LocalDate.now().toString());
        JCheckBox present = new JCheckBox("Present", true);
        confirm("Mark attendance", form("Enrollment", new JLabel(pair.toString()), "Date (yyyy-MM-dd)", date,
                "Attendance", present), () -> {
            LocalDate parsed;
            try { parsed = LocalDate.parse(date.getText().strip()); }
            catch (DateTimeParseException error) { throw new IllegalArgumentException("Enter a valid date as yyyy-MM-dd."); }
            school.recordAttendance(new Attendance(pair.studentId(), pair.courseId(), parsed, present.isSelected()));
        });
    }

    /** Refresh every view after a mutation while retaining valid current selections. */
    public void refresh() {
        requireEdt();
        refreshing = true;
        try {
            users.setRowCount(0); courses.setRowCount(0); enrollments.setRowCount(0); results.setRowCount(0);
            for (User user : school.getUsers()) users.addRow(new Object[]{user.getId(), user.getName(), user.getRole(), user.getEmail()});
            for (Course course : school.getCourses()) {
                long size = school.getEnrollments().stream().filter(e -> e.courseId().equals(course.getId())).count();
                courses.addRow(new Object[]{course.getId(), course.getTitle(), school.findUser(course.getTeacherId()).getName(),
                        course.getSchedule(), size + " / " + course.getCapacity(), course.getGradingMode()});
            }
            for (Enrollment enrollment : school.getEnrollments()) {
                String student = userLabel(school.findUser(enrollment.studentId()));
                Course course = school.findCourse(enrollment.courseId());
                int count = school.getStudentGrades(enrollment.studentId(), enrollment.courseId()).size();
                String value = count == 0 ? "Not graded" : percent(school.calculateFinalGrade(enrollment.studentId(), enrollment.courseId()));
                enrollments.addRow(new Object[]{student, course.getTitle(), value});
                results.addRow(new Object[]{student, course.getTitle(), course.getGradingMode(), count, value});
            }
            counts[0].setText(Long.toString(school.getUsers().stream().filter(u -> u.getRole() == Role.STUDENT).count()));
            counts[1].setText(Long.toString(school.getUsers().stream().filter(u -> u.getRole() == Role.TEACHER).count()));
            counts[2].setText(Integer.toString(school.getCourses().size()));
            counts[3].setText(Integer.toString(school.getGrades().size()));
            boolean empty = school.getUsers().isEmpty() && school.getCourses().isEmpty();
            demo.setEnabled(empty);
            schoolSummary.setText(empty ? "An empty school. Load demo data for a quick walkthrough." :
                    school.getEnrollments().size() + " enrollments  |  Manage profiles, track progress and prepare student reports");
            replaceChoices(reportStudent, userPicks(Role.STUDENT));
            replaceChoices(recipient, userPicks(null));
            List<EnrollmentPick> pairs = school.getEnrollments().stream().map(e -> new EnrollmentPick(e.studentId(),
                    e.courseId(), userLabel(school.findUser(e.studentId())) + "  /  " + school.findCourse(e.courseId()).getTitle())).toList();
            replaceChoices(assessmentPair, pairs);
            activity.setText(school.getActivityLog().isEmpty() ? "No school activity yet." : String.join("\n\n", school.getActivityLog()));
            activity.setCaretPosition(0);
        } finally { refreshing = false; }
        refreshAssessments(); refreshReport(); refreshInbox();
    }

    private void refreshAssessments() {
        grades.setRowCount(0); attendance.setRowCount(0);
        EnrollmentPick pair = (EnrollmentPick) assessmentPair.getSelectedItem();
        assessmentPair.setToolTipText(pair == null ? "Choose an enrollment." : pair.toString());
        if (pair == null) { gradeSummary.setText("Enroll a student to record assessments or attendance."); return; }
        List<Grade> entries = school.getStudentGrades(pair.studentId(), pair.courseId());
        for (Grade grade : entries) grades.addRow(new Object[]{grade.category(), grade.earned(), grade.possible(), grade.comment()});
        school.getAttendance().stream().filter(a -> a.studentId().equals(pair.studentId()) && a.courseId().equals(pair.courseId()))
                .sorted(java.util.Comparator.comparing(Attendance::date))
                .forEach(a -> attendance.addRow(new Object[]{a.date().toString(), a.present() ? "Present" : "Absent"}));
        Course course = school.findCourse(pair.courseId());
        String result = entries.isEmpty() ? "Not graded" : percent(school.calculateFinalGrade(pair.studentId(), pair.courseId()));
        gradeSummary.setText("Final result: " + result + "  |  " + course.getGradingMode()
                + (course.getGradingMode() == GradingMode.WEIGHTED ? "  |  " + course.getWeights() : "  |  Total earned / total possible"));
        gradeSummary.setToolTipText(course.getGradingMode() == GradingMode.WEIGHTED ? "Unassessed categories contribute zero." : null);
    }

    private void refreshReport() {
        Pick student = (Pick) reportStudent.getSelectedItem();
        report.setText(student == null ? "Add a student to generate a report." :
                school.generateReport(student.id(), includeAttendance.isSelected(), includeComments.isSelected()));
        report.setCaretPosition(0);
    }

    private void refreshInbox() {
        Pick user = (Pick) recipient.getSelectedItem();
        if (user == null) inbox.setText("Add a user to view notifications.");
        else {
            List<String> messages = school.getNotifications(user.id()).stream().map(n -> n.message()).toList();
            inbox.setText(messages.isEmpty() ? "No notifications for this user." : String.join("\n\n", messages));
        }
        inbox.setCaretPosition(0);
    }

    private void confirm(String title, JPanel fields, Runnable change) {
        while (JOptionPane.showConfirmDialog(this, fields, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                == JOptionPane.OK_OPTION) {
            try { change.run(); }
            catch (IllegalArgumentException error) { showError(error); continue; }
            changed();
            return;
        }
    }

    private void mutate(Runnable change) {
        try { change.run(); }
        catch (IllegalArgumentException error) { showError(error); return; }
        changed();
    }

    private void changed() {
        try { onMutation.run(); }
        finally { refresh(); }
    }

    private void showError(RuntimeException error) {
        JOptionPane.showMessageDialog(this, error.getMessage(), "Check your input", JOptionPane.ERROR_MESSAGE);
    }

    private void information(String text) {
        JOptionPane.showMessageDialog(this, text, "School Management", JOptionPane.INFORMATION_MESSAGE);
    }

    private List<Pick> userPicks(Role role) {
        return school.getUsers().stream().filter(u -> role == null || u.getRole() == role)
                .map(u -> new Pick(u.getId(), userLabel(u))).toList();
    }

    private List<Pick> coursePicks() {
        return school.getCourses().stream().map(c -> new Pick(c.getId(), c.getId() + " - " + c.getTitle())).toList();
    }

    private static String userLabel(User user) { return user.getId() + " - " + user.getName(); }
    private static String percent(double value) { return String.format(Locale.ROOT, "%.2f%%", value); }
    private static JTextField field(String value) { return new JTextField(value, 25); }
    private static <T> T selected(JComboBox<T> combo, String label) {
        @SuppressWarnings("unchecked") T item = (T) combo.getSelectedItem();
        if (item == null) throw new IllegalArgumentException("Choose a " + label + ".");
        return item;
    }
    private static <T> void replaceChoices(JComboBox<T> combo, List<T> values) {
        Object selected = combo.getSelectedItem();
        combo.removeAllItems();
        values.forEach(combo::addItem);
        if (values.contains(selected)) combo.setSelectedItem(selected);
        combo.setEnabled(!values.isEmpty());
    }
    private static int integer(String text, String label) {
        try { return Integer.parseInt(text.strip()); }
        catch (NumberFormatException error) { throw new IllegalArgumentException(label + " must be a whole number."); }
    }
    private static double number(String text, String label) {
        try { return Double.parseDouble(text.strip()); }
        catch (NumberFormatException error) { throw new IllegalArgumentException(label + " must be a number, using a dot for decimals."); }
    }
    private static Map<String, Double> parseWeights(String text) {
        Map<String, Double> weights = new LinkedHashMap<>();
        for (String part : text.split(",", -1)) {
            String[] pair = part.split("=", -1);
            if (pair.length != 2 || pair[0].isBlank()) throw new IllegalArgumentException("Use category=weight pairs, e.g. homework=40, exam=60.");
            String category = pair[0].strip().toLowerCase(Locale.ROOT);
            if (weights.putIfAbsent(category, number(pair[1], "Category weight")) != null)
                throw new IllegalArgumentException("Duplicate category: " + category);
        }
        return weights;
    }
    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int column) {
                return getRowCount() == 0 || getValueAt(0, column) == null ? Object.class : getValueAt(0, column).getClass();
            }
        };
    }
    private static JScrollPane table(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(31);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(228, 235, 239));
        table.setIntercellSpacing(new Dimension(8, 1));
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD));
        for (int i = 0; i < table.getColumnCount(); i++) {
            String heading = model.getColumnName(i);
            var column = table.getColumnModel().getColumn(i);
            int preferred = switch (heading) {
                case "ID" -> 60;
                case "Course", "Email", "Teacher comment" -> 260;
                case "Student", "Teacher", "Name" -> 185;
                case "Schedule" -> 150;
                default -> 105;
            };
            column.setMinWidth(heading.equals("ID") ? 45 : 75);
            column.setPreferredWidth(preferred);
        }
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setPreferredSize(new Dimension(800, 250));
        return scroll;
    }
    private static JTextArea textArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        area.setMargin(new Insets(16, 18, 16, 18));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }
    private static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setMargin(new Insets(8, 14, 8, 14));
        button.addActionListener(event -> action.run());
        return button;
    }
    private static JLabel note(String text) { JLabel label = new JLabel(text); label.setForeground(MUTED); return label; }
    private static JPanel row(JComponent... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        row.setOpaque(false);
        for (JComponent component : components) row.add(component);
        return row;
    }
    private static JPanel page() {
        JPanel page = new JPanel(new BorderLayout(0, 16));
        page.setBackground(BACKGROUND);
        page.setBorder(BorderFactory.createEmptyBorder(18, 16, 16, 16));
        return page;
    }
    private static JPanel section(String heading, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout(0, 9));
        panel.setOpaque(false);
        JLabel title = new JLabel(heading);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setForeground(INK);
        panel.add(title, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        panel.setMinimumSize(new Dimension(180, 100));
        return panel;
    }
    private static JPanel form(Object... rows) {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(7, 6, 7, 6);
        constraints.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < rows.length; i += 2) {
            constraints.gridy = i / 2; constraints.gridx = 0; constraints.weightx = 0; constraints.fill = GridBagConstraints.NONE;
            JLabel label = new JLabel(rows[i].toString());
            label.setLabelFor((JComponent) rows[i + 1]);
            form.add(label, constraints);
            constraints.gridx = 1; constraints.weightx = 1; constraints.fill = GridBagConstraints.HORIZONTAL;
            form.add((JComponent) rows[i + 1], constraints);
        }
        return form;
    }
    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("SchoolPanel must be accessed on the Swing event thread.");
    }
    private record Pick(String id, String label) { @Override public String toString() { return label; } }
    private record EnrollmentPick(String studentId, String courseId, String label) { @Override public String toString() { return label; } }
}
