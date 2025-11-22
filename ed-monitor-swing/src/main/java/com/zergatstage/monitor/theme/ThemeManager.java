package com.zergatstage.monitor.theme;

import com.formdev.flatlaf.FlatLaf;
import java.util.Optional;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import lombok.extern.log4j.Log4j2;

/**
 * Central place to manage application theme (dark/light) and Look & Feel.
 * Responsibilities:
 * - Resolve initial theme (preferences > OS detection > light default).
 * - Apply the chosen FlatLaf variant.
 * - Persist user-selected theme.
 * - Refresh all open Swing windows when a theme change occurs.
 */
@Log4j2
public class ThemeManager {
    private static final String PREF_KEY = "appTheme";
    private static final ThemeManager INSTANCE = new ThemeManager(new WindowsThemeDetector());

    private final WindowsThemeDetector windowsThemeDetector;
    private final Preferences preferences;

    private AppTheme currentTheme = AppTheme.LIGHT;
    private boolean lafApplied = false;

    private ThemeManager(WindowsThemeDetector windowsThemeDetector) {
        this.windowsThemeDetector = windowsThemeDetector;
        this.preferences = Preferences.userNodeForPackage(ThemeManager.class);
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    /**
     * Chooses and applies the startup theme. Should be called before constructing UI.
     */
    public synchronized void initialize() {
        Optional<AppTheme> storedPreference = loadUserPreference();
        Optional<AppTheme> osTheme = storedPreference.isPresent()
                ? Optional.empty()
                : windowsThemeDetector.detectPreferredTheme();

        AppTheme startupTheme = storedPreference
                .or(() -> osTheme)
                .orElse(AppTheme.LIGHT);
        applyThemeInternal(startupTheme, false);
        String source = storedPreference.isPresent() ? "user preference"
                : osTheme.isPresent() ? "Windows 11" : "light default";
        log.info("UI theme initialized to {} (source: {})", currentTheme, source);
    }

    /**
     * Returns the currently active theme.
     */
    public synchronized AppTheme getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Applies a new theme and persists the selection.
     */
    public void applyTheme(AppTheme theme) {
        applyThemeInternal(theme, true);
    }

    private synchronized void applyThemeInternal(AppTheme theme, boolean persistChoice) {
        AppTheme target = (theme != null) ? theme : AppTheme.LIGHT;
        if (target == currentTheme && lafApplied && !persistChoice) {
            return;
        }
        try {
            FlatLaf.setup(target.createLookAndFeel());
            installUiTweaks();
            currentTheme = target;
            lafApplied = true;
            if (persistChoice) {
                storeUserPreference(target);
            }
            refreshAllWindows();
        } catch (RuntimeException ex) {
            lafApplied = false;
            log.warn("Failed to apply theme {}; falling back to light.", target, ex);
            if (target != AppTheme.LIGHT) {
                applyThemeInternal(AppTheme.LIGHT, persistChoice);
            }
        }
    }

    private void installUiTweaks() {
        // Gentle rounding and narrow focus rings to mimic IntelliJ-like flat styling.
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("ScrollBar.showButtons", false);
    }

    private void refreshAllWindows() {
        // FlatLaf updates all top-levels; ensure executed on EDT.
        Runnable task = FlatLaf::updateUI;
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private Optional<AppTheme> loadUserPreference() {
        String value = preferences.get(PREF_KEY, null);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(AppTheme.valueOf(value));
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown stored theme '{}'; ignoring preference.", value);
            return Optional.empty();
        }
    }

    private void storeUserPreference(AppTheme theme) {
        try {
            preferences.put(PREF_KEY, theme.name());
        } catch (IllegalStateException ex) {
            log.debug("Unable to persist theme preference; continuing without persistence.", ex);
        }
    }
}
