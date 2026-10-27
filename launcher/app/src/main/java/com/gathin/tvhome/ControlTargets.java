package com.gathin.tvhome;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * control_center_targets (config.xml or a theme): "key=package/activity" lines that send a Control Center tile or
 * row to a vendor's own settings page instead of the standard one, e.g.
 * {@code adb=com.xiaomi.mitv.settings/com.xiaomi.mitv.settings.entry.MainActivity}. Pure Java so JUnit can test it;
 * QuickSettingsActivity turns the values into components and only uses one whose package is installed.
 */
final class ControlTargets {
    static final String INPUTS = "inputs", NETWORK = "network", PICTURE = "picture", SOUND = "sound",
            BLUETOOTH = "bluetooth", APPS = "apps", ADB = "adb", LAUNCHER = "launcher", SYSTEM = "system",
            ANDROID = "android", POWER = "power";

    private ControlTargets() {
    }

    /**
     * key -> "package/activity", keys lower-cased and both sides trimmed. A line without "=", with an empty key, or
     * whose value is not package/activity is left out; for a key listed twice the last line wins. Unknown keys are
     * kept (nothing asks for them).
     */
    static Map<String, String> parse(List<String> lines) {
        Map<String, String> targets = new HashMap<>();
        if (lines == null) return targets;
        for (String line : lines) {
            if (line == null) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String component = line.substring(eq + 1).trim();
            if (key.isEmpty() || !isComponent(component)) continue;
            targets.put(key, component);
        }
        return targets;
    }

    /** What ComponentName.unflattenFromString accepts: "package/class", both parts non-empty, no spaces. */
    static boolean isComponent(String value) {
        int slash = value.indexOf('/');
        return slash > 0 && slash < value.length() - 1 && value.indexOf('/', slash + 1) < 0
                && value.indexOf(' ') < 0;
    }
}
