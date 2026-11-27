package org.dreeam.leaf.gui;

import com.mojang.logging.LogUtils;
import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinReg;
import org.dreeam.leaf.config.modules.misc.ServerGui;
import org.slf4j.Logger;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.basic.BasicLabelUI;
import javax.swing.plaf.basic.BasicListUI;
import javax.swing.plaf.basic.BasicPanelUI;
import javax.swing.plaf.basic.BasicScrollPaneUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.plaf.basic.BasicTextPaneUI;
import javax.swing.plaf.basic.BasicToolTipUI;
import javax.swing.plaf.basic.BasicViewportUI;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class GuiTheme {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Color BACKGROUND = new Color(0x202226);
    private static final Color CONTROL = new Color(0x2B2E33);
    private static final Color FOREGROUND = new Color(0xE4E7EC);
    private static final Color BORDER = new Color(0x4B5058);
    private static final Color SELECTION = new Color(0x365778);
    private static final Color LIGHT_GRID = new Color(0x888888);
    private static boolean dark;
    private static boolean customPalette;

    private GuiTheme() {
    }

    public static void install() throws ClassNotFoundException, InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException {
        String theme = ServerGui.theme;
        if (Platform.isMac()) {
            System.setProperty("apple.awt.application.appearance", switch (theme) {
                case "dark" -> "NSAppearanceNameDarkAqua";
                case "light" -> "NSAppearanceNameAqua";
                default -> "system";
            });
        }
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        dark = switch (theme) {
            case "dark" -> true;
            case "light" -> false;
            default -> isSystemDark();
        };
        customPalette = dark || (theme.equals("light") && isLookAndFeelDark());
        if (customPalette) {
            installPalette();
        }
    }

    private static void installPalette() {
        Color background = dark ? BACKGROUND : Color.WHITE;
        Color control = dark ? CONTROL : new Color(0xF0F0F0);
        Color foreground = dark ? FOREGROUND : Color.BLACK;
        Color border = dark ? BORDER : LIGHT_GRID;
        Color selection = dark ? SELECTION : new Color(0x0078D7);
        Color disabled = dark ? new Color(0x9299A3) : LIGHT_GRID;
        Object[] defaults = {
            "PanelUI", BasicPanelUI.class.getName(),
            "LabelUI", BasicLabelUI.class.getName(),
            "ListUI", BasicListUI.class.getName(),
            "TextPaneUI", BasicTextPaneUI.class.getName(),
            "TextFieldUI", BasicTextFieldUI.class.getName(),
            "ScrollPaneUI", BasicScrollPaneUI.class.getName(),
            "ViewportUI", BasicViewportUI.class.getName(),
            "ToolTipUI", BasicToolTipUI.class.getName(),
            "control", control,
            "background", control,
            "text", foreground,
            "controlText", foreground,
            "textText", foreground,
            "textForeground", foreground,
            "textHighlight", selection,
            "textHighlightText", Color.WHITE,
            "textInactiveText", disabled,
            "info", control,
            "infoText", foreground,
            "RootPane.background", control,
            "Panel.background", control,
            "Panel.foreground", foreground,
            "Label.foreground", foreground,
            "Label.disabledForeground", disabled,
            "TextPane.background", background,
            "TextPane.inactiveBackground", background,
            "TextPane.foreground", foreground,
            "TextPane.caretForeground", foreground,
            "TextPane.selectionBackground", selection,
            "TextPane.selectionForeground", Color.WHITE,
            "TextField.background", background,
            "TextField.foreground", foreground,
            "TextField.caretForeground", foreground,
            "TextField.selectionBackground", selection,
            "TextField.selectionForeground", Color.WHITE,
            "TextField.border", new BorderUIResource(BorderFactory.createLineBorder(border)),
            "TextArea.background", background,
            "TextArea.foreground", foreground,
            "List.background", background,
            "List.foreground", foreground,
            "List.selectionBackground", selection,
            "List.selectionForeground", Color.WHITE,
            "Viewport.background", background,
            "ScrollPane.background", control,
            "ScrollBar.background", background,
            "ScrollBar.foreground", foreground,
            "ScrollBar.track", background,
            "ScrollBar.trackHighlight", border,
            "ScrollBar.thumb", dark ? new Color(0x656A73) : new Color(0xB2B2B2),
            "ScrollBar.hoverThumbColor", dark ? new Color(0x858B95) : new Color(0x929292),
            "TitledBorder.titleColor", foreground,
            "ToolTip.background", control,
            "ToolTip.foreground", foreground,
            "ToolTip.border", new BorderUIResource(BorderFactory.createLineBorder(border))
        };
        for (int i = 0; i < defaults.length; i += 2) {
            UIManager.put(defaults[i], defaults[i + 1]);
        }
    }

    public static void styleScrollPane(JScrollPane scrollPane) {
        if (customPalette) {
            scrollPane.getVerticalScrollBar().setUI(new GuiScrollBarUI());
            scrollPane.getHorizontalScrollBar().setUI(new GuiScrollBarUI());
        }
    }

    public static boolean isDark() {
        return dark;
    }

    public static Color graphGridColor() {
        return dark ? BORDER : LIGHT_GRID;
    }

    private static boolean isSystemDark() {
        if (Platform.isWindows()) {
            try {
                return Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "AppsUseLightTheme") == 0;
            } catch (Win32Exception exception) {
                LOGGER.debug("Unable to read the Windows app theme, using the desktop colors", exception);
            }
        } else if (Platform.isMac()) {
            return readDesktopSetting("/usr/bin/defaults", "read", "-g", "AppleInterfaceStyle").equalsIgnoreCase("Dark");
        } else if (Platform.isLinux()) {
            String colorScheme = readDesktopSetting("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
            if (colorScheme.equals("'prefer-dark'")) {
                return true;
            }
            if (colorScheme.equals("'prefer-light'")) {
                return false;
            }
        }
        return isLookAndFeelDark();
    }

    private static boolean isLookAndFeelDark() {
        Color background = UIManager.getColor("Panel.background");
        return background.getRed() * 299 + background.getGreen() * 587 + background.getBlue() * 114 < 128000;
    }

    private static String readDesktopSetting(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try (InputStream output = process.getInputStream()) {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return "";
                }
                return process.exitValue() == 0 ? new String(output.readAllBytes(), StandardCharsets.UTF_8).trim() : "";
            } catch (InterruptedException exception) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                return "";
            }
        } catch (IOException exception) {
            LOGGER.debug("Unable to read the desktop theme", exception);
            return "";
        }
    }

    public static void applyWindowTheme(JFrame frame) {
        if (Platform.isWindows()) {
            Function setAttribute = NativeLibrary.getInstance("dwmapi")
                .getFunction("DwmSetWindowAttribute", Function.ALT_CONVENTION);
            Pointer window = Native.getWindowPointer(frame);
            setWindowAttribute(setAttribute, window, 20, dark ? 1 : 0); // DWMWA_USE_IMMERSIVE_DARK_MODE
            setWindowAttribute(setAttribute, window, 35, dark ? toColorRef(CONTROL) : -1); // DWMWA_CAPTION_COLOR
            setWindowAttribute(setAttribute, window, 36, dark ? toColorRef(FOREGROUND) : -1); // DWMWA_TEXT_COLOR
        }
    }

    private static void setWindowAttribute(Function function, Pointer window, int attribute, int value) {
        int result = function.invokeInt(new Object[]{window, attribute, new int[]{value}, Integer.BYTES});
        if (result < 0) {
            LOGGER.debug("Unable to set GUI window attribute {}: HRESULT 0x{}", attribute, Integer.toHexString(result));
        }
    }

    private static int toColorRef(Color color) {
        return color.getRed() | color.getGreen() << 8 | color.getBlue() << 16;
    }
}
