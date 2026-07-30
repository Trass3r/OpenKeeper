/*
 * Copyright (c) 2014-2026 OpenKeeper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package toniarts.openkeeper.android;

import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import com.jme3.app.AndroidHarness;
import com.jme3.system.AppSettings;
import java.io.File;
import toniarts.openkeeper.Main;

/**
 * Android lifecycle and surface host for OpenKeeper.
 */
public final class OpenKeeperAndroidActivity extends AndroidHarness {

    private static final int RENDER_WIDTH = 1920;
    private static final int RENDER_HEIGHT = 886;

    public OpenKeeperAndroidActivity() {
        appClass = Main.class.getName();
        eglBitsPerPixel = 24;
        eglDepthBits = 24;
        eglSamples = 0;
        frameRate = 60;
        audioRendererType = AppSettings.ANDROID_OPENAL_SOFT;
        joystickEventsEnabled = true;
        keyEventsEnabled = true;
        mouseEventsEnabled = true;
        screenFullScreen = true;
        screenShowTitle = false;
        exitDialogTitle = "Exit OpenKeeper?";
        exitDialogMessage = "Return to Android or exit OpenKeeper completely.";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        File privateFiles = getFilesDir();
        File externalFiles = getExternalFilesDir(null);
        System.setProperty("user.home", privateFiles.getAbsolutePath());
        File storage = externalFiles != null ? externalFiles : privateFiles;
        Main.configureEmbedded(
                new File(storage, "DK2").getAbsolutePath(),
                new File(storage, "Converted").getAbsolutePath());
        super.onCreate(savedInstanceState);
        if (view != null) {
            // DK2's Nifty UI uses fixed pixel dimensions. Rendering at the
            // phone's native 3120x1440 makes controls physically tiny and
            // needlessly expensive; Android scales this buffer fullscreen.
            view.getHolder().setFixedSize(RENDER_WIDTH, RENDER_HEIGHT);
        }
        enterImmersiveMode();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
        }
    }

    private void enterImmersiveMode() {
        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }
}
