package io.github.fortuneoflab.dualsimbands.ui.graph;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;
import androidx.core.content.ContextCompat;
import io.github.fortuneoflab.dualsimbands.R;

/**
 * One horizontal quality meter: "RSRP  -98 dBm  [=====-----]". The bar colour shows the level; the level
 * word stays in the content description for TalkBack. Unknown values draw an empty track.
 */
public final class MeterView extends View {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), muted = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private String name = "", number = "", unit = "", level = "";
    private float fraction = -1f;
    private final boolean compact;

    public MeterView(Context c) { this(c, false); }

    public MeterView(Context c, boolean compact) {
        super(c);
        this.compact = compact;
        density = c.getResources().getDisplayMetrics().density;
        text.setTextSize(sp(c, compact ? 12 : 14));
        text.setColor(themeColor(c, com.google.android.material.R.attr.colorOnSurface));
        muted.setTextSize(sp(c, compact ? 11 : 12));
        muted.setColor(themeColor(c, com.google.android.material.R.attr.colorOnSurfaceVariant));
        track.setColor(ContextCompat.getColor(c, R.color.chart_track));
    }

    public void set(String name, Quality.Metric metric, Integer v) { set(name, metric, v, false); }

    /** @param approximate a computed rather than reported value, shown as "≈-59 dBm" */
    public void set(String name, Quality.Metric metric, Integer v, boolean approximate) {
        this.name = name;
        this.number = v == null ? "—" : (approximate ? "≈" : "") + v;
        this.unit = v == null ? "" : metric.unit;
        Quality.Level l = Quality.level(metric, v);
        this.level = Quality.label(l);
        this.fraction = Quality.fraction(metric, v);
        fill.setColor(ContextCompat.getColor(getContext(), statusColor(l)));
        setContentDescription(name + " " + number + " " + unit + " " + level);
        invalidate();
    }

    public static int statusColorRes(Quality.Level l) { return statusColor(l); }

    static int statusColor(Quality.Level l) {
        switch (l) {
            case GOOD: return R.color.status_good;
            case FAIR: return R.color.status_warning;
            case POOR: return R.color.status_critical;
            default: return R.color.chart_unknown;
        }
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round(dp(compact ? 22 : 28)));
    }

    @Override protected void onDraw(Canvas canvas) {
        float h = getHeight(), w = getWidth();
        float baseline = h / 2f - (text.descent() + text.ascent()) / 2f;
        // Columns are sized from the widest label, number and unit, the same in every row, so the units line
        // up and the number sits close to its label.
        float nameW = muted.measureText("RSRQ") + dp(8);
        float numberEnd = nameW + text.measureText("≈-140");
        float unitX = numberEnd + dp(4);
        canvas.drawText(name, 0, baseline, muted);
        canvas.drawText(number, numberEnd - text.measureText(number), baseline, text);
        canvas.drawText(unit, unitX, baseline, text);
        float left = unitX + text.measureText("dBm") + dp(10), right = w;
        float barH = dp(compact ? 6 : 8), top = (h - barH) / 2f;
        if (right > left) {
            rect.set(left, top, right, top + barH);
            canvas.drawRoundRect(rect, barH / 2, barH / 2, track);
            if (fraction >= 0) {
                rect.set(left, top, left + Math.max(barH, (right - left) * fraction), top + barH);
                canvas.drawRoundRect(rect, barH / 2, barH / 2, fill);
            }
        }
    }

    private float dp(float v) { return v * density; }

    static float sp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
    }

    static int themeColor(Context c, int attr) {
        TypedValue v = new TypedValue();
        c.getTheme().resolveAttribute(attr, v, true);
        return v.data;
    }
}
