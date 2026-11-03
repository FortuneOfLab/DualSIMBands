package io.github.fortuneoflab.dualsimbands.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import io.github.fortuneoflab.dualsimbands.R;

/**
 * Settings rows in the style of Android's own Settings app: a small category label, then each setting as its
 * own tile (title, summary, optional switch) in a group whose outer corners are rounded and inner ones tight.
 */
public final class Pref {
    private Pref() {}

    public static TextView category(ViewGroup page, String title) {
        Context c = page.getContext();
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextColor(Ui.color(c, com.google.android.material.R.attr.colorOnSurfaceVariant));
        // The space above separates a category from the group before it; the first one on a page needs little.
        t.setPadding(Ui.dp(c, 16), Ui.dp(c, page.getChildCount() == 0 ? 8 : 20), Ui.dp(c, 16), Ui.dp(c, 8));
        page.addView(t);
        return t;
    }

    /**
     * The page's one footer, as Android's settings guidelines place explanations: at the bottom of the screen,
     * a divider above it, an info icon, then the notes.
     * @param notes each a bold heading line and then one bullet per line ("\n"); a single line is a plain note
     */
    public static void footer(ViewGroup page, String... notes) {
        Context c = page.getContext();
        View rule = new View(c);
        rule.setBackgroundColor(Ui.color(c, com.google.android.material.R.attr.colorOutlineVariant));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1)));
        lp.setMargins(0, Ui.dp(c, 24), 0, Ui.dp(c, 16));
        page.addView(rule, lp);
        android.widget.ImageView icon = new android.widget.ImageView(c);
        icon.setImageResource(R.drawable.ic_info);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                Ui.color(c, com.google.android.material.R.attr.colorOnSurfaceVariant)));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24));
        ip.setMarginStart(Ui.dp(c, 16));
        page.addView(icon, ip);
        int color = Ui.color(c, com.google.android.material.R.attr.colorOnSurfaceVariant);
        for (String note : notes) {
            // A note is its heading, then one bullet per line; a wrapped bullet keeps its text indented.
            String[] lines = note.split("\n");
            for (int i = 0; i < lines.length; i++) {
                TextView t = new TextView(c);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                t.setTextColor(color);
                if (i == 0 && lines.length > 1) {
                    t.setText(lines[0]);
                    t.setTypeface(null, android.graphics.Typeface.BOLD);
                    t.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), 0);
                } else {
                    android.text.SpannableString b = new android.text.SpannableString(lines[i]);
                    if (lines.length > 1) b.setSpan(new android.text.style.BulletSpan(Ui.dp(c, 8), color, Ui.dp(c, 2)),
                            0, b.length(), android.text.Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                    t.setText(b);
                    t.setPadding(Ui.dp(c, 20), Ui.dp(c, 4), Ui.dp(c, 16), 0);
                }
                page.addView(t);
            }
        }
    }

    public static final class Group {
        private final Context c;
        private final LinearLayout box;
        private final List<View> tiles = new ArrayList<>();

        public Group(ViewGroup page) {
            c = page.getContext();
            box = Ui.vertical(c);
            page.addView(box);
        }

        /** A row with a title and an optional summary; {@code onClick} null makes it informational. */
        public TextView text(String title, String summary, View.OnClickListener onClick) {
            LinearLayout tile = tile();
            TextView s = texts(tile, title, summary);
            if (onClick != null) tile.setOnClickListener(onClick); else tile.setClickable(false);
            add(tile);
            return s;
        }

        public MaterialSwitch toggle(String title, String summary, boolean checked,
                              android.widget.CompoundButton.OnCheckedChangeListener listener) {
            LinearLayout tile = tile();
            texts(tile, title, summary);
            MaterialSwitch sw = new MaterialSwitch(c);
            sw.setChecked(checked);
            sw.setOnCheckedChangeListener(listener);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(Ui.dp(c, 12));
            tile.addView(sw, lp);
            tile.setOnClickListener(v -> sw.toggle());
            add(tile);
            return sw;
        }

        /**
         * A row showing the current choice as its summary; tapping opens a single-choice dialog.
         * Returns a setter that shows a choice made elsewhere, without calling {@code onPick}.
         */
        public IntConsumer choice(String title, String[] labels, int selected, IntConsumer onPick) {
            LinearLayout tile = tile();
            TextView summary = texts(tile, title, labels[Math.max(0, selected)]);
            int[] current = {Math.max(0, selected)};
            IntConsumer show = which -> { current[0] = which; summary.setText(labels[which]); };
            tile.setOnClickListener(v -> new MaterialAlertDialogBuilder(c).setTitle(title)
                    .setSingleChoiceItems(labels, current[0], (d, which) -> {
                        show.accept(which);
                        onPick.accept(which);
                        d.dismiss();
                    })
                    .setNegativeButton(R.string.action_cancel, null).show());
            add(tile);
            return show;
        }

        /** The group's container, to show or hide the whole group. */
        public View view() { return box; }

        /** Applies the rounded corners once every tile is in: large outside the group, small between tiles. */
        public void close() {
            float outer = Ui.dp(c, 24), inner = Ui.dp(c, 4);
            int fill = Ui.surface(c, com.google.android.material.R.attr.colorSurfaceContainerHigh);
            for (int i = 0; i < tiles.size(); i++) {
                float top = i == 0 ? outer : inner, bottom = i == tiles.size() - 1 ? outer : inner;
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(fill);
                bg.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
                View t = tiles.get(i);
                android.graphics.drawable.RippleDrawable ripple = new android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(Ui.color(c, android.R.attr.colorControlHighlight)),
                        bg, bg);
                t.setBackground(t.isClickable() ? ripple : bg);
            }
        }

        private LinearLayout tile() {
            LinearLayout tile = new LinearLayout(c);
            tile.setOrientation(LinearLayout.HORIZONTAL);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setMinimumHeight(Ui.dp(c, 64));
            tile.setPadding(Ui.dp(c, 20), Ui.dp(c, 14), Ui.dp(c, 20), Ui.dp(c, 14));
            return tile;
        }

        private TextView texts(LinearLayout tile, String title, String summary) {
            LinearLayout col = Ui.vertical(c);
            TextView t = new TextView(c);
            t.setText(title);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            t.setTextColor(Ui.color(c, com.google.android.material.R.attr.colorOnSurface));
            col.addView(t);
            TextView s = new TextView(c);
            s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            s.setTextColor(Ui.color(c, com.google.android.material.R.attr.colorOnSurfaceVariant));
            s.setText(summary == null ? "" : summary);
            s.setVisibility(summary == null ? View.GONE : View.VISIBLE);
            col.addView(s);
            tile.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            return s;
        }

        private void add(View tile) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (!tiles.isEmpty()) lp.topMargin = Ui.dp(c, 3);
            box.addView(tile, lp);
            tiles.add(tile);
        }
    }
}
