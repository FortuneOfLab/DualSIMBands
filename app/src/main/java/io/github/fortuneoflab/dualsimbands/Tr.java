package io.github.fortuneoflab.dualsimbands;

import android.content.Context;
import java.util.Locale;

/**
 * Japanese or English for text built in code (reports, status lines) rather than taken from string resources.
 * Japanese on a Japanese device, English on every other one, matching values-ja / values. Until {@link #init}
 * runs (plain JVM unit tests) the text stays Japanese, so the tests do not depend on the machine's locale.
 */
public final class Tr {
    private static volatile boolean english;

    private Tr() {}

    /**
     * Called by the Application on start and on configuration changes. The per-app language is read from
     * LocaleManager first: a process started at boot by a receiver can carry the system locale in its
     * configuration even when the app's own language differs. Without one, the strings Android picked from the
     * whole language list are followed (R.bool.tr_japanese), not the list's first entry: with an unsupported
     * language first and Japanese after it, the resources are Japanese.
     */
    public static void init(Context c) {
        Locale l = null;
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            android.app.LocaleManager lm = c.getSystemService(android.app.LocaleManager.class);
            android.os.LocaleList app = lm == null ? null : lm.getApplicationLocales();
            if (app != null && !app.isEmpty()) l = app.get(0);
        }
        english = l != null ? !"ja".equals(l.getLanguage()) : !c.getResources().getBoolean(R.bool.tr_japanese);
    }

    public static String t(String japanese, String englishText) { return english ? englishText : japanese; }

    /** The locale of {@link #t}'s text, for dates and numbers written into it. */
    public static java.util.Locale locale() { return english ? java.util.Locale.ENGLISH : java.util.Locale.JAPANESE; }

    public static boolean english() { return english; }
}
