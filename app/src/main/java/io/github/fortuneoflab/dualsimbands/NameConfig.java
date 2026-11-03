package io.github.fortuneoflab.dualsimbands;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

import java.util.LinkedHashMap;
import java.util.Map;

/** Both states are explicit because CarrierConfig merges successive overrides. */
final class NameConfig {
    static final String NAME = "carrier_name_string";
    static final String OVERRIDE = "carrier_name_override_bool";
    static final String RESOLVER = "enable_carrier_display_name_resolver_bool";
    static final String CONDITION = "spn_display_condition_override_int";
    static final String[] KEYS = {NAME, OVERRIDE, RESOLVER, CONDITION};

    private NameConfig() {}

    /** Trimmed custom label; 1–40 characters. */
    static String label(String input) {
        String label = input == null ? "" : input.trim();
        if (label.isEmpty() || label.length() > 40)
            throw new IllegalArgumentException(t("表示名は1〜40文字にしてください", "Use a name of 1 to 40 characters"));
        return label;
    }

    /** When a baseline was recorded, relative to the current boot. */
    enum Boot {
        /** This boot: it holds the values from before this app's first change since the reboot. */
        THIS,
        /**
         * An earlier boot. The reboot cleared every override, and a baseline is always saved before this app
         * writes, so this app has not changed the SIM since: the current values are the pre-change values.
         */
        EARLIER,
        /** Cannot be told (boot count unavailable or going back): neither re-recorded nor written back. */
        UNKNOWN
    }

    /**
     * Overrides are not persistent, so a baseline is valid only in the boot it was recorded in. One from an
     * earlier boot may hold another tool's or this app's old override (e.g. "au" instead of "KDDI").
     * A baseline found UNKNOWN once is marked with {@code seenBoot} and stays UNKNOWN until a later boot, so an
     * uptime that grows past the recorded one cannot turn it into THIS. A reused boot count is caught only while
     * the uptime is still below the recorded one; the boot count alone cannot prove the boot.
     * @param recordedBoot the boot count when recorded, or -1 (before boots were tracked, or unavailable then)
     * @param recordedElapsed elapsedRealtime when recorded, or -1 if not recorded (baselines of 2026-10-22)
     * @param seenBoot the boot count when the baseline was first found UNKNOWN, or -1
     */
    static Boot boot(int recordedBoot, long recordedElapsed, int seenBoot, int currentBoot, long currentElapsed) {
        if (currentBoot < 0) return Boot.UNKNOWN;
        // Only a reboot after it was found UNKNOWN clears any override made in that boot.
        if (seenBoot >= 0) return currentBoot > seenBoot ? Boot.EARLIER : Boot.UNKNOWN;
        if (recordedBoot < 0 || recordedBoot > currentBoot) return Boot.UNKNOWN;
        if (recordedBoot < currentBoot) return Boot.EARLIER;
        // The same count with a later uptime in the record: the count was reused, not this boot.
        return recordedElapsed > currentElapsed ? Boot.UNKNOWN : Boot.THIS;
    }

    /**
     * Whether an UNKNOWN baseline gets {@code seenBoot = currentBoot}: when not marked yet, or when the boot count
     * went below the mark, so that the next reboot (not reaching the old mark) makes it usable again.
     */
    static boolean mark(Boot recorded, int seenBoot, int currentBoot) {
        return recorded == Boot.UNKNOWN && currentBoot >= 0 && (seenBoot < 0 || currentBoot < seenBoot);
    }

    /**
     * @param baseline values captured before this app first changed the SIM in this boot; used when {@code custom} is false
     */
    static Map<String, Object> values(boolean custom, String input, Map<String, Object> baseline) {
        return custom ? custom(input) : baseline(baseline);
    }

    static Map<String, Object> custom(String input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(NAME, label(input));
        result.put(OVERRIDE, true);
        result.put(RESOLVER, true);
        result.put(CONDITION, 2);
        return result;
    }

    /**
     * Name override off: write back the values from before this app's first change (decision of 2026-10-19).
     * Writing "" / false is not the carrier default, and clearing every override with
     * overrideConfig(null) would also remove other tools' settings (e.g. Pixel IMS), so neither is used.
     */
    static Map<String, Object> baseline(Map<String, Object> saved) {
        if (saved == null) throw new IllegalStateException(t("このSIMの変更前の値が記録されていません", "No original values are recorded for this SIM"));
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : KEYS) {
            if (!saved.containsKey(key)) throw new IllegalStateException(t("変更前の値が記録されていません: ", "Original value not recorded: ") + key);
            Object v = saved.get(key);
            boolean typed = key.equals(NAME) ? v == null || v instanceof String
                    : key.equals(CONDITION) ? v instanceof Integer : v instanceof Boolean;
            if (!typed) throw new IllegalStateException(t("変更前の値の形式が不正です: ", "Original value has an invalid type: ") + key);
            result.put(key, v);
        }
        return result;
    }
}
