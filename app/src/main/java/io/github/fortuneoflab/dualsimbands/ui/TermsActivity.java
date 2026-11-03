package io.github.fortuneoflab.dualsimbands.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import io.github.fortuneoflab.dualsimbands.R;

/** The terminology list, opened from the settings tab as its own page so the settings stay short. */
public final class TermsActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        EdgeToEdge.enable(this);
        super.onCreate(state);
        setContentView(R.layout.activity_terms);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        View appBar = findViewById(R.id.app_bar), scroll = findViewById(R.id.scroll);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            appBar.setPadding(bars.left, bars.top, bars.right, 0);
            scroll.setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });

        LinearLayout page = findViewById(R.id.page);
        Pref.Group terms = new Pref.Group(page);
        int[][] ids = {
                {R.string.term_connected, R.string.term_connected_desc}, {R.string.term_standby, R.string.term_standby_desc},
                {R.string.term_unconfirmed, R.string.term_unconfirmed_desc}, {R.string.term_none, R.string.term_none_desc},
                {R.string.term_not_connected, R.string.term_not_connected_desc}, {R.string.term_plus, R.string.term_plus_desc},
                {R.string.term_sub6, R.string.term_sub6_desc}, {R.string.term_mmwave, R.string.term_mmwave_desc},
                {R.string.term_tenyou, R.string.term_tenyou_desc}, {R.string.term_reported, R.string.term_reported_desc},
                {R.string.term_approx, R.string.term_approx_desc}, {R.string.term_ta, R.string.term_ta_desc},
                {R.string.term_peak, R.string.term_peak_desc},
                {R.string.term_bars, R.string.term_bars_desc},
                {R.string.term_carrier_colors, R.string.term_carrier_colors_desc},
                {R.string.term_sim_icons, R.string.term_sim_icons_desc},
                {R.string.term_inferred, R.string.term_inferred_desc}, {R.string.term_remembered, R.string.term_remembered_desc},
                {R.string.term_candidates, R.string.term_candidates_desc}};
        for (int[] t : ids) terms.text(getString(t[0]), getString(t[1]), null);
        terms.close();
    }
}
