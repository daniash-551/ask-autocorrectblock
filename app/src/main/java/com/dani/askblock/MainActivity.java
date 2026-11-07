package com.dani.askblock;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** App picker: tick the apps that should get no suggestions and no autocorrect. */
public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private Set<String> blocked;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(BlockProvider.PREFS, Context.MODE_PRIVATE);
        blocked = new HashSet<>();
        Set<String> stored = prefs.getStringSet(BlockProvider.KEY, null);
        if (stored == null) {
            blocked.add(BlockProvider.DEFAULT_PACKAGE);
        } else {
            blocked.addAll(stored);
        }

        float dp = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * dp);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("No suggestions, no autocorrect");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Ticked apps get an empty suggestion strip and no autocorrection in "
                + "AnySoftKeyboard. Takes effect the next time you focus a field in that app.");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setPadding(0, (int) (6 * dp), 0, (int) (14 * dp));
        root.addView(hint);

        for (ResolveInfo ri : launchableApps()) {
            final String pkg = ri.activityInfo.packageName;
            String label;
            try {
                label = ri.loadLabel(getPackageManager()).toString();
            } catch (Throwable t) {
                label = pkg;
            }
            CheckBox cb = new CheckBox(this);
            cb.setText(label + "\n" + pkg);
            cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            cb.setChecked(blocked.contains(pkg));
            cb.setPadding(0, (int) (8 * dp), 0, (int) (8 * dp));
            cb.setOnCheckedChangeListener((b, checked) -> {
                if (checked) blocked.add(pkg); else blocked.remove(pkg);
                prefs.edit().putStringSet(BlockProvider.KEY, new HashSet<>(blocked)).apply();
            });
            root.addView(cb);
        }

        setContentView(scroll);
    }

    private List<ResolveInfo> launchableApps() {
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = new ArrayList<>(
                getPackageManager().queryIntentActivities(main, 0));
        List<String> seen = new ArrayList<>();
        List<ResolveInfo> unique = new ArrayList<>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || seen.contains(pkg)) continue;
            seen.add(pkg);
            unique.add(ri);
        }
        Collections.sort(unique, Comparator.comparing(
                ri -> ri.loadLabel(getPackageManager()).toString().toLowerCase()));
        return unique;
    }
}
