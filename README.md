# ASK Block

LSPosed module that turns off **suggestions and autocorrect** in
[AnySoftKeyboard](https://github.com/AnySoftKeyboard/AnySoftKeyboard) for apps you choose
(Termux by default).

## Why this needs a module

ASK only disables prediction for a few input classes
(`AnySoftKeyboardSuggestions.onStartInputView`):

```java
switch (attribute.inputType & EditorInfo.TYPE_MASK_CLASS) {
  case TYPE_CLASS_DATETIME: mPredictionOn = false; break;
  case TYPE_CLASS_NUMBER:   mPredictionOn = false; break;
  case TYPE_CLASS_PHONE:    mPredictionOn = false; break;
  default:                  /* "No class. Probably a console window" */
                            /* mPredictionOn stays true */
}
```

Termux hands the IME `TYPE_NULL`, which has no class bits, so it falls into `default:` and ASK
keeps predicting. `isAutoCorrect()` also requires `mPredictionOn`, so one flag disables both.

ASK already honours `TYPE_TEXT_FLAG_NO_SUGGESTIONS` (`IMEUtil.shouldHonorNoSuggestionsFlag`); it
just never receives that flag from these apps. This module supplies it.

## What the hook does

`Hooks` hooks `onStartInputView(EditorInfo, boolean)` and `onStartInput(EditorInfo, boolean)` on
`com.anysoftkeyboard.ime.AnySoftKeyboardSuggestions` (plus the base class method as a fallback),
and for a blocked package rewrites the client's `EditorInfo`:

```java
info.inputType = (info.inputType | TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        & ~(TYPE_TEXT_FLAG_AUTO_CORRECT | TYPE_TEXT_FLAG_AUTO_COMPLETE);
```

The contradiction bits must be cleared: ASK ignores `NO_SUGGESTIONS` when `AUTO_CORRECT` or
`AUTO_COMPLETE` is also set.

This relies on ASK release builds keeping class and method names — it ships
`-dontobfuscate` in `proguard-dont-obs.txt`, so they are stable. Verified on ASK 1.13.8175:

```
Lcom/anysoftkeyboard/ime/AnySoftKeyboardSuggestions;  present
onStartInputView:(Landroid/view/inputmethod/EditorInfo;Z)V  PUBLIC
```

## Blocked apps

Chosen in the module's own UI (launcher activity); Termux is the default when nothing is stored.
The list is served to the IME process by a `ContentProvider`
(`content://com.dani.askblock.settings`, method `blocked`) because the keyboard runs in a
different process and cannot read our `SharedPreferences`. The hook caches the list for 2 s.

## Build

```sh
export JAVA_HOME=~/tools/jdk17
export ANDROID_HOME=~/Android/Sdk
~/tools/gradle/gradle-8.10.2/bin/gradle --no-daemon assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

AGP 8.5.2, compileSdk 35, minSdk 29. `app/libs/xposed-api82.jar` is `compileOnly` — never
packaged; LSPosed provides the real implementation at runtime.

## Install

1. Install the APK, open **ASK Block**, tick the apps (Termux is pre-ticked).
2. LSPosed → Modules → **ASK Block** → enable it, scope: **AnySoftKeyboard** only.
3. Force-stop AnySoftKeyboard (or reboot) so the hook loads at process start.
4. Check it fired:

```sh
adb logcat -s LSPosed-Bridge | grep AskBlock
# AskBlock: hooked com.anysoftkeyboard.ime.AnySoftKeyboardSuggestions#onStartInputView
# AskBlock: suggestions+autocorrect off for com.termux
```

Toggling a checkbox takes effect on the next field focus (2 s cache).

## Caveats

- Hooks depend on the two ASK method names above. If a future ASK release renames them the
  feature goes quiet, it does not crash: every hook is wrapped and logs what it could not find.
- The settings provider is exported without a permission — it exposes only package names.
