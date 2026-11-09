package school.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;

/** One bundled light theme for both native desktop launches and component previews. */
public final class DesktopTheme {
    public static final Color INK = new Color(26, 48, 67);
    public static final Color TEAL = new Color(0, 111, 119);
    public static final Color MUTED = new Color(82, 101, 116);
    public static final Color BACKGROUND = new Color(242, 246, 249);
    public static final Color SURFACE = Color.WHITE;

    private DesktopTheme() { }

    /** Configure before creating any Swing component; never inherit the OS GTK palette. */
    public static void install() throws UnsupportedLookAndFeelException {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Install the desktop theme on the Swing event thread.");
        }
        UIManager.put("control", BACKGROUND);
        UIManager.put("info", SURFACE);
        UIManager.put("text", INK);
        UIManager.put("nimbusBase", new Color(48, 91, 107));
        UIManager.put("nimbusBlueGrey", new Color(219, 229, 235));
        UIManager.put("nimbusLightBackground", SURFACE);
        UIManager.put("nimbusSelectionBackground", TEAL);
        UIManager.put("nimbusSelectedText", SURFACE);
        UIManager.put("nimbusDisabledText", MUTED);
        UIManager.put("nimbusFocus", TEAL);
        UIManager.put("defaultFont", new FontUIResource(Font.SANS_SERIF, Font.PLAIN, 13));
        UIManager.setLookAndFeel(new NimbusLookAndFeel());
    }
}
