package com.zergatstage.monitor.config;

import java.awt.*;

/**
 * The DisplayConfig class contains configuration constants for display thresholds and colors.
 */
public class DisplayConfig {

    // Tritium threshold constants
    public static final double TRITIUM_THRESHOLD_LOW = 10.0;
    public static final double TRITIUM_THRESHOLD_MEDIUM = 15.1;
    public static final double TRITIUM_THRESHOLD_HIGH = 25.0;

    // Color constants
    public static final Color COLOR_DEFAULT = new Color(238, 238, 238); // Light gray
    public static final Color COLOR_LOW = Color.GRAY;
    public static final Color COLOR_MEDIUM = Color.YELLOW;
    public static final Color COLOR_HIGH = Color.ORANGE;
    public static final Color COLOR_VERY_HIGH = new Color(147, 112, 219); // Medium purple
}
