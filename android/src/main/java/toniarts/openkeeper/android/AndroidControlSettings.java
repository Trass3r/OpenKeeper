/*
 * Copyright (c) 2014-2026 OpenKeeper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package toniarts.openkeeper.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

/**
 * Persistent settings for the Android-native camera controls.
 */
final class AndroidControlSettings {

    @FunctionalInterface
    interface Listener {

        void onSettingsChanged(AndroidControlSettings settings);
    }

    private static final String PREFERENCES_NAME = "android_controls";
    private static final String SHOW_VIEW = "show_view";
    private static final String SWAP_SIDES = "swap_sides";
    private static final String INVERT_VIEW_VERTICAL = "invert_view_vertical";
    private static final String JOYSTICK_SIZE = "joystick_size";
    private static final String JOYSTICK_OPACITY = "joystick_opacity";
    private static final String MOVE_SENSITIVITY = "move_sensitivity";
    private static final String VIEW_SENSITIVITY = "view_sensitivity";

    private static final int DEFAULT_JOYSTICK_SIZE = 152;
    private static final int DEFAULT_JOYSTICK_OPACITY = 100;
    private static final int DEFAULT_SENSITIVITY = 100;
    private static final int MIN_JOYSTICK_SIZE = 112;
    private static final int MAX_JOYSTICK_SIZE = 200;
    private static final int MIN_OPACITY = 35;
    private static final int MAX_OPACITY = 100;
    private static final int MIN_SENSITIVITY = 50;
    private static final int MAX_SENSITIVITY = 150;

    private final boolean showView;
    private final boolean swapSides;
    private final boolean invertViewVertical;
    private final int joystickSize;
    private final int joystickOpacity;
    private final int moveSensitivity;
    private final int viewSensitivity;

    private AndroidControlSettings(boolean showView, boolean swapSides,
            boolean invertViewVertical, int joystickSize,
            int joystickOpacity, int moveSensitivity, int viewSensitivity) {
        this.showView = showView;
        this.swapSides = swapSides;
        this.invertViewVertical = invertViewVertical;
        this.joystickSize = clamp(joystickSize, MIN_JOYSTICK_SIZE,
                MAX_JOYSTICK_SIZE);
        this.joystickOpacity = clamp(joystickOpacity, MIN_OPACITY,
                MAX_OPACITY);
        this.moveSensitivity = clamp(moveSensitivity, MIN_SENSITIVITY,
                MAX_SENSITIVITY);
        this.viewSensitivity = clamp(viewSensitivity, MIN_SENSITIVITY,
                MAX_SENSITIVITY);
    }

