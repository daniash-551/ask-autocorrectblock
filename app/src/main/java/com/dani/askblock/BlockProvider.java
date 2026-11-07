package com.dani.askblock;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Serves the blocked package list to the IME process. */
public class BlockProvider extends ContentProvider {

    public static final String AUTHORITY = "com.dani.askblock.settings";
    public static final String PREFS = "settings";
    public static final String KEY = "blocked";
    public static final String DEFAULT_PACKAGE = "com.termux";

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Bundle out = new Bundle();

        if ("set".equals(method) && arg != null) {
            Set<String> now = read(prefs);
            boolean blocked = extras == null || extras.getBoolean("blocked", true);
            if (blocked) now.add(arg); else now.remove(arg);
            prefs.edit().putStringSet(KEY, now).apply();
        } else if ("reset".equals(method)) {
            prefs.edit().remove(KEY).apply();
        }

        out.putStringArrayList("packages", new java.util.ArrayList<>(read(prefs)));
        return out;
    }

    private static Set<String> read(SharedPreferences prefs) {
        Set<String> stored = prefs.getStringSet(KEY, null);
        if (stored == null) {
            return new HashSet<>(Collections.singletonList(DEFAULT_PACKAGE));
        }
        return new HashSet<>(stored);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
