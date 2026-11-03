package io.github.fortuneoflab.dualsimbands.ui;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/** Created before the application: hands the sample data to the bands screen. Serves nothing. */
public final class DemoInit extends ContentProvider {
    @Override public boolean onCreate() {
        Demo.source = new DemoData(getContext());
        return true;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