    static AndroidControlSettings load(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);
        return new AndroidControlSettings(
                preferences.getBoolean(SHOW_VIEW, true),
                preferences.getBoolean(SWAP_SIDES, false),
                preferences.getBoolean(INVERT_VIEW_VERTICAL, false),
                preferences.getInt(JOYSTICK_SIZE, DEFAULT_JOYSTICK_SIZE),
                preferences.getInt(JOYSTICK_OPACITY,
                        DEFAULT_JOYSTICK_OPACITY),
                preferences.getInt(MOVE_SENSITIVITY, DEFAULT_SENSITIVITY),
                preferences.getInt(VIEW_SENSITIVITY, DEFAULT_SENSITIVITY));
    }

    static AndroidControlSettings defaults() {
        return new AndroidControlSettings(true, false, false,
                DEFAULT_JOYSTICK_SIZE, DEFAULT_JOYSTICK_OPACITY,
                DEFAULT_SENSITIVITY, DEFAULT_SENSITIVITY);
    }

    void save(Context context) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(SHOW_VIEW, showView)
                .putBoolean(SWAP_SIDES, swapSides)
                .putBoolean(INVERT_VIEW_VERTICAL, invertViewVertical)
                .putInt(JOYSTICK_SIZE, joystickSize)
                .putInt(JOYSTICK_OPACITY, joystickOpacity)
                .putInt(MOVE_SENSITIVITY, moveSensitivity)
                .putInt(VIEW_SENSITIVITY, viewSensitivity)
                .apply();
    }

    static void show(Activity activity, AndroidControlSettings current,
            Listener listener) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPixels(activity, 24);
        content.setPadding(padding, dpToPixels(activity, 8), padding,
                padding);

        CheckBox showViewSwitch = addCheckBox(content, "Show VIEW joystick",
                current.showView);
        CheckBox swapSidesSwitch = addCheckBox(content,
                "Swap MOVE / VIEW sides", current.swapSides);
        CheckBox invertViewSwitch = addCheckBox(content,
                "Invert VIEW vertical zoom", current.invertViewVertical);
        SliderRow sizeSlider = addSlider(content, "Joystick size",
                MIN_JOYSTICK_SIZE, MAX_JOYSTICK_SIZE, current.joystickSize,
                " dp");
        SliderRow opacitySlider = addSlider(content, "Joystick opacity",
                MIN_OPACITY, MAX_OPACITY, current.joystickOpacity, "%");
        SliderRow moveSlider = addSlider(content, "MOVE speed",
                MIN_SENSITIVITY, MAX_SENSITIVITY,
                current.moveSensitivity, "%");
        SliderRow viewSlider = addSlider(content, "VIEW speed",
                MIN_SENSITIVITY, MAX_SENSITIVITY,
                current.viewSensitivity, "%");

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(activity)
                .setTitle("Android camera controls")
                .setView(scrollView)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Reset", (dialog, which)
                        -> listener.onSettingsChanged(defaults()))
                .setPositiveButton("Apply", (dialog, which) -> {
                    AndroidControlSettings settings
                            = new AndroidControlSettings(
                                    showViewSwitch.isChecked(),
                                    swapSidesSwitch.isChecked(),
                                    invertViewSwitch.isChecked(),
                                    sizeSlider.getValue(),
                                    opacitySlider.getValue(),
                                    moveSlider.getValue(),
                                    viewSlider.getValue());
                    listener.onSettingsChanged(settings);
                })
                .show();
    }

    boolean isViewVisible() {
        return showView;
    }

    boolean isSidesSwapped() {
        return swapSides;
    }

    boolean isViewVerticalInverted() {
        return invertViewVertical;
    }

    int getJoystickSize() {
        return joystickSize;
    }

    float getJoystickOpacity() {
        return joystickOpacity / 100f;
    }

    float getMoveSensitivity() {
        return moveSensitivity / 100f;
    }

    float getViewSensitivity() {
        return viewSensitivity / 100f;
    }

    private static CheckBox addCheckBox(LinearLayout parent, String label,
            boolean checked) {
        CheckBox control = new CheckBox(parent.getContext());
        control.setText(label);
        control.setChecked(checked);
        control.setPadding(0, dpToPixels(parent.getContext(), 4), 0,
                dpToPixels(parent.getContext(), 4));
        parent.addView(control, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return control;
    }

    private static SliderRow addSlider(LinearLayout parent, String label,
            int minimum, int maximum, int value, String suffix) {
        SliderRow row = new SliderRow(parent.getContext(), label, minimum,
                maximum, value, suffix);
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int dpToPixels(Context context, int dp) {
        return Math.round(dp * context.getResources()
                .getDisplayMetrics().density);
    }

    private static final class SliderRow extends LinearLayout {

        private final String label;
        private final String suffix;
        private final TextView valueLabel;
        private final SeekBar seekBar;

        private SliderRow(Context context, String label, int minimum,
                int maximum, int value, String suffix) {
            super(context);
            this.label = label;
            this.suffix = suffix;
            setOrientation(VERTICAL);

            valueLabel = new TextView(context);
            addView(valueLabel, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            seekBar = new SeekBar(context);
            seekBar.setMin(minimum);
            seekBar.setMax(maximum);
            seekBar.setProgress(value);
            seekBar.setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress,
                        boolean fromUser) {
                    updateLabel(progress);
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar bar) {
                }
            });
            addView(seekBar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            updateLabel(value);
        }

        private int getValue() {
            return seekBar.getProgress();
        }

        private void updateLabel(int value) {
            valueLabel.setText(String.format(Locale.ROOT, "%s: %d%s",
                    label, value, suffix));
        }
    }
}
