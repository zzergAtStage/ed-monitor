package com.zergatstage.monitor.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.util.function.Supplier;

/**
 * Supported application themes. Extend by adding additional entries (e.g. high contrast)
 * and wiring them into {@link ThemeManager}.
 */
public enum AppTheme {
    LIGHT("Light", FlatLightLaf::new),
    DARK("Dark", FlatDarkLaf::new);

    private final String displayName;
    private final Supplier<FlatLaf> lafSupplier;

    AppTheme(String displayName, Supplier<FlatLaf> lafSupplier) {
        this.displayName = displayName;
        this.lafSupplier = lafSupplier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public FlatLaf createLookAndFeel() {
        return lafSupplier.get();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
