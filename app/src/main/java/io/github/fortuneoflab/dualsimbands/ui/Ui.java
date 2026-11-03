package io.github.fortuneoflab.dualsimbands.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.widget.TextViewCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/** Small builders so screens stay theme-aware (light/dark, dynamic color) without hard-coded colors. */
public final class Ui {
    private Ui() {}

    /**
     * A result message at the bottom of the screen, up to six lines for a few seconds. A toast is cut at two lines
     * on Android 12 and later (seen with the eSIM switch result on the Pixel 10a).
     */
    public static void message(androidx.fragment.app.Fragment f, CharSequence text) {
        android.app.Activity a = f.getActivity();
        if (a == null || text == null) return;
        View root = a.findViewById(android.R.id.content);
        com.google.android.material.snackbar.Snackbar bar = com.google.android.material.snackbar.Snackbar.make(root, text, 8000);
        TextView body = bar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
        if (body != null) body.setMaxLines(6);
        bar.show();
    }

    /**
     * Puts the SIM-kind icon Android's SIM settings use (a SIM card for a physical SIM, a SIM card with a download
     * arrow for an eSIM) before a title. The kind is also given in words for screen readers.
     */
    public static void simIcon(TextView title, boolean embedded) {
        simIcon(title, embedded, color(title.getContext(), com.google.android.material.R.attr.colorOnSurfaceVariant));
    }

    /** @param tint the icon's colour */
    public static void simIcon(TextView title, boolean embedded, int tint) {
        Context c = title.getContext();
        CharSequence words = title.getText();
        android.graphics.drawable.Drawable icon = androidx.core.content.ContextCompat.getDrawable(c,
                embedded ? io.github.fortuneoflab.dualsimbands.R.drawable.ic_sim_esim : io.github.fortuneoflab.dualsimbands.R.drawable.ic_sim_physical);
        if (icon == null) return;
        icon = icon.mutate();
        icon.setTint(tint);
        int size = Math.round(title.getTextSize() * 1.1f);
        icon.setBounds(0, 0, size, size);
        title.setCompoundDrawablesRelative(icon, null, null, null);
        title.setCompoundDrawablePadding(dp(c, 6));
        title.setContentDescription(c.getString(embedded ? io.github.fortuneoflab.dualsimbands.R.string.radio_kind_esim
                : io.github.fortuneoflab.dualsimbands.R.string.radio_kind_physical) + " " + words);
    }

    public static int dp(Context c, int value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }

