/*
 * Copyright (c) 2014-2026 OpenKeeper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package toniarts.openkeeper.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * A lightweight analog joystick drawn above the Android game surface.
 */
@SuppressLint("ViewConstructor")
final class VirtualJoystickView extends View {

    @FunctionalInterface
    interface Listener {

        void onMove(float horizontal, float vertical);
    }

    private static final float DEAD_ZONE = 0.16f;
    private static final int INVALID_POINTER_ID = -1;

    private final Paint baseFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baseStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String label;

    private Listener listener;
    private int activePointerId = INVALID_POINTER_ID;
    private float centerX;
    private float centerY;
    private float baseRadius;
    private float knobRadius;
    private float knobX;
    private float knobY;
    private float horizontal;
    private float vertical;

    VirtualJoystickView(Context context, String label, int accentColor,
            int knobColor) {
        super(context);
        this.label = label;
        setContentDescription(label + " virtual joystick");
        setFocusable(true);

        baseFillPaint.setColor(Color.argb(94, 16, 12, 8));
        baseFillPaint.setStyle(Paint.Style.FILL);

        baseStrokePaint.setColor(withAlpha(accentColor, 190));
        baseStrokePaint.setStyle(Paint.Style.STROKE);
        baseStrokePaint.setStrokeWidth(dpToPixels(2f));

        guidePaint.setColor(withAlpha(accentColor, 95));
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(dpToPixels(1f));

        knobPaint.setColor(withAlpha(knobColor, 205));
        knobPaint.setStyle(Paint.Style.FILL);

        labelPaint.setColor(withAlpha(accentColor, 225));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dpToPixels(11f));
        labelPaint.setFakeBoldText(true);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    boolean beginInput(int pointerId, float rawX, float rawY) {
        if (activePointerId != INVALID_POINTER_ID) {
            return false;
        }
        activePointerId = pointerId;
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        updateFromScreen(rawX, rawY);
        return true;
    }

    boolean updateInput(int pointerId, float rawX, float rawY) {
        if (activePointerId != pointerId) {
            return false;
        }
        updateFromScreen(rawX, rawY);
        return true;
    }

    boolean endInput(int pointerId, boolean performClick) {
        if (activePointerId != pointerId) {
            return false;
        }
        cancelInput();
        if (performClick) {
            performClick();
        }
        return true;
    }

    boolean ownsPointer(int pointerId) {
        return activePointerId == pointerId;
    }

    void setControlOpacity(float opacity) {
        setAlpha(Math.max(0.2f, Math.min(1f, opacity)));
    }

    void cancelInput() {
        activePointerId = INVALID_POINTER_ID;
        setOutput(0f, 0f);
        knobX = centerX;
        knobY = centerY;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        centerX = width / 2f;
        centerY = height / 2f;
        baseRadius = Math.min(width, height) * 0.36f;
        knobRadius = baseRadius * 0.36f;
        knobX = centerX;
        knobY = centerY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawCircle(centerX, centerY, baseRadius, baseFillPaint);
        canvas.drawCircle(centerX, centerY, baseRadius, baseStrokePaint);
        canvas.drawCircle(centerX, centerY, baseRadius * DEAD_ZONE, guidePaint);
        canvas.drawLine(centerX - baseRadius * 0.72f, centerY,
                centerX + baseRadius * 0.72f, centerY, guidePaint);
        canvas.drawLine(centerX, centerY - baseRadius * 0.72f,
                centerX, centerY + baseRadius * 0.72f, guidePaint);
        canvas.drawCircle(knobX, knobY, knobRadius, knobPaint);
        canvas.drawCircle(knobX, knobY, knobRadius, baseStrokePaint);
        canvas.drawText(label, centerX, dpToPixels(16f), labelPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (event.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER) {
                    return false;
                }
                return beginInput(event.getPointerId(0), event.getRawX(0),
                        event.getRawY(0));
            case MotionEvent.ACTION_MOVE:
                int pointerIndex = event.findPointerIndex(activePointerId);
                if (pointerIndex >= 0) {
                    updateInput(activePointerId, event.getRawX(pointerIndex),
                            event.getRawY(pointerIndex));
                }
                return activePointerId != INVALID_POINTER_ID;
            case MotionEvent.ACTION_POINTER_UP:
                endInput(event.getPointerId(event.getActionIndex()), false);
                return true;
            case MotionEvent.ACTION_UP:
                int pointerId = event.getPointerId(event.getActionIndex());
                if (ownsPointer(pointerId)) {
                    cancelInput();
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelInput();
                return true;
            default:
                return activePointerId != INVALID_POINTER_ID;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void updateFromTouch(float x, float y) {
        float deltaX = x - centerX;
        float deltaY = y - centerY;
        float distance = (float) Math.hypot(deltaX, deltaY);
        float clampedDistance = Math.min(distance, baseRadius);
        float directionX = distance == 0f ? 0f : deltaX / distance;
        float directionY = distance == 0f ? 0f : deltaY / distance;

        knobX = centerX + directionX * clampedDistance;
        knobY = centerY + directionY * clampedDistance;

        float magnitude = clampedDistance / baseRadius;
        if (magnitude <= DEAD_ZONE) {
            setOutput(0f, 0f);
        } else {
            float scaledMagnitude = (magnitude - DEAD_ZONE) / (1f - DEAD_ZONE);
            setOutput(directionX * scaledMagnitude,
                    -directionY * scaledMagnitude);
        }
        invalidate();
    }

    private void updateFromScreen(float rawX, float rawY) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        updateFromTouch(rawX - location[0], rawY - location[1]);
    }

    private void setOutput(float horizontal, float vertical) {
        if (this.horizontal == horizontal && this.vertical == vertical) {
            return;
        }
        this.horizontal = horizontal;
        this.vertical = vertical;
        if (listener != null) {
            listener.onMove(horizontal, vertical);
        }
    }

    private float dpToPixels(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color),
                Color.blue(color));
    }
}
