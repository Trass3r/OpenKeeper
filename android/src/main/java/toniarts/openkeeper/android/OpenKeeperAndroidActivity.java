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
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import com.jme3.app.AndroidHarness;
import com.jme3.system.AppSettings;
import java.io.File;
import java.util.Locale;
import toniarts.openkeeper.Main;
import toniarts.openkeeper.view.PlayerInteractionState;

/**
 * Android lifecycle and surface host for OpenKeeper.
 */
public final class OpenKeeperAndroidActivity extends AndroidHarness {

    private static final String SPEN_LOG_TAG = "OpenKeeperSpen";
    private static final int RENDER_WIDTH = 1920;
    private static final int RENDER_HEIGHT = 886;
    private static final int STYLUS_SECONDARY_BUTTONS
            = MotionEvent.BUTTON_STYLUS_PRIMARY
            | MotionEvent.BUTTON_STYLUS_SECONDARY
            | MotionEvent.BUTTON_SECONDARY;
    private static final long PALM_REJECTION_GRACE_MS = 200L;

    private boolean stylusInRange;
    private boolean stylusTouching;
    private boolean stylusSecondaryActive;
    private boolean stylusSecondaryTouch;
    private boolean suppressedFingerGesture;
    private long lastStylusMoveLog;
    private long lastStylusEventTime;

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

