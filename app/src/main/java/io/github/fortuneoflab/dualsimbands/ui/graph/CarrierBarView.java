package io.github.fortuneoflab.dualsimbands.ui.graph;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import io.github.fortuneoflab.dualsimbands.R;

/**
 * Carrier composition as one horizontal bar, segment width proportional to bandwidth, with a 2 dp gap
 * between segments. Verified components are coloured by RAT and labelled with their band; bandwidths
 * whose role/RAT is unknown (ServiceState list) are drawn grey and labelled only with MHz.
 */
public final class CarrierBarView extends View {
    public static final class Segment {
        final int bandwidthKhz;
        /** "LTE", "NR" or null when unknown. */
        final String rat;
        /** First label line (band, e.g. "B1"); for role-unknown segments the bandwidth itself. */
        final String label;
        /** Second label line (bandwidth, e.g. "20 MHz"), or null. */
        final String detail;
        /** Bandwidth unknown: drawn as an outline of fixed width after the proportional segments. */
        final boolean placeholder;
        /** Whether to draw the signal icon beside the band; {@link #rsrp} null then means "not reported". */
        boolean showSignal;
        Integer rsrp;
        /** Fill colour instead of the RAT's, e.g. an NR carrier's frequency class; null keeps the RAT's. */
        Integer color;
        public Segment(int bandwidthKhz, String rat, String label, String detail) {
            this(bandwidthKhz, rat, label, detail, false);
        }
        public Segment(int bandwidthKhz, String rat, String label, String detail, boolean placeholder) {
            this.bandwidthKhz = bandwidthKhz; this.rat = rat; this.label = label; this.detail = detail;
            this.placeholder = placeholder;
        }
        /** Adds the four-step signal icon (from RSRP, the same scale as the meters) beside the band label. */
        public Segment withSignal(Integer rsrp) { this.showSignal = true; this.rsrp = rsrp; return this; }
        public Segment withColor(int color) { this.color = color; return this; }
    }

    /** Steps of the icon: 4 at -85 dBm and above, then every 10 dB down to 0 below -115 dBm; -1 when unknown. */
    static int bars(Integer rsrp) {
        if (rsrp == null) return -1;
        if (rsrp >= -85) return 4;
        if (rsrp >= -95) return 3;
        if (rsrp >= -105) return 2;
        if (rsrp >= -115) return 1;
        return 0;
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sub = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private final int lte, nr, unknown;
    private List<Segment> segments = new ArrayList<>();
    /** Drawn above the bar as a bracket spanning every segment; null draws none. */
    private String totalLabel;
    private static final float TOP_DP = 12;

    public CarrierBarView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        lte = ContextCompat.getColor(c, R.color.chart_lte);
        nr = ContextCompat.getColor(c, R.color.chart_nr);
        unknown = ContextCompat.getColor(c, R.color.chart_unknown);
        text.setTextSize(MeterView.sp(c, 12));
        text.setColor(MeterView.themeColor(c, com.google.android.material.R.attr.colorOnSurface));
        text.setFakeBoldText(true);
        sub.setTextSize(MeterView.sp(c, 11));
        sub.setColor(MeterView.themeColor(c, com.google.android.material.R.attr.colorOnSurfaceVariant));
        line.setColor(sub.getColor());
        line.setStrokeWidth(Math.max(1, density));
    }

    /** @param total e.g. "75 MHz" for the bracket over the whole bar, or null for none */
    public void setTotal(String total) {
        boolean resize = (totalLabel == null) != (total == null);
        totalLabel = total;
        if (resize) requestLayout();
        invalidate();
    }

    private float bracketH() { return totalLabel == null ? 0 : text.getTextSize() + 6 * density; }

