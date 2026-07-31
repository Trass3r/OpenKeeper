/*
 * Copyright (c) 2014-2026 OpenKeeper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package toniarts.openkeeper.android;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import com.jme3.app.AndroidHarness;
import com.jme3.input.TouchInput;
import com.jme3.system.AppSettings;
import java.io.File;
import java.util.Locale;
import toniarts.openkeeper.Main;
import toniarts.openkeeper.view.PlayerCameraState;
import toniarts.openkeeper.view.PlayerInteractionState;

/**
 * Android lifecycle and surface host for OpenKeeper.
 */
public final class OpenKeeperAndroidActivity extends AndroidHarness {

    private static final String SPEN_LOG_TAG = "OpenKeeperSpen";
    private static final String CONTROLS_LOG_TAG = "OpenKeeperControls";
    private static final int RENDER_WIDTH = 1920;
    private static final int RENDER_HEIGHT = 886;
    private static final int STYLUS_SECONDARY_BUTTONS
            = MotionEvent.BUTTON_STYLUS_PRIMARY
            | MotionEvent.BUTTON_STYLUS_SECONDARY
            | MotionEvent.BUTTON_SECONDARY;
    private static final long PALM_REJECTION_GRACE_MS = 200L;
    private static final long TWO_FINGER_TAP_TIMEOUT_MS = 500L;

