package com.zergatstage.monitor.theme;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;

/**
 * Windows-specific helper that reads the OS theme preference from the registry on Windows 11.
 * The registry key mirrors the one used by Windows for app theme (light/dark).
 * Fails safely on non-Windows systems or when registry access is blocked.
 */
@Log4j2
public class WindowsThemeDetector {
    private static final String PERSONALIZE_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";
    private static final String APPS_USE_LIGHT_VALUE = "AppsUseLightTheme";
    private static final String CURRENT_VERSION_KEY = "HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion";
    private static final String CURRENT_BUILD_VALUE = "CurrentBuildNumber";
    private static final int WINDOWS_11_MIN_BUILD = 22000; // First public Windows 11 build

    public Optional<AppTheme> detectPreferredTheme() {
        if (!isWindows()) {
            return Optional.empty();
        }
        if (!isWindows11OrLater()) {
            log.debug("OS is Windows but build is below Windows 11 threshold; skipping system theme detection.");
            return Optional.empty();
        }

        try {
            Process process = new ProcessBuilder("reg", "query", PERSONALIZE_KEY, "/v", APPS_USE_LIGHT_VALUE)
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(APPS_USE_LIGHT_VALUE)) {
                        boolean light = line.contains("0x1") || line.contains("0x00000001");
                        return Optional.of(light ? AppTheme.LIGHT : AppTheme.DARK);
                    }
                }
            }
            int exit = process.waitFor();
            if (exit != 0) {
                log.debug("Registry query exited with code {}", exit);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.info("Windows theme detection interrupted; defaulting to application theme.", ex);
        } catch (IOException | RuntimeException ex) {
            log.info("Windows theme detection encountered an error; defaulting to application theme.", ex);
        }
        return Optional.empty();
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    private boolean isWindows11OrLater() {
        if (!isWindows()) {
            return false;
        }
        Integer build = readCurrentBuildNumber();
        if (build != null) {
            return build >= WINDOWS_11_MIN_BUILD;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("11"); // Best effort fallback when build not available
    }

    private Integer readCurrentBuildNumber() {
        try {
            Process process = new ProcessBuilder("reg", "query", CURRENT_VERSION_KEY, "/v", CURRENT_BUILD_VALUE)
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(CURRENT_BUILD_VALUE)) {
                        String[] tokens = line.trim().split("\\s+");
                        String maybeNumber = tokens[tokens.length - 1];
                        try {
                            return Integer.parseInt(maybeNumber);
                        } catch (NumberFormatException ignored) {
                            log.debug("Could not parse build number from token '{}'", maybeNumber);
                        }
                    }
                }
            }
            process.waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.debug("Build number detection interrupted; proceeding without it.", ex);
        } catch (IOException ex) {
            log.debug("Build number detection failed; proceeding without it.", ex);
        }
        return null;
    }
}
