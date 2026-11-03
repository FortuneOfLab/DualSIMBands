package io.github.fortuneoflab.dualsimbands.history;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Local-only history database. Nothing here is sent anywhere; export goes through the user's document picker. */
public final class HistoryStore extends SQLiteOpenHelper {
    private static HistoryStore instance;

    public static synchronized HistoryStore get(Context c) {
        if (instance == null) instance = new HistoryStore(c.getApplicationContext());
        return instance;
    }

    /** Version 1, as the first release; the development versions' databases were cleared on the devices. */
    private HistoryStore(Context c) { super(c, "history.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE samples (id INTEGER PRIMARY KEY AUTOINCREMENT, t INTEGER NOT NULL, slot INTEGER NOT NULL,"
                + " schema INTEGER NOT NULL, summary TEXT, csv TEXT NOT NULL)");
        db.execSQL("CREATE INDEX samples_t ON samples(t)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    public void insert(HistorySample s) {
        ContentValues v = new ContentValues();
        v.put("t", s.epochMillis);
        v.put("slot", s.slot);
        v.put("schema", HistorySample.SCHEMA_VERSION);
        v.put("summary", s.summary);
        v.put("csv", HistoryCsv.line(s.values));
        getWritableDatabase().insert("samples", null, v);
    }

    public int prune(long olderThanEpochMillis) {
        return getWritableDatabase().delete("samples", "t < ?", new String[]{Long.toString(olderThanEpochMillis)});
    }

    public void deleteAll() {
        getWritableDatabase().delete("samples", null, null);
    }

    /** {count, oldest t, newest t}; times are 0 when empty. */
    public long[] stats() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*), MIN(t), MAX(t) FROM samples", null)) {
            c.moveToFirst();
            return new long[]{c.getLong(0), c.isNull(1) ? 0 : c.getLong(1), c.isNull(2) ? 0 : c.getLong(2)};
        }
    }

    /** Newest first: {epoch millis as string, summary}. */
    public List<String[]> latest(int limit) {
        List<String[]> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("samples", new String[]{"t", "summary"}, null, null, null, null,
                "t DESC", Integer.toString(limit))) {
            while (c.moveToNext()) out.add(new String[]{Long.toString(c.getLong(0)), c.getString(1)});
        }
        return out;
    }

    /** Writes BOM + header + rows (oldest first). */
    public int export(OutputStream stream) throws IOException {
        int rows = 0;
        Writer w = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
        w.write(HistoryCsv.BOM);
        w.write(HistoryCsv.header());
        w.write("\r\n");
        try (Cursor c = getReadableDatabase().query("samples", new String[]{"csv"}, "schema = ?",
                new String[]{Integer.toString(HistorySample.SCHEMA_VERSION)}, null, null, "t ASC")) {
            while (c.moveToNext()) {
                w.write(c.getString(0));
                w.write("\r\n");
                rows++;
            }
        }
        w.flush();
        return rows;
    }
}
