package io.github.fortuneoflab.dualsimbands;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

/**
 * Compares the carrier name Android reports (SubscriptionInfo.getCarrierName) with what was requested.
 * A config readback proves the write; this separately tells whether the visible name has caught up.
 */
final class LabelVerdict {
    enum Result {
        /** The reported name equals the requested custom label. */
        MATCHES,
        /** Not (yet) the requested label; the platform may refresh it later. */
        DIFFERS,
        /** Name override is off: the name follows the restored pre-change values, no fixed expectation. */
        NO_EXPECTATION,
        /** The SIM or its name could not be read. */
        UNKNOWN
    }

    private LabelVerdict() {}

    static Result evaluate(boolean rename, String expected, CharSequence actual) {
        if (actual == null) return Result.UNKNOWN;
        if (!rename) return Result.NO_EXPECTATION;
        String want = expected == null ? "" : expected.trim();
        return want.equals(actual.toString().trim()) ? Result.MATCHES : Result.DIFFERS;
    }

    static String describe(String sim, Result result, CharSequence actual) {
        String name = actual == null ? "" : actual.toString();
        switch (result) {
            case MATCHES: return sim + t("：「", ": \"") + name + t("」と表示されています", "\" is shown");
            case DIFFERS: return sim + t("：まだ「", ": still \"") + name + t("」のままです。設定は書き込み済みなので、少し待つか、機内モードをオン・オフすると変わります", "\". The setting is written; wait a moment, or turn airplane mode on and off");
            case NO_EXPECTATION: return sim + t("：「", ": \"") + name + t("」と表示されています（名前は指定していません）", "\" is shown (no name specified)");
            default: return sim + t("：表示名を確認できませんでした", ": could not check the carrier name");
        }
    }
}