    private boolean stylusInRange;
    private boolean stylusTouching;
    private boolean stylusSecondaryActive;
    private boolean stylusSecondaryTouch;
    private boolean suppressedFingerGesture;
    private long lastStylusMoveLog;
    private long lastStylusEventTime;
    private MotionEvent pendingFingerDown;
    private boolean multiFingerGesture;
    private boolean multiFingerTapCandidate;
    private float fingerDownX;
    private float fingerDownY;
    private float multiFingerStartX;
    private float multiFingerStartY;
    private float multiFingerStartSpan;
    private float multiFingerTapX;
    private float multiFingerTapY;
    private int touchSlop;
    private AndroidControlSettings controlSettings;
    private VirtualJoystickView movementJoystick;
    private VirtualJoystickView viewJoystick;
    private ImageButton controlSettingsButton;
    private boolean virtualJoysticksVisible;
    private boolean virtualJoystickGesture;
    private boolean controlSettingsButtonGesture;

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
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        controlSettings = AndroidControlSettings.load(this);
        Main.setEmbeddedCameraControlsListener(
                this::setVirtualJoysticksVisible);
        File privateFiles = getFilesDir();
        File externalFiles = getExternalFilesDir(null);
        System.setProperty("user.home", privateFiles.getAbsolutePath());
        System.setProperty("openkeeper.display.width",
                Integer.toString(RENDER_WIDTH));
        System.setProperty("openkeeper.display.height",
                Integer.toString(RENDER_HEIGHT));
        System.setProperty("openkeeper.display.bitDepth",
                Integer.toString(eglBitsPerPixel));
        System.setProperty("openkeeper.display.refreshRate",
                Integer.toString(frameRate));
        System.setProperty("openkeeper.display.renderer", "OpenGL ES");
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
        setupVirtualControls();
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
        resetFingerGesture();
        virtualJoystickGesture = false;
        controlSettingsButtonGesture = false;
        if (movementJoystick != null) {
            movementJoystick.cancelInput();
        }
        if (viewJoystick != null) {
            viewJoystick.cancelInput();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Main.setEmbeddedCameraControlsListener(null);
        virtualJoystickGesture = false;
        controlSettingsButtonGesture = false;
        if (movementJoystick != null) {
            movementJoystick.cancelInput();
        }
        if (viewJoystick != null) {
            viewJoystick.cancelInput();
        }
        super.onDestroy();
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
            resetFingerGesture();
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
            if (action == MotionEvent.ACTION_DOWN
                    && isInsideView(event, 0, controlSettingsButton)) {
                controlSettingsButtonGesture = true;
                resetFingerGesture();
                boolean handled = super.dispatchTouchEvent(event);
                if (action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL) {
                    controlSettingsButtonGesture = false;
                }
                return handled;
            }
            if (controlSettingsButtonGesture) {
                boolean handled = super.dispatchTouchEvent(event);
                if (action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL) {
                    controlSettingsButtonGesture = false;
                }
                return handled;
            }
            if (action == MotionEvent.ACTION_DOWN) {
                virtualJoystickGesture = beginVirtualJoystickPointer(event,
                        event.getActionIndex());
                if (virtualJoystickGesture) {
                    resetFingerGesture();
                    return true;
                }
            }
            if (virtualJoystickGesture) {
                dispatchVirtualJoystickEvent(event);
                return true;
            }
            return dispatchFingerTouchEvent(event);
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
                        postSecondaryPointer(event, true);
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

    private boolean dispatchFingerTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                resetFingerGesture();
                pendingFingerDown = MotionEvent.obtain(event);
                fingerDownX = event.getX();
                fingerDownY = event.getY();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (pendingFingerDown != null) {
                    beginMultiFingerGesture(event);
                    boolean downHandled = dispatchPendingFingerDown(false);
                    return dispatchTouchEvent(event, false) || downHandled;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (multiFingerGesture) {
                    updateMultiFingerGesture(event);
                    return dispatchTouchEvent(event, false);
                }
                if (pendingFingerDown != null) {
                    float deltaX = event.getX() - fingerDownX;
                    float deltaY = event.getY() - fingerDownY;
                    if (deltaX * deltaX + deltaY * deltaY
                            >= touchSlop * touchSlop) {
                        boolean downHandled = dispatchPendingFingerDown(true);
                        return super.dispatchTouchEvent(event) || downHandled;
                    }
                    return true;
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (multiFingerGesture) {
                    updateMultiFingerGesture(event);
                    return dispatchTouchEvent(event, false);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (multiFingerGesture) {
                    boolean handled = dispatchTouchEvent(event, false);
                    boolean performSecondary = multiFingerTapCandidate
                            && event.getEventTime() - event.getDownTime()
                            <= TWO_FINGER_TAP_TIMEOUT_MS;
                    float secondaryX = multiFingerTapX;
                    float secondaryY = multiFingerTapY;
                    resetFingerGesture();
                    if (performSecondary) {
                        postSecondaryPointer(secondaryX, secondaryY, false);
                    }
                    return handled;
                }
                if (pendingFingerDown != null) {
                    boolean downHandled = dispatchPendingFingerDown(true);
                    boolean upHandled = super.dispatchTouchEvent(event);
                    resetFingerGesture();
                    return upHandled || downHandled;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (multiFingerGesture) {
                    boolean handled = dispatchTouchEvent(event, false);
                    resetFingerGesture();
                    return handled;
                }
                if (pendingFingerDown != null) {
                    resetFingerGesture();
                    return true;
                }
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    private void beginMultiFingerGesture(MotionEvent event) {
        multiFingerGesture = true;
        multiFingerTapCandidate = event.getPointerCount() == 2;
        multiFingerStartX = getCentroidX(event);
        multiFingerStartY = getCentroidY(event);
        multiFingerStartSpan = getPointerSpan(event);
        multiFingerTapX = multiFingerStartX;
        multiFingerTapY = multiFingerStartY;
    }

    private void updateMultiFingerGesture(MotionEvent event) {
        if (event.getPointerCount() != 2) {
            multiFingerTapCandidate = false;
            return;
        }

        float centroidX = getCentroidX(event);
        float centroidY = getCentroidY(event);
        float span = getPointerSpan(event);
        multiFingerTapX = centroidX;
        multiFingerTapY = centroidY;
        if (Math.hypot(centroidX - multiFingerStartX,
                centroidY - multiFingerStartY) >= touchSlop
                || Math.abs(span - multiFingerStartSpan) >= touchSlop) {
            multiFingerTapCandidate = false;
        }
    }

    private boolean dispatchPendingFingerDown(boolean simulateMouse) {
        if (pendingFingerDown == null) {
            return false;
        }
        try {
            return dispatchTouchEvent(pendingFingerDown, simulateMouse);
        } finally {
            pendingFingerDown.recycle();
            pendingFingerDown = null;
        }
    }

    private boolean dispatchTouchEvent(MotionEvent event, boolean simulateMouse) {
        TouchInput touchInput = getJmeApplication() != null
                && getJmeApplication().getContext() != null
                ? getJmeApplication().getContext().getTouchInput()
                : null;
        if (touchInput == null || touchInput.isSimulateMouse() == simulateMouse) {
            return super.dispatchTouchEvent(event);
        }

        boolean previousValue = touchInput.isSimulateMouse();
        touchInput.setSimulateMouse(simulateMouse);
        try {
            return super.dispatchTouchEvent(event);
        } finally {
            touchInput.setSimulateMouse(previousValue);
        }
    }

    private void resetFingerGesture() {
        if (pendingFingerDown != null) {
            pendingFingerDown.recycle();
            pendingFingerDown = null;
        }
        multiFingerGesture = false;
        multiFingerTapCandidate = false;
    }

    private static float getCentroidX(MotionEvent event) {
        float x = 0f;
        for (int i = 0; i < event.getPointerCount(); i++) {
            x += event.getX(i);
        }
        return x / event.getPointerCount();
    }

    private static float getCentroidY(MotionEvent event) {
        float y = 0f;
        for (int i = 0; i < event.getPointerCount(); i++) {
            y += event.getY(i);
        }
        return y / event.getPointerCount();
    }

    private static float getPointerSpan(MotionEvent event) {
        if (event.getPointerCount() < 2) {
            return 0f;
        }
        return (float) Math.hypot(event.getX(1) - event.getX(0),
                event.getY(1) - event.getY(0));
    }

    private void setupVirtualControls() {
        movementJoystick = new VirtualJoystickView(this, "MOVE",
                Color.rgb(255, 199, 82), Color.rgb(171, 83, 24));
        movementJoystick.setVisibility(View.GONE);
        movementJoystick.setListener(this::postVirtualCameraMove);
        addContentView(movementJoystick, createJoystickLayout(Gravity.END));

        viewJoystick = new VirtualJoystickView(this, "VIEW",
                Color.rgb(112, 205, 255), Color.rgb(33, 112, 166));
        viewJoystick.setVisibility(View.GONE);
        viewJoystick.setListener(this::postVirtualCameraView);
        addContentView(viewJoystick, createJoystickLayout(Gravity.START));

        controlSettingsButton = new ImageButton(this);
        controlSettingsButton.setVisibility(View.GONE);
        controlSettingsButton.setContentDescription(
                "Open Android camera control settings");
        controlSettingsButton.setImageResource(
                android.R.drawable.ic_menu_preferences);
        controlSettingsButton.setColorFilter(Color.WHITE);
        controlSettingsButton.setPadding(dpToPixels(10), dpToPixels(10),
                dpToPixels(10), dpToPixels(10));
        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setShape(GradientDrawable.OVAL);
        buttonBackground.setColor(Color.argb(170, 20, 16, 12));
        buttonBackground.setStroke(dpToPixels(1),
                Color.argb(210, 255, 199, 82));
        controlSettingsButton.setBackground(buttonBackground);
        controlSettingsButton.setOnClickListener(view -> {
            cancelVirtualJoystickInput();
            AndroidControlSettings.show(this, controlSettings,
                    this::applyControlSettings);
        });

        int buttonSize = dpToPixels(48);
        FrameLayout.LayoutParams buttonLayout = new FrameLayout.LayoutParams(
                buttonSize, buttonSize, Gravity.END | Gravity.TOP);
        buttonLayout.setMarginEnd(dpToPixels(16));
        buttonLayout.topMargin = dpToPixels(14);
        addContentView(controlSettingsButton, buttonLayout);

        updateControlViews();
    }

    private FrameLayout.LayoutParams createJoystickLayout(
            int horizontalGravity) {
        int size = dpToPixels(controlSettings.getJoystickSize());
        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(
                size, size, horizontalGravity | Gravity.BOTTOM);
        if (horizontalGravity == Gravity.START) {
            layout.setMarginStart(dpToPixels(18));
        } else {
            layout.setMarginEnd(dpToPixels(18));
        }
        layout.bottomMargin = dpToPixels(84);
        return layout;
    }

    private void applyControlSettings(AndroidControlSettings settings) {
        controlSettings = settings;
        controlSettings.save(this);
        updateControlViews();
        postVirtualControlSensitivity();
    }

    private void updateControlViews() {
        if (movementJoystick == null || viewJoystick == null
                || controlSettingsButton == null) {
            return;
        }

        int movementSide = controlSettings.isSidesSwapped()
                ? Gravity.START : Gravity.END;
        int viewSide = controlSettings.isSidesSwapped()
                ? Gravity.END : Gravity.START;
        movementJoystick.setLayoutParams(createJoystickLayout(movementSide));
        viewJoystick.setLayoutParams(createJoystickLayout(viewSide));
        movementJoystick.setControlOpacity(
                controlSettings.getJoystickOpacity());
        viewJoystick.setControlOpacity(controlSettings.getJoystickOpacity());

        movementJoystick.setVisibility(virtualJoysticksVisible
                ? View.VISIBLE : View.GONE);
        boolean showView = virtualJoysticksVisible
                && controlSettings.isViewVisible();
        viewJoystick.setVisibility(showView ? View.VISIBLE : View.GONE);
        controlSettingsButton.setVisibility(virtualJoysticksVisible
                ? View.VISIBLE : View.GONE);
        if (!showView) {
            viewJoystick.cancelInput();
        }
    }

    private void setVirtualJoysticksVisible(boolean visible) {
        runOnUiThread(() -> {
            virtualJoysticksVisible = visible;
            if (!visible) {
                cancelVirtualJoystickInput();
                controlSettingsButtonGesture = false;
            }
            updateControlViews();
            if (visible) {
                postVirtualControlSensitivity();
            }
        });
    }

    private void dispatchVirtualJoystickEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_POINTER_DOWN:
                beginVirtualJoystickPointer(event, event.getActionIndex());
                break;
            case MotionEvent.ACTION_MOVE:
                updateVirtualJoystickPointers(event);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                endVirtualJoystickPointer(event, event.getActionIndex(),
                        false);
                break;
            case MotionEvent.ACTION_UP:
                endVirtualJoystickPointer(event, event.getActionIndex(),
                        true);
                virtualJoystickGesture = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelVirtualJoystickInput();
                break;
            default:
                break;
        }
    }

    private boolean beginVirtualJoystickPointer(MotionEvent event,
            int pointerIndex) {
        if (pointerIndex < 0 || pointerIndex >= event.getPointerCount()
                || event.getToolType(pointerIndex)
                != MotionEvent.TOOL_TYPE_FINGER) {
            return false;
        }

        VirtualJoystickView joystick = findJoystickAt(event, pointerIndex);
        if (joystick == null) {
            return false;
        }

        int pointerId = event.getPointerId(pointerIndex);
        boolean accepted = joystick.beginInput(pointerId,
                event.getRawX(pointerIndex), event.getRawY(pointerIndex));
        if (accepted) {
            Log.d(CONTROLS_LOG_TAG, String.format(Locale.ROOT,
                    "%s pointer %d down", joystick == movementJoystick
                            ? "MOVE" : "VIEW", pointerId));
        }
        return accepted;
    }

    private void updateVirtualJoystickPointers(MotionEvent event) {
        for (int i = 0; i < event.getPointerCount(); i++) {
            int pointerId = event.getPointerId(i);
            float rawX = event.getRawX(i);
            float rawY = event.getRawY(i);
            movementJoystick.updateInput(pointerId, rawX, rawY);
            viewJoystick.updateInput(pointerId, rawX, rawY);
        }
    }

    private void endVirtualJoystickPointer(MotionEvent event,
            int pointerIndex, boolean performClick) {
        if (pointerIndex < 0 || pointerIndex >= event.getPointerCount()) {
            return;
        }
        int pointerId = event.getPointerId(pointerIndex);
        if (movementJoystick.endInput(pointerId, performClick)) {
            Log.d(CONTROLS_LOG_TAG, String.format(Locale.ROOT,
                    "MOVE pointer %d up", pointerId));
        }
        if (viewJoystick.endInput(pointerId, performClick)) {
            Log.d(CONTROLS_LOG_TAG, String.format(Locale.ROOT,
                    "VIEW pointer %d up", pointerId));
        }
    }

    private void cancelVirtualJoystickInput() {
        virtualJoystickGesture = false;
        if (movementJoystick != null) {
            movementJoystick.cancelInput();
        }
        if (viewJoystick != null) {
            viewJoystick.cancelInput();
        }
    }

    private VirtualJoystickView findJoystickAt(MotionEvent event,
            int pointerIndex) {
        if (isInsideView(event, pointerIndex, movementJoystick)) {
            return movementJoystick;
        }
        if (isInsideView(event, pointerIndex, viewJoystick)) {
            return viewJoystick;
        }
        return null;
    }

    private static boolean isInsideView(MotionEvent event, int pointerIndex,
            View target) {
        if (target == null || target.getVisibility() != View.VISIBLE
                || pointerIndex < 0
                || pointerIndex >= event.getPointerCount()) {
            return false;
        }

        int[] location = new int[2];
        target.getLocationOnScreen(location);
        float x = event.getRawX(pointerIndex);
        float y = event.getRawY(pointerIndex);
        return x >= location[0]
                && x < location[0] + target.getWidth()
                && y >= location[1]
                && y < location[1] + target.getHeight();
    }

    private void postVirtualCameraMove(float horizontal, float vertical) {
        if (!(getJmeApplication() instanceof Main main)) {
            return;
        }
        float moveSensitivity = controlSettings.getMoveSensitivity();
        float viewSensitivity = controlSettings.getViewSensitivity();
        main.enqueue(() -> {
            PlayerCameraState cameraState
                    = main.getStateManager().getState(PlayerCameraState.class);
            if (cameraState != null) {
                cameraState.setVirtualControlSensitivity(moveSensitivity,
                        viewSensitivity);
                cameraState.handleVirtualJoystick(horizontal, vertical);
            }
        });
    }

    private void postVirtualCameraView(float horizontal, float vertical) {
        if (!(getJmeApplication() instanceof Main main)) {
            return;
        }
        float moveSensitivity = controlSettings.getMoveSensitivity();
        float viewSensitivity = controlSettings.getViewSensitivity();
        float adjustedVertical = controlSettings.isViewVerticalInverted()
                ? -vertical : vertical;
        main.enqueue(() -> {
            PlayerCameraState cameraState
                    = main.getStateManager().getState(PlayerCameraState.class);
            if (cameraState != null) {
                cameraState.setVirtualControlSensitivity(moveSensitivity,
                        viewSensitivity);
                cameraState.handleVirtualViewJoystick(horizontal,
                        adjustedVertical);
            }
        });
    }

    private void postVirtualControlSensitivity() {
        if (!(getJmeApplication() instanceof Main main)) {
            return;
        }
        float moveSensitivity = controlSettings.getMoveSensitivity();
        float viewSensitivity = controlSettings.getViewSensitivity();
        main.enqueue(() -> {
            PlayerCameraState cameraState
                    = main.getStateManager().getState(PlayerCameraState.class);
            if (cameraState != null) {
                cameraState.setVirtualControlSensitivity(moveSensitivity,
                        viewSensitivity);
            }
        });
    }

    private int dpToPixels(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void beginStylusSecondary(MotionEvent event) {
        if (!stylusSecondaryActive) {
            stylusSecondaryActive = true;
            Log.d(SPEN_LOG_TAG, "S Pen secondary button pressed");
            postSecondaryPointer(event, true);
        }
    }

    private void finishStylusSecondary(MotionEvent event, boolean performAction) {
        if (!stylusSecondaryActive) {
            return;
        }
        if (performAction) {
            postSecondaryPointer(event, false);
        }
        Log.d(SPEN_LOG_TAG, performAction
                ? "S Pen secondary button released"
                : "S Pen secondary gesture cancelled");
        stylusSecondaryActive = false;
    }

    private void postSecondaryPointer(MotionEvent event, boolean pressed) {
        if (event.getPointerCount() == 0) {
            return;
        }

        int pointerIndex = Math.max(0, Math.min(event.getActionIndex(),
                event.getPointerCount() - 1));
        postSecondaryPointer(event.getX(pointerIndex),
                event.getY(pointerIndex), pressed);
    }

    private void postSecondaryPointer(float x, float y, boolean pressed) {
        if (!(getJmeApplication() instanceof Main main) || view == null
                || view.getWidth() == 0 || view.getHeight() == 0) {
            return;
        }

        float normalizedX = x / view.getWidth();
        float normalizedY = 1f - y / view.getHeight();

        main.enqueue(() -> {
            PlayerInteractionState interactionState
                    = main.getStateManager().getState(PlayerInteractionState.class);
            if (interactionState != null) {
                interactionState.handleSecondaryPointer(
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