    /** Adds a filled Material card and returns its vertical body. */
    public static LinearLayout card(ViewGroup parent) {
        Context c = parent.getContext();
        MaterialCardView card = new MaterialCardView(c, null, com.google.android.material.R.attr.materialCardViewFilledStyle);
        card.setCardBackgroundColor(surface(c, com.google.android.material.R.attr.colorSurfaceContainerHighest));
        LinearLayout body = vertical(c);
        body.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        card.addView(body);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(c, 6), 0, dp(c, 6));
        parent.addView(card, lp);
        return body;
    }

    /**
     * Card and tile fill: the theme's container colour in the dark theme; in the light theme a lighter grey than
     * the theme's (which read as heavy on the near-white page).
     */
    public static int surface(Context c, int darkAttr) {
        boolean night = (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        return night ? color(c, darkAttr) : androidx.core.content.ContextCompat.getColor(c, io.github.fortuneoflab.dualsimbands.R.color.card_light);
    }

    public static LinearLayout vertical(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    /** Numbered step heading, so every step of a procedure looks the same. */
    public static TextView step(ViewGroup p, CharSequence s) {
        gap(p, 12);
        TextView t = text(p, s, com.google.android.material.R.attr.textAppearanceTitleMedium);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(color(p.getContext(), androidx.appcompat.R.attr.colorPrimary));
        return t;
    }
    public static TextView title(ViewGroup p, CharSequence s) {
        return text(p, s, com.google.android.material.R.attr.textAppearanceTitleMedium);
    }
    public static TextView body(ViewGroup p, CharSequence s) {
        return text(p, s, com.google.android.material.R.attr.textAppearanceBodyMedium);
    }
    public static TextView caption(ViewGroup p, CharSequence s) {
        TextView t = text(p, s, com.google.android.material.R.attr.textAppearanceBodySmall);
        t.setTextColor(color(p.getContext(), com.google.android.material.R.attr.colorOnSurfaceVariant));
        return t;
    }

    public static TextView text(ViewGroup p, CharSequence s, int appearanceAttr) {
        Context c = p.getContext();
        TextView t = new TextView(c);
        TypedValue v = new TypedValue();
        if (c.getTheme().resolveAttribute(appearanceAttr, v, true)) TextViewCompat.setTextAppearance(t, v.resourceId);
        t.setText(s);
        t.setPadding(0, dp(c, 3), 0, dp(c, 3));
        p.addView(t);
        return t;
    }

    /** Label/value pair; the label column keeps values aligned for scanning. */
    public static TextView row(ViewGroup p, CharSequence label, CharSequence value) {
        Context c = p.getContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        p.addView(row);
        TextView l = caption(row, label);
        l.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 92), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView v = body(row, value);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        v.setTextIsSelectable(true);
        return v;
    }

    public static MaterialButton button(ViewGroup p, CharSequence text, View.OnClickListener click) {
        return add(p, new MaterialButton(p.getContext()), text, click);
    }

    public static MaterialButton outlined(ViewGroup p, CharSequence text, View.OnClickListener click) {
        return add(p, new MaterialButton(p.getContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle), text, click);
    }

    public static MaterialButton textButton(ViewGroup p, CharSequence text, View.OnClickListener click) {
        return add(p, new MaterialButton(p.getContext(), null,
                androidx.appcompat.R.attr.borderlessButtonStyle), text, click);
    }

    private static MaterialButton add(ViewGroup p, MaterialButton b, CharSequence text, View.OnClickListener click) {
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(click);
        p.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }

    /** One table cell: text plus an optional coloured dot that sits beside (never replaces) the text. */
    public static final class Cell {
        final CharSequence text;
        final int dotColor;
        final boolean bold;
        public Cell(CharSequence text) { this(text, 0, false); }
        public Cell(CharSequence text, int dotColor, boolean bold) { this.text = text; this.dotColor = dotColor; this.bold = bold; }
    }

    /**
     * A compact table with a single rounded outline, a tinted header row and hairline dividers.
     * {@code dotColor} in a cell is a resolved colour int (0 = none).
     */
    public static android.widget.TableLayout table(ViewGroup parent, String[] header, java.util.List<Cell[]> rows) {
        return table(parent, header, rows, false);
    }

    /**
     * Like {@link #table}, but only the given columns may shrink and wrap, so a wide table stays inside the
     * card while short numeric columns keep their values on one line.
     */
    public static android.widget.TableLayout table(ViewGroup parent, String[] header, java.util.List<Cell[]> rows,
                                                   int... wrapColumns) {
        android.widget.TableLayout t = table(parent, header, rows, false);
        for (int col : wrapColumns) t.setColumnShrinkable(col, true);
        return t;
    }

    /** Right-aligns the given columns below the header, so numbers of different lengths line up by their last digit. */
    public static void alignEnd(android.widget.TableLayout t, int... columns) {
        // Index 0 is the header row: its labels stay left-aligned like every other column label.
        for (int i = 1; i < t.getChildCount(); i++) {
            if (!(t.getChildAt(i) instanceof android.widget.TableRow)) continue;
            android.widget.TableRow row = (android.widget.TableRow) t.getChildAt(i);
            for (int col : columns) {
                if (col >= row.getChildCount() || !(row.getChildAt(col) instanceof TextView)) continue;
                TextView v = (TextView) row.getChildAt(col);
                v.setGravity(android.view.Gravity.END);
                v.setPaddingRelative(v.getPaddingStart(), v.getPaddingTop(), dp(v.getContext(), 12), v.getPaddingBottom());
                // A compound drawable stays at the view's start edge; inline the status dot so it moves with the number.
                android.graphics.drawable.Drawable dot = v.getCompoundDrawablesRelative()[0];
                if (dot != null) {
                    android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder("  ");
                    b.setSpan(new android.text.style.ImageSpan(dot, android.text.style.DynamicDrawableSpan.ALIGN_CENTER),
                            0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    b.append(v.getText());
                    v.setCompoundDrawablesRelative(null, null, null, null);
                    v.setText(b);
                }
            }
        }
    }

    /** Centres every label in the header row. */
    public static void centerHeader(android.widget.TableLayout t) {
        if (t.getChildCount() == 0 || !(t.getChildAt(0) instanceof android.widget.TableRow)) return;
        android.widget.TableRow head = (android.widget.TableRow) t.getChildAt(0);
        for (int col = 0; col < head.getChildCount(); col++) {
            if (head.getChildAt(col) instanceof TextView) ((TextView) head.getChildAt(col)).setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        }
    }

    /** Gives each column a fixed share of the width, so tables with different contents line up with each other. */
    public static void columnWeights(android.widget.TableLayout t, float... weights) {
        t.setStretchAllColumns(false);
        for (int i = 0; i < t.getChildCount(); i++) {
            if (!(t.getChildAt(i) instanceof android.widget.TableRow)) continue;
            android.widget.TableRow row = (android.widget.TableRow) t.getChildAt(i);
            for (int col = 0; col < weights.length && col < row.getChildCount(); col++) {
                row.getChildAt(col).setLayoutParams(new android.widget.TableRow.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, weights[col]));
            }
        }
    }

    /** Two columns where the second takes the remaining width and wraps long text. */
    public static android.widget.TableLayout keyValueTable(ViewGroup parent, String[] header, java.util.List<Cell[]> rows) {
        return table(parent, header, rows, true);
    }

    private static android.widget.TableLayout table(ViewGroup parent, String[] header, java.util.List<Cell[]> rows,
                                                    boolean keyValue) {
        Context c = parent.getContext();
        android.widget.TableLayout t = new android.widget.TableLayout(c);
        if (keyValue) {
            t.setColumnStretchable(1, true);
            t.setColumnShrinkable(1, true);
        } else {
            t.setStretchAllColumns(true);
        }
        float radius = dp(c, 10);
        int stroke = Math.max(1, dp(c, 1));
        android.graphics.drawable.GradientDrawable frame = new android.graphics.drawable.GradientDrawable();
        frame.setCornerRadius(radius);
        frame.setStroke(stroke, color(c, com.google.android.material.R.attr.colorOutlineVariant));
        t.setBackground(frame);
        t.setPadding(stroke, stroke, stroke, stroke);
        int divider = color(c, com.google.android.material.R.attr.colorOutlineVariant);
        // The header fill is rounded on top with the frame's inner radius, so it never covers the corners.
        float inner = Math.max(0, radius - stroke);
        android.graphics.drawable.GradientDrawable headerBg = new android.graphics.drawable.GradientDrawable();
        headerBg.setColor(color(c, com.google.android.material.R.attr.colorSurfaceContainer));
        headerBg.setCornerRadii(new float[]{inner, inner, inner, inner, 0, 0, 0, 0});
        android.widget.TableRow head = new android.widget.TableRow(c);
        head.setBackground(headerBg);
        for (String h : header) head.addView(cellView(c, new Cell(h), true));
        t.addView(head);
        for (Cell[] r : rows) {
            View line = new View(c);
            line.setBackgroundColor(divider);
            t.addView(line, new android.widget.TableLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1) / 2)));
            android.widget.TableRow row = new android.widget.TableRow(c);
            for (Cell cell : r) row.addView(cellView(c, cell, false));
            t.addView(row);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(c, 6), 0, dp(c, 6));
        parent.addView(t, lp);
        return t;
    }

    private static TextView cellView(Context c, Cell cell, boolean header) {
        TextView v = new TextView(c);
        TypedValue tv = new TypedValue();
        int attr = header ? com.google.android.material.R.attr.textAppearanceLabelMedium
                : com.google.android.material.R.attr.textAppearanceBodyMedium;
        if (c.getTheme().resolveAttribute(attr, tv, true)) TextViewCompat.setTextAppearance(v, tv.resourceId);
        if (header) v.setTextColor(color(c, com.google.android.material.R.attr.colorOnSurfaceVariant));
        if (cell.bold) v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setText(cell.text);
        v.setPadding(dp(c, 10), dp(c, header ? 6 : 8), dp(c, 6), dp(c, header ? 6 : 8));
        if (cell.dotColor != 0) {
            android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
            dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setColor(cell.dotColor);
            dot.setSize(dp(c, 9), dp(c, 9));
            v.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null);
            v.setCompoundDrawablePadding(dp(c, 6));
        }
        return v;
    }

    public static int color(Context c, int attr) {
        TypedValue v = new TypedValue();
        c.getTheme().resolveAttribute(attr, v, true);
        return v.data;
    }

    public static void gap(ViewGroup p, int dp) {
        View v = new View(p.getContext());
        p.addView(v, new LinearLayout.LayoutParams(1, dp(p.getContext(), dp)));
    }
}
