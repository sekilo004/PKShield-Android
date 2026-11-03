// The game's activity: Android's NativeActivity (the engine, libnxnative.so) plus what only Java can see: which input devices are connected.
// A controller connected (Bluetooth / USB gamepad or joystick, e.g. a Joy-Con pair) hides the touch controls; disconnecting the last one shows them again.
// A touch on the screen still brings them back while a controller is connected (touch_ui.c).
package com.nxnative;

import android.app.NativeActivity;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.view.InputDevice;

public final class GameActivity extends NativeActivity implements InputManager.InputDeviceListener {
    static { System.loadLibrary("nxnative"); }   // loaded through this class's loader, so the native method below resolves (NativeActivity then opens the same library)
    private static native void nativePadConnected(boolean connected);
    private static native void nativeTrimMemory(int level);
    public static native boolean nativeGetPrewarmStatus(int[] out);
    public static native void nativeCancelPrewarm();
    private InputManager im;
    static boolean viaSetup;   // this process started the game from the setup screen (SetupActivity.startGame)

    private android.view.View prewarmOverlay;
    private android.os.Handler progressHandler;
    private Runnable progressRunnable;

    // The system is short of memory (or the game left the screen): the engine gives back the device memory and the allocator pages it no longer uses, before the low-memory killer looks at this process
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        try { nativeTrimMemory(level); } catch (UnsatisfiedLinkError e) { }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        // Android brings the task back after the process ended (recents, the launcher icon) straight into this activity, past the setup screen that translates the
        // shaders the game captured last time: hand over to it before there is a window (the engine starts with the window, android_main.c)
        if (!viaSetup && !SetupActivity.pendingCaptures(getExternalFilesDir(null)).isEmpty()) { startActivity(new Intent(this, SetupActivity.class)); finish(); return; }
        im = getSystemService(InputManager.class);
        if (im != null) im.registerInputDeviceListener(this, null);
        check();
        setupPrewarmOverlay();
    }
    @Override protected void onResume() { super.onResume(); check(); }
    @Override protected void onDestroy() {
        if (im != null) im.unregisterInputDeviceListener(this);
        dismissOverlay();
        super.onDestroy();
    }
    @Override public void onInputDeviceAdded(int id) { check(); }
    @Override public void onInputDeviceRemoved(int id) { check(); }
    @Override public void onInputDeviceChanged(int id) { check(); }

    private void check() {
        boolean pad = false;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id); if (d == null || d.isVirtual()) continue;
            int s = d.getSources();
            if ((s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) pad = true;
        }
        try { nativePadConnected(pad); } catch (UnsatisfiedLinkError e) { }
    }

    private void setupPrewarmOverlay() {
        java.io.File envFile = new java.io.File(getExternalFilesDir(null), "env.txt");
        if (envFile.exists()) {
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(envFile))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.trim().startsWith("DM_PREWARM_STARTUP=0")) return;
                }
            } catch (Exception ignored) { }
        }

        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(0xF00F111A);

        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER);
        box.setPadding(64, 48, 64, 48);

        android.widget.FrameLayout.LayoutParams boxLp = new android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.CENTER
        );
        box.setLayoutParams(boxLp);

        boolean es = "es".equals(java.util.Locale.getDefault().getLanguage());

        android.widget.TextView title = new android.widget.TextView(this);
        title.setText(es ? "Compilando Shaders..." : "Compiling Shaders...");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(android.view.Gravity.CENTER);
        box.addView(title);

        android.widget.TextView subtitle = new android.widget.TextView(this);
        subtitle.setText(es ? "Optimizando pipelines de gráficos para evitar tirones..." : "Preparing graphics pipelines to prevent in-game stutter...");
        subtitle.setTextColor(0xFFAAAAAA);
        subtitle.setTextSize(13);
        subtitle.setGravity(android.view.Gravity.CENTER);
        subtitle.setPadding(0, 8, 0, 24);
        box.addView(subtitle);

        android.widget.ProgressBar bar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        bar.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 24));
        box.addView(bar);

        android.widget.TextView progressText = new android.widget.TextView(this);
        progressText.setText(es ? "Iniciando compilador..." : "Starting compiler...");
        progressText.setTextColor(0xFFDDDDDD);
        progressText.setTextSize(14);
        progressText.setGravity(android.view.Gravity.CENTER);
        progressText.setPadding(0, 16, 0, 24);
        box.addView(progressText);

        android.widget.Button skipBtn = new android.widget.Button(this);
        skipBtn.setText(es ? "Omitir" : "Skip");
        skipBtn.setTextColor(0xFFCCCCCC);
        skipBtn.setBackgroundColor(0x33FFFFFF);
        skipBtn.setOnClickListener(v -> {
            try { nativeCancelPrewarm(); } catch (UnsatisfiedLinkError e) { }
            dismissOverlay();
        });
        android.widget.LinearLayout.LayoutParams btnLp = new android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        btnLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        skipBtn.setLayoutParams(btnLp);
        box.addView(skipBtn);

        root.addView(box);
        addContentView(root, new android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ));
        prewarmOverlay = root;

        progressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        final int[] pollCount = {0};
        progressRunnable = new Runnable() {
            @Override public void run() {
                if (prewarmOverlay == null) return;
                pollCount[0]++;
                int[] stats = new int[2];
                boolean fin = false;
                try { fin = nativeGetPrewarmStatus(stats); } catch (UnsatisfiedLinkError e) { fin = true; }
                int done = stats[0], total = stats[1];
                if (total > 0) {
                    bar.setMax(total);
                    bar.setProgress(done);
                    int pct = (int)((done * 100.0) / total);
                    progressText.setText(done + " / " + total + " (" + pct + "%)");
                }
                if (fin || (total > 0 && done >= total) || (total == 0 && pollCount[0] > 30)) {
                    dismissOverlay();
                } else {
                    progressHandler.postDelayed(this, 50);
                }
            }
        };
        progressHandler.postDelayed(progressRunnable, 100);
    }

    private void dismissOverlay() {
        if (prewarmOverlay == null) return;
        final android.view.View v = prewarmOverlay;
        prewarmOverlay = null;
        if (progressHandler != null && progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
        v.animate().alpha(0f).setDuration(350).withEndAction(() -> {
            if (v.getParent() != null) {
                ((android.view.ViewGroup) v.getParent()).removeView(v);
            }
        });
    }
}