    @Override
    protected void onPause() {
        stylusInRange = false;
        stylusTouching = false;
        stylusSecondaryActive = false;
        stylusSecondaryTouch = false;
        suppressedFingerGesture = false;
        lastStylusEventTime = 0L;
        super.onPause();
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (isStylusEvent(event)) {
            int action = event.getActionMasked();
            lastStylusEventTime = event.getEventTime();
            logStylusEvent(event);

            switch (action) {
                case MotionEvent.ACTION_HOVER_ENTER:
                case MotionEvent.ACTION_HOVER_MOVE:
                    stylusInRange = true;
                    // Most devices send ACTION_BUTTON_PRESS/RELEASE, but
                    // several Android vendor stacks expose only a changing
                    // buttonState on hover motion. Support both forms.
                    if (hasSecondaryButton(event)) {
                        beginStylusSecondary(event);
                    } else if (action == MotionEvent.ACTION_HOVER_MOVE
                            && stylusSecondaryActive
                            && !stylusSecondaryTouch) {
                        finishStylusSecondary(event, true);
                    }
                    break;
                case MotionEvent.ACTION_HOVER_EXIT:
                    stylusInRange = stylusTouching;
                    if (stylusSecondaryActive && !stylusSecondaryTouch) {
                        finishStylusSecondary(event, false);
                    }
                    break;
                case MotionEvent.ACTION_BUTTON_PRESS:
                    if (isSecondaryButton(event.getActionButton())
                            || hasSecondaryButton(event)) {
                        beginStylusSecondary(event);
                    }
                    break;
                case MotionEvent.ACTION_BUTTON_RELEASE:
                    if (stylusSecondaryActive
                            && (isSecondaryButton(event.getActionButton())
                            || !hasSecondaryButton(event))) {
                        finishStylusSecondary(event, true);
                    }
                    break;
                default:
                    break;
            }
        }
        if (isStylusEvent(event) && !isTouchscreenSource(event)) {
            MotionEvent touchscreenEvent = MotionEvent.obtain(event);
            touchscreenEvent.setSource(
                    event.getSource() | InputDevice.SOURCE_TOUCHSCREEN);
            try {
                return super.dispatchGenericMotionEvent(touchscreenEvent);
            } finally {
                touchscreenEvent.recycle();
            }
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        boolean stylusEvent = isStylusEvent(event);
        if (stylusEvent) {
            lastStylusEventTime = event.getEventTime();
        }

        if (!stylusEvent && isFingerEvent(event)) {
            long timeSinceStylus = event.getEventTime() - lastStylusEventTime;
            boolean recentlyInRange = lastStylusEventTime != 0L
                    && timeSinceStylus >= 0L
                    && timeSinceStylus <= PALM_REJECTION_GRACE_MS;
            if (action == MotionEvent.ACTION_DOWN
                    && (stylusInRange || recentlyInRange)) {
                // Suppress the complete gesture, rather than only its first
                // event, so a resting palm cannot become a partial pinch.
                suppressedFingerGesture = true;
                Log.d(SPEN_LOG_TAG, "Palm/finger gesture suppressed while S Pen is in range");
            }
            if (suppressedFingerGesture) {
                if (action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL) {
                    suppressedFingerGesture = false;
                }
                return true;
            }
        }

        if (stylusEvent) {
            logStylusEvent(event);
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    stylusTouching = true;
                    stylusInRange = true;
                    if (hasSecondaryButton(event)) {
                        stylusSecondaryTouch = true;
                        beginStylusSecondary(event);
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    stylusTouching = true;
                    stylusInRange = true;
                    if (stylusSecondaryTouch) {
                        postStylusSecondary(event, true);
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    stylusTouching = false;
                    stylusInRange = false;
                    if (stylusSecondaryTouch) {
                        stylusSecondaryTouch = false;
                        finishStylusSecondary(event, true);
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    stylusTouching = false;
                    stylusInRange = false;
                    if (stylusSecondaryTouch) {
                        stylusSecondaryTouch = false;
                        finishStylusSecondary(event, false);
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }

        if (stylusEvent && !isTouchscreenSource(event)) {
            // jME's AndroidInputHandler accepts MotionEvents only when their
            // source includes SOURCE_TOUCHSCREEN. Some Samsung/ADB stylus
            // events expose only SOURCE_STYLUS, even though they target the
            // same display surface. Preserve every pen field and add the
            // touchscreen capability bit before normal jME processing.
            MotionEvent touchscreenEvent = MotionEvent.obtain(event);
            touchscreenEvent.setSource(
                    event.getSource() | InputDevice.SOURCE_TOUCHSCREEN);
            try {
                return super.dispatchTouchEvent(touchscreenEvent);
            } finally {
                touchscreenEvent.recycle();
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void beginStylusSecondary(MotionEvent event) {
        if (!stylusSecondaryActive) {
            stylusSecondaryActive = true;
            Log.d(SPEN_LOG_TAG, "S Pen secondary button pressed");
            postStylusSecondary(event, true);
        }
    }

    private void finishStylusSecondary(MotionEvent event, boolean performAction) {
        if (!stylusSecondaryActive) {
            return;
        }
        if (performAction) {
            postStylusSecondary(event, false);
        }
        Log.d(SPEN_LOG_TAG, performAction
                ? "S Pen secondary button released"
                : "S Pen secondary gesture cancelled");
        stylusSecondaryActive = false;
    }

    private void postStylusSecondary(MotionEvent event, boolean pressed) {
        if (!(getJmeApplication() instanceof Main main) || view == null
                || view.getWidth() == 0 || view.getHeight() == 0
                || event.getPointerCount() == 0) {
            return;
        }

        int pointerIndex = Math.max(0, Math.min(event.getActionIndex(),
                event.getPointerCount() - 1));
        float normalizedX = event.getX(pointerIndex) / view.getWidth();
        float normalizedY = 1f - event.getY(pointerIndex) / view.getHeight();

        main.enqueue(() -> {
            PlayerInteractionState interactionState
                    = main.getStateManager().getState(PlayerInteractionState.class);
            if (interactionState != null) {
                interactionState.handleStylusSecondary(
                        normalizedX, normalizedY, pressed);
            }
        });
    }

    private void logStylusEvent(MotionEvent event) {
        int action = event.getActionMasked();
        long now = event.getEventTime();
        if ((action == MotionEvent.ACTION_MOVE
                || action == MotionEvent.ACTION_HOVER_MOVE)
                && now - lastStylusMoveLog < 250L) {
            return;
        }
        lastStylusMoveLog = now;

        if (event.getPointerCount() == 0) {
            return;
        }
        int pointerIndex = Math.max(0, Math.min(event.getActionIndex(),
                event.getPointerCount() - 1));
        Log.d(SPEN_LOG_TAG, String.format(Locale.ROOT,
                "%s tool=%d x=%.1f y=%.1f pressure=%.3f tilt=%.3f orientation=%.3f distance=%.1f buttons=0x%x",
                MotionEvent.actionToString(action),
                event.getToolType(pointerIndex),
                event.getX(pointerIndex),
                event.getY(pointerIndex),
                event.getPressure(pointerIndex),
                event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex),
                event.getOrientation(pointerIndex),
                event.getAxisValue(MotionEvent.AXIS_DISTANCE, pointerIndex),
                event.getButtonState()));
    }

    private static boolean isStylusEvent(MotionEvent event) {
        for (int i = 0; i < event.getPointerCount(); i++) {
            int toolType = event.getToolType(i);
            if (toolType == MotionEvent.TOOL_TYPE_STYLUS
                    || toolType == MotionEvent.TOOL_TYPE_ERASER) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFingerEvent(MotionEvent event) {
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (event.getToolType(i) == MotionEvent.TOOL_TYPE_FINGER) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSecondaryButton(MotionEvent event) {
        return (event.getButtonState() & STYLUS_SECONDARY_BUTTONS) != 0;
    }

    private static boolean isSecondaryButton(int button) {
        return (button & STYLUS_SECONDARY_BUTTONS) != 0;
    }

    private static boolean isTouchscreenSource(MotionEvent event) {
        return (event.getSource() & InputDevice.SOURCE_TOUCHSCREEN)
                == InputDevice.SOURCE_TOUCHSCREEN;
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
