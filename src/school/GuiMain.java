package school;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import school.application.DemoData;
import school.ui.DesktopSession;
import school.ui.SchoolPanel;

/** Desktop entry point. The Swing view uses the same facade and snapshot as the console. */
public final class GuiMain {
    private final DesktopSession session;
    private final JFrame window = new JFrame("School Management System");
    private final JLabel status = new JLabel();

    private GuiMain(DesktopSession session) {
        this.session = session;
    }

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println("Usage: java Run.java gui [--memory | --demo | --data FILE]");
            System.out.println("Default: desktop window with autosave to data/school.properties.");
            System.out.println("--demo: desktop with fictional sample data, without changing saved data.");
            System.out.println("--memory: empty desktop session without persistence.");
            return;
        }
        Path file;
        boolean demo = args.length == 1 && args[0].equals("--demo");
        if (args.length == 0) file = Path.of("data", "school.properties");
        else if (args.length == 1 && (args[0].equals("--memory") || demo)) file = null;
        else if (args.length == 2 && args[0].equals("--data")) file = Path.of(args[1]);
        else {
            System.err.println("Unknown desktop arguments. Run java Run.java gui --help.");
            System.exit(2);
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("The desktop interface needs a graphical display. Use java Run.java for the console.");
            System.exit(2);
            return;
        }
        try {
            DesktopSession session = DesktopSession.open(file);
            if (demo) DemoData.populate(session.school());
            SwingUtilities.invokeLater(() -> {
                try {
                    try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
                    catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ignored) {
                        // The JDK's default Swing look and feel is a usable fallback.
                    }
                    new GuiMain(session).show();
                } catch (Exception error) {
                    try { session.close(); } catch (IOException closeError) { error.addSuppressed(closeError); }
                    JOptionPane.showMessageDialog(null, error.getMessage(), "Cannot open school", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }
            });
        } catch (IOException error) {
            System.err.println("Cannot open school data: " + error.getMessage());
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, error.getMessage(), "Cannot open school data", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            });
        }
    }

    private void show() {
        window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        window.add(new SchoolPanel(session.school(), this::changed), BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBorder(BorderFactory.createEmptyBorder(10, 18, 12, 18));
        footer.add(status, BorderLayout.CENTER);
        JPanel actions = new JPanel();
        JButton save = new JButton("Save now");
        save.setEnabled(session.file() != null);
        save.addActionListener(event -> save());
        JButton backup = new JButton("Save backup copy");
        backup.addActionListener(event -> backup());
        actions.add(save);
        actions.add(backup);
        footer.add(actions, BorderLayout.EAST);
        window.add(footer, BorderLayout.SOUTH);
        window.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { close(); }
        });
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        window.setSize(Math.min(1180, screen.width), Math.min(800, screen.height));
        window.setMinimumSize(new Dimension(Math.min(900, screen.width), Math.min(600, screen.height)));
        window.setLocationRelativeTo(null);
        updateStatus();
        window.setVisible(true);
    }

    private void changed() {
        try {
            session.changed();
        } catch (IOException error) {
            showSaveError(error);
        }
        updateStatus();
    }

    private boolean save() {
        try {
            session.save();
            updateStatus();
            return true;
        } catch (IOException error) {
            showSaveError(error);
            updateStatus();
            return false;
        }
    }

    private boolean backup() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save school backup");
        chooser.setFileFilter(new FileNameExtensionFilter("School snapshot (*.properties)", "properties"));
        chooser.setSelectedFile(new java.io.File("school-backup.properties"));
        if (chooser.showSaveDialog(window) != JFileChooser.APPROVE_OPTION) return false;
        Path copy = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        if (Files.exists(copy) && JOptionPane.showConfirmDialog(window,
                "Replace the existing file?\n" + copy, "Replace backup", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return false;
        try {
            session.saveCopy(copy);
            updateStatus();
            JOptionPane.showMessageDialog(window, "Backup saved to:\n" + copy
                    + "\n\nOpen it later with: java Run.java gui --data \"" + copy + "\"",
                    "Backup saved", JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (IOException error) {
            JOptionPane.showMessageDialog(window, error.getMessage(), "Backup failed", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void close() {
        if (session.isDirty() && session.file() != null) save();
        if (session.isDirty()) {
            String[] options = {"Save backup and close", "Close without saving", "Cancel"};
            int answer = JOptionPane.showOptionDialog(window,
                    "This session has changes that are not saved. Choose how to close it.",
                    "Unsaved changes", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[2]);
            if (answer == 0) { if (!backup()) return; }
            else if (answer != 1) return;
        }
        try {
            session.close();
        } catch (IOException error) {
            JOptionPane.showMessageDialog(window, error.getMessage(), "Cannot release data lock", JOptionPane.ERROR_MESSAGE);
        } finally {
            window.dispose();
        }
    }

    private void showSaveError(IOException error) {
        JOptionPane.showMessageDialog(window, "Your change is still available in this window.\nSaving failed: "
                + error.getMessage() + "\nUse Save now to retry, or Save backup copy.",
                "Changes are not saved", JOptionPane.ERROR_MESSAGE);
    }

    private void updateStatus() {
        status.setForeground(session.isDirty() ? new Color(151, 76, 15) : new Color(25, 106, 92));
        String label = session.file() == null ? "Memory mode - use a backup to keep your data"
                : (session.isDirty() ? "Unsaved changes - " : "Autosave - ") + session.file();
        status.setText(label);
        status.setToolTipText(label);
    }
}