    public void set(List<Segment> segments) {
        this.segments = segments;
        StringBuilder d = new StringBuilder(t("キャリア構成 ", "Carriers "));
        for (Segment s : segments) d.append(s.label).append(s.detail == null ? "" : " " + s.detail).append('、');
        setContentDescription(d.toString().trim());
        invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        // Top gap separates the bar from the chips above; two label lines below the bar.
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round(TOP_DP * density + bracketH() + 14 * density
                + text.getTextSize() + sub.getTextSize() + 14 * density));
    }

    @Override protected void onDraw(Canvas c) {
        if (segments.isEmpty()) return;
        long total = 0;
        int placeholders = 0;
        for (Segment s : segments) {
            if (s.placeholder) placeholders++; else total += Math.max(s.bandwidthKhz, 1);
        }
        float gap = 2 * density, barTop = TOP_DP * density + bracketH(), barH = 14 * density, w = getWidth();
        if (totalLabel != null) {
            // "|—— 75 MHz ——|": the label centred on a line whose end ticks come down to the bar's edges.
            float baseline = TOP_DP * density + text.getTextSize();
            float y = baseline - text.getTextSize() / 3f, half = text.measureText(totalLabel) / 2f, cx = w / 2f;
            float pad = 6 * density, edge = line.getStrokeWidth() / 2f, tickEnd = barTop - 2 * density;
            c.drawText(totalLabel, cx - half, baseline, text);
            c.drawLine(edge, y, cx - half - pad, y, line);
            c.drawLine(cx + half + pad, y, w - edge, y, line);
            c.drawLine(edge, y, edge, tickEnd, line);
            c.drawLine(w - edge, y, w - edge, tickEnd, line);
        }
        // Unknown-bandwidth segments get a fixed slot; they are not part of the proportional scale.
        float slot = placeholders == 0 ? 0 : Math.max(56 * density, w * 0.14f);
        float usable = w - gap * (segments.size() - 1) - slot * placeholders;
        float x = 0, labelsEnd = -w;
        for (Segment s : segments) {
            float sw = s.placeholder ? slot : total == 0 ? 0 : Math.max(4 * density, usable * Math.max(s.bandwidthKhz, 1) / total);
            int color = s.color != null ? s.color : "LTE".equals(s.rat) ? lte : "NR".equals(s.rat) ? nr : unknown;
            rect.set(x, barTop, Math.min(w, x + sw), barTop + barH);
            if (s.placeholder) {
                fill.setStyle(Paint.Style.STROKE);
                fill.setStrokeWidth(2 * density);
                fill.setColor(color);
                rect.inset(density, density);
                c.drawRoundRect(rect, 4 * density, 4 * density, fill);
                fill.setStyle(Paint.Style.FILL);
            } else {
                fill.setColor(color);
                c.drawRoundRect(rect, 4 * density, 4 * density, fill);
            }
            // Band and bandwidth below each segment; identity is in text, not colour alone. A label may run on past a
            // narrow segment; the next one then starts after it with a space (it touched before: "15 MHz100 MHz"),
            // and is left out when that would no longer start under its own segment.
            float line1 = barTop + barH + text.getTextSize() + 4 * density;
            float tx = Math.max(x, labelsEnd + 6 * density);
            if (tx < x + sw) {
                float labelW = text.measureText(s.label);
                if (tx + labelW <= w) {
                    c.drawText(s.label, tx, line1, text);
                    labelsEnd = Math.max(labelsEnd, tx + labelW);
                    float iconW = 14 * density, iconH = text.getTextSize() * 0.8f, iconX = tx + labelW + 4 * density;
                    if (s.showSignal && iconX + iconW <= w) {
                        drawBars(c, iconX, line1, iconW, iconH, s.rsrp);
                        labelsEnd = Math.max(labelsEnd, iconX + iconW);
                    }
                }
                float detailW = s.detail == null ? 0 : sub.measureText(s.detail);
                if (s.detail != null && tx + detailW <= w) {
                    c.drawText(s.detail, tx, line1 + sub.getTextSize() + 3 * density, sub);
                    labelsEnd = Math.max(labelsEnd, tx + detailW);
                }
            }
            x += sw + gap;
        }
    }

    /** Four rising bars, filled up to the level in the meter colour, the rest outlined; "—" when not reported. */
    private void drawBars(Canvas c, float left, float baseline, float w, float h, Integer rsrp) {
        int level = bars(rsrp);
        // Empty bars would read as "no signal"; the dash is the meters' "not reported".
        if (level < 0) { c.drawText("—", left, baseline, sub); return; }
        int on = ContextCompat.getColor(getContext(), MeterView.statusColorRes(Quality.level(Quality.Metric.RSRP, rsrp)));
        float gap = density, bw = (w - 3 * gap) / 4f;
        for (int i = 0; i < 4; i++) {
            float bh = h * (i + 1) / 4f, l = left + i * (bw + gap);
            rect.set(l, baseline - bh, l + bw, baseline);
            if (i < level) {
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(on);
            } else {
                fill.setStyle(Paint.Style.STROKE);
                fill.setStrokeWidth(Math.max(1, density * 0.8f));
                fill.setColor(unknown);
                rect.inset(fill.getStrokeWidth() / 2, fill.getStrokeWidth() / 2);
            }
            c.drawRect(rect, fill);
        }
        fill.setStyle(Paint.Style.FILL);
    }
}
