package io.github.fortuneoflab.dualsimbands.ui;

import android.content.Context;
import android.content.SharedPreferences;
import io.github.fortuneoflab.dualsimbands.radio.BandMemory;
import io.github.fortuneoflab.dualsimbands.radio.NrChannel;

/** Device-local storage for {@link BandMemory}: carrier + NR-ARFCN → band, nothing else. */
public final class BandMemoryStore {
    private final SharedPreferences prefs;
    private final String mcc, mnc;

    public BandMemoryStore(Context c, String mcc, String mnc) {
        this.prefs = c.getApplicationContext().getSharedPreferences("dualsimbands_bands", Context.MODE_PRIVATE);
        this.mcc = mcc; this.mnc = mnc;
    }

    public void learn(NrChannel ch) {
        if (!BandMemory.shouldLearn(ch)) return;
        String key = BandMemory.key(mcc, mnc, ch.nrArfcn);
        if (prefs.getInt(key, 0) != ch.band) prefs.edit().putInt(key, ch.band).apply();
    }

    /** Remembered band usable for this unresolved channel, or 0. */
    public int lookup(NrChannel ch) {
        if (ch == null || ch.nrArfcn < 0) return 0;
        return BandMemory.usable(ch, prefs.getInt(BandMemory.key(mcc, mnc, ch.nrArfcn), 0));
    }
}
