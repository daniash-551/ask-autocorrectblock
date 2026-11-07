package com.dani.askblock;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Forces TYPE_TEXT_FLAG_NO_SUGGESTIONS onto the EditorInfo AnySoftKeyboard receives from a blocked
 * app. ASK honours that flag itself: AnySoftKeyboardSuggestions.onStartInputView() clears
 * mPredictionOn, which hides the strip and, because isAutoCorrect() requires mPredictionOn, stops
 * autocorrection too.
 *
 * The blocked set lives in the module app and is read over a ContentProvider - the IME runs in a
 * different process and cannot see our SharedPreferences.
 */
public class Hooks implements IXposedHookLoadPackage {

    private static final String TAG = "AskBlock";
    private static final String IME_PKG = "com.menny.android.anysoftkeyboard";
    private static final Uri SETTINGS_URI = Uri.parse("content://com.dani.askblock.settings");
    private static final long CACHE_MS = 2000L;

    private static long sReadAt = 0L;
    private static Set<String> sBlocked = Collections.emptySet();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!IME_PKG.equals(lpparam.packageName)) {
            return;
        }
        XposedBridge.log(TAG + ": loaded into " + lpparam.processName);

        // ASK ships with -dontobfuscate, so these names are stable in release builds.
        boolean hooked = hook(lpparam, "com.anysoftkeyboard.ime.AnySoftKeyboardSuggestions", "onStartInputView");
        hooked |= hook(lpparam, "com.anysoftkeyboard.ime.AnySoftKeyboardSuggestions", "onStartInput");
        // Belt and braces: the base class method runs via super.onStartInputView().
        hook(lpparam, "com.anysoftkeyboard.AnySoftKeyboard", "onStartInputView");

        if (!hooked) {
            XposedBridge.log(TAG + ": ASK classes not found - feature inactive");
        }
    }

    private boolean hook(XC_LoadPackage.LoadPackageParam lpparam, String className, String method) {
        try {
            Class<?> cls = XposedHelpers.findClass(className, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, method, EditorInfo.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                apply(param);
                            } catch (Throwable t) {
                                XposedBridge.log(t);
                            }
                        }
                    });
            XposedBridge.log(TAG + ": hooked " + className + "#" + method);
            return true;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": no " + className + "#" + method + " (" + t + ")");
            return false;
        }
    }

    private void apply(XC_MethodHook.MethodHookParam param) {
        if (param.args == null || param.args.length == 0) return;
        if (!(param.args[0] instanceof EditorInfo)) return;
        EditorInfo info = (EditorInfo) param.args[0];
        String pkg = info.packageName;
        if (pkg == null || pkg.isEmpty()) return;
        if (!(param.thisObject instanceof Context)) return;
        if (!isBlocked((Context) param.thisObject, pkg)) return;

        final int before = info.inputType;
        info.inputType =
                (info.inputType | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
                        & ~(EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT
                                | EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE);
        if (before != info.inputType) {
            XposedBridge.log(TAG + ": suggestions+autocorrect off for " + pkg);
        }
    }

    private static boolean isBlocked(Context ctx, String pkg) {
        long now = System.currentTimeMillis();
        if (now - sReadAt > CACHE_MS) {
            sReadAt = now;
            sBlocked = readBlocked(ctx);
        }
        return sBlocked.contains(pkg);
    }

    private static Set<String> readBlocked(Context ctx) {
        try {
            Bundle b = ctx.getContentResolver().call(SETTINGS_URI, "blocked", null, null);
            if (b == null) return Collections.emptySet();
            ArrayList<String> list = b.getStringArrayList("packages");
            return list == null ? Collections.emptySet() : new HashSet<>(list);
        } catch (Throwable t) {
            XposedBridge.log(t);
            return Collections.emptySet();
        }
    }
}
