package com.zergatstage.monitor.theme;

import java.util.prefs.Preferences;
import lombok.extern.log4j.Log4j2;

/**
 * Manages the persisted UI scale (font/DPI scaling via FlatLaf's "flatlaf.uiScale" system
 * property). FlatLaf only reads that property once, before its first UI is built, so a change
 * here takes effect on the next application launch rather than live.
 */
@Log4j2
public class ScaleManager {
    private static final String PREF_KEY = "uiScalePercent";
    private static final int DEFAULT_SCALE_PERCENT = 100;
    public static final int[] SUPPORTED_SCALE_PERCENTS = {100, 125, 150, 175, 200, 250};

    private static final ScaleManager INSTANCE = new ScaleManager();

    private final Preferences preferences;
    private int currentScalePercent;

    private ScaleManager() {
        this.preferences = Preferences.userNodeForPackage(ScaleManager.class);
        this.currentScalePercent = loadStoredScalePercent();
    }

    public static ScaleManager getInstance() {
        return INSTANCE;
    }

    /**
     * Applies the persisted UI scale as a system property. Must be called before any
     * Swing/FlatLaf class is touched, i.e. as the very first thing in main().
     */
    public void applyStartupScale() {
        if (currentScalePercent != 100) {
            String factor = currentScalePercent / 100.0 + "";
            System.setProperty("flatlaf.uiScale", factor);
            log.info("Applying UI scale {}% via flatlaf.uiScale={}", currentScalePercent, factor);
        }
    }

    public int getCurrentScalePercent() {
        return currentScalePercent;
    }

    /**
     * Persists the chosen scale for the next launch. Does not change the running UI.
     */
    public void setScalePercentForNextLaunch(int scalePercent) {
        this.currentScalePercent = scalePercent;
        try {
            preferences.putInt(PREF_KEY, scalePercent);
        } catch (IllegalStateException ex) {
            log.debug("Unable to persist UI scale preference; continuing without persistence.", ex);
        }
    }

    private int loadStoredScalePercent() {
        try {
            return preferences.getInt(PREF_KEY, DEFAULT_SCALE_PERCENT);
        } catch (IllegalStateException ex) {
            return DEFAULT_SCALE_PERCENT;
        }
    }
}
