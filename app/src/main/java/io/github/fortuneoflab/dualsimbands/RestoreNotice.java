package io.github.fortuneoflab.dualsimbands;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

/**
 * Wording of the auto-restore notification. On the Pixel 10a the written name only appears after a
 * radio state change (e.g. airplane mode off/on), so "restored" must not imply the status bar changed.
 */
final class RestoreNotice {
    private RestoreNotice() {}

    /** @return {title, text} */
    static String[] text(boolean success, boolean displayPending) {
        if (!success) return new String[]{t("表示名を戻せませんでした", "Could not restore carrier names"), t("タップで結果を確認できます。", "Tap to see the result.")};
        if (displayPending) return new String[]{t("表示名の設定を戻しました", "Carrier name settings restored"),
                t("表示が変わらないときは、機内モードをオン・オフしてください。タップで結果を確認できます。", "If the display does not change, turn airplane mode on and off. Tap to see the result.")};
        return new String[]{t("表示名を戻しました", "Carrier names restored"), t("タップで結果を確認できます。", "Tap to see the result.")};
    }
}
