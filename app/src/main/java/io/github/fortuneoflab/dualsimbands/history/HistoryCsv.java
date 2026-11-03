package io.github.fortuneoflab.dualsimbands.history;

/** RFC 4180 CSV with a UTF-8 BOM so Excel (Japanese locale) opens it without mojibake. */
public final class HistoryCsv {
    public static final String BOM = String.valueOf((char) 0xFEFF);
    public static final String UNKNOWN = "unknown";
    public static final String UNSUPPORTED = "unsupported";
    public static final String NOT_RECORDED = "not_recorded";

    private HistoryCsv() {}

    public static String header() { return line(HistorySample.COLUMNS); }

    public static String line(String[] values) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) b.append(',');
            b.append(escape(values[i]));
        }
        return b.toString();
    }

    static String escape(String v) {
        if (v == null || v.isEmpty()) return UNKNOWN;
        boolean quote = v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        return quote ? '"' + v.replace("\"", "\"\"") + '"' : v;
    }
}
