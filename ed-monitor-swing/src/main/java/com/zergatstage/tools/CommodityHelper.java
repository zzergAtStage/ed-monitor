package com.zergatstage.tools;

public class CommodityHelper {
    /**
     * Normalizes a raw commodity name from any event type to a consistent,
     * lowercase system name.
     * e.g., "$LiquidOxygen_name;" -> "liquidoxygen"
     * e.g., "superconductors" -> "superconductors"
     *
     * @param rawName The raw string from the JSON event.
     * @return A clean, consistent identifier.
     */
    public static String normalizeSystemName(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return "unknown"; // Or throw an exception
        }
        String processedName = rawName;
        if (processedName.startsWith("$")) {
            processedName = processedName.substring(1);
        }
        if (processedName.endsWith("_name;")) {
            processedName = processedName.substring(0, processedName.lastIndexOf("_name;"));
        }
        return processedName.toLowerCase();
    }
}
