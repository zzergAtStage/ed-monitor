### T-201: Implement Dark/Light Theme System for ed-monitor Swing UI

**Description**:  
Design and implement a dark/light theming system for the existing `ed-monitor` Java Swing desktop application. The UI should use a flat, modern style similar to IntelliJ IDEA’s light and dark themes, with support for Windows 11 system theme detection on startup and an in-app Settings toggle to switch themes at runtime.

**Acceptance Criteria**:
- [ ] The application exposes at least two distinct visual themes at runtime: a light theme and a dark theme, applied consistently across all main windows, dialogs, menus, buttons, tables, trees, and text components.
- [ ] A centralized theme management layer exists (e.g. a dedicated “theme manager” + simple `AppTheme` representation) that:
  - Tracks the current theme.
  - Can initialize the theme during application startup.
  - Can change the theme at runtime.
  - Can report the currently active theme to other UI code.
- [ ] Changing the theme at runtime triggers a full UI refresh on all currently open frames, dialogs, and main panels so that components visually update immediately without requiring an application restart.
- [ ] On Windows 11:
  - The application detects the OS theme (dark vs. light) at startup using a suitable mechanism (e.g. registry or system APIs).
  - If no user preference is stored, the default theme on startup matches the Windows 11 system theme.
  - If detection fails or the OS is not recognized as Windows 11, the application falls back to the light theme without crashing or logging severe errors.
- [ ] A Settings menu entry (e.g. under “Settings”, “Preferences”, or similar) provides a user-facing theme toggle or selector that:
  - Reflects the currently active theme when the menu is opened.
  - Allows the user to switch between dark and light themes.
  - Invokes the shared theme manager to perform the switch, and the UI updates immediately.
- [ ] Theme choice is persisted locally (e.g. via Java preferences or a small config file) so that:
  - On startup, if a stored user preference exists, it overrides system theme detection.
  - On startup, if no stored preference exists, the theme is derived from Windows 11 system theme when available; otherwise it defaults to light.
- [ ] The visual style is flat and modern, broadly aligned with IntelliJ IDEA’s default and Darcula-like look:
  - Minimal 3D effects and gradients; primarily flat component surfaces.
  - Consistent background/foreground/selection colors across controls.
  - Reasonable, consistent typography (font family and sizes) across menus, buttons, and content components.
  - Harmonized padding and margins for menus, toolbars, dialogs, and form controls.
- [ ] The code that detects Windows 11 theme is isolated in a dedicated helper (e.g. a Windows-specific theme detector) so that:
  - It can be disabled or bypassed when running on non-Windows systems.
  - It fails safely (no crashes) when system APIs/registry keys are not present.
- [ ] Theme-related logic is not coupled to business logic:
  - The main theme manager and any OS-specific helpers live in dedicated UI/utility classes.
  - Business logic classes remain unaware of theme details.
- [ ] The implementation is reasonably documented with short comments or Javadoc explaining:
  - Where and how the initial theme is selected.
  - How to add additional themes in the future (e.g. high-contrast theme).
  - Any OS-specific assumptions (especially for Windows 11 detection).

**Scope**:
- Files involved:
  - Main Swing application entry point and main window / frame classes (search for the class that creates and shows the primary UI).
  - Menu bar and Settings/Preferences menu code (search for “Settings”, “Preferences”, or similar).
  - Any existing Look & Feel or UI utility classes (search for `UIManager`, `LookAndFeel`, or LAF-related helpers).
  - New theme-management and Windows-theme-detection classes as needed.
- Modules:
  - `swing` / desktop client module only (no changes required to server, Spring Boot, JPA, or H2 logic).
- Estimated complexity: Medium–Complex (cross-cutting UI change with OS-specific behavior and persistence).

**Constraints & Assumptions**:
- The coding agent can read and modify repository files directly; do not require manual code pasting in the prompt or placeholders.
- Favor a modern, flat Swing Look & Feel (IntelliJ-like) using:
  - Either a well-known flat LAF library already present in the project, or
  - A new dependency chosen to be compatible with Swing and cross-platform. If a new dependency is introduced, keep configuration minimal and document the choice.
- The solution must remain cross-platform:
  - Windows 11 theme detection is optional behavior that enhances startup defaults, but the application must still run correctly on non-Windows systems.
  - Any Windows-specific logic must be guarded by OS checks and fail safely.
- Do not introduce heavy refactoring of business logic; keep changes localized to UI, configuration, and theming utilities.
- Maintain existing application behavior and workflows; only visual appearance and theme switching should change.

**Priority**: P1 (high)

**Notes**:
- **Role & mindset for the coding agent**:
  - Act as a senior Java desktop engineer experienced with Swing Look & Feel customization and cross-platform UI.
  - Before making changes, scan the codebase to identify:
    - The main app entry point (where the initial Look & Feel and main frame are configured).
    - Existing menu bar / Settings or Preferences implementation.
    - Any existing UI utility or LAF-related classes that should be extended rather than duplicated.
  - Implement the theming system in a modular way so that adding future themes (e.g. high-contrast) is straightforward.
- **Startup behavior (intended)**:
  1. On application start, load any stored user theme preference.
  2. If none exists and the OS is Windows 11, derive a default theme from the system’s dark/light setting.
  3. If detection is unavailable or fails, use the light theme.
  4. Initialize the global Look & Feel and theme-related UI defaults accordingly, then show the main UI.
- **Runtime switching behavior (intended)**:
  - When the user toggles the theme from the Settings menu:
    - Update the centralized theme manager.
    - Apply the new theme and refresh all open windows/dialogs.
    - Persist the new choice so the next startup uses it by default.
- **IntelliJ-style look reference (conceptual)**:
  - Flat, clean surfaces with subtle separators.
  - Clear, readable font and spacing.
  - High-contrast dark theme with carefully chosen accent and selection colors.
  - Calm, uncluttered light theme with consistent use of grays and neutral tones.
