/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.best.deskclock.utils;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.VisibleForTesting;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.best.deskclock.R;
import com.best.deskclock.alarms.AlarmStateManager;
import com.best.deskclock.provider.AlarmInstance;
import com.best.deskclock.widget.toast.ToastManager;

import java.util.Calendar;
import java.util.Locale;

/**
 * Static utility methods for Alarms.
 */
public class AlarmUtils {

    /**
     * Intent action sent when the alarm has been either created or updated in the Clock app.
     * <p>
     * This action will display the next alarm of this app only in the clock tab and screensaver.
     */
    public static final String ACTION_NEXT_ALARM_CHANGED_BY_CLOCK = "com.best.deskclock.NEXT_ALARM_CHANGED_BY_CLOCK";

    /**
     * Hide system bars when alarm goes off or timer expires.
     */
    public static void hideSystemBarsOfTriggeredAlarms(Window window, View view) {
        if (SdkUtils.isAtLeastAndroid10()) {
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, view);
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            view.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    /**
     * @return The text of the next alarm.
     */
    public static String getNextAlarm(Context context) {
        AlarmInstance instance = AlarmStateManager.getNextFiringAlarm(context);
        if (instance != null) {
            Calendar alarmCalendar = Calendar.getInstance();
            long alarmTime = instance.getAlarmTime().getTimeInMillis();
            alarmCalendar.setTimeInMillis(alarmTime);
            return getFormattedTime(context, alarmCalendar);
        }

        return null;
    }

    /**
     * @return The next alarm title.
     */
    public static String getNextAlarmTitle(Context context) {
        AlarmInstance instance = AlarmStateManager.getNextFiringAlarm(context);
        if (instance != null) {
            return instance.mLabel.isEmpty() ? "" : instance.mLabel;
        }
        return null;
    }

    /**
     * Clock views can call this to refresh their alarm to the next upcoming value.
     */
    public static void refreshAlarm(Context context, View clock) {
        final TextView nextAlarmIconView = clock.findViewById(R.id.nextAlarmIcon);
        final TextView nextAlarmView = clock.findViewById(R.id.nextAlarm);
        if (nextAlarmView == null) {
            return;
        }

        AlarmInstance instance = AlarmStateManager.getNextFiringAlarm(context);
        if (instance == null) {
            nextAlarmIconView.setVisibility(View.GONE);
            nextAlarmView.setVisibility(View.GONE);
            return;
        }

        Calendar alarmCalendar = Calendar.getInstance();
        long alarmTime = instance.getAlarmTime().getTimeInMillis();
        alarmCalendar.setTimeInMillis(alarmTime);
        String alarmFormattedTime = getFormattedTime(context, alarmCalendar);

        if (TextUtils.isEmpty(alarmFormattedTime)) {
            nextAlarmView.setVisibility(View.GONE);
            nextAlarmIconView.setVisibility(View.GONE);
        } else {
            String description = context.getString(R.string.next_alarm_description, alarmFormattedTime);
            nextAlarmView.setText(alarmFormattedTime);
            nextAlarmView.setContentDescription(description);
            nextAlarmView.setVisibility(View.VISIBLE);
            nextAlarmIconView.setVisibility(View.VISIBLE);
            nextAlarmIconView.setContentDescription(description);
        }
    }

    public static String getAlarmText(Context context, AlarmInstance instance, boolean includeLabel) {
        String alarmTimeStr = getFormattedTime(context, instance.getAlarmTime());
        return (instance.mLabel.isEmpty() || !includeLabel)
                ? alarmTimeStr
                : alarmTimeStr + " - " + instance.mLabel;
    }

    public static String getFormattedTime(Context context, Calendar time) {
        final String skeleton = DateFormat.is24HourFormat(context) ? "EHm" : "Ehma";
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        return (String) DateFormat.format(pattern, time);
    }

    public static String getFormattedTime(Context context, long timeInMillis) {
        final Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timeInMillis);
        return getFormattedTime(context, c);
    }

    /**
     * format "Alarm set for 2 days, 7 hours, and 53 minutes from now."
     */
    @VisibleForTesting
    static String formatElapsedTimeUntilAlarm(Context context, long delta) {
        // If the alarm will ring within 60 seconds, just report "less than a minute."
        final String[] formats = context.getResources().getStringArray(R.array.alarm_set);
        if (delta < DateUtils.MINUTE_IN_MILLIS) {
            return formats[0];
        }

        // Otherwise, format the remaining time until the alarm rings.

        // Round delta upwards to the nearest whole minute. (e.g. 7m 58s -> 8m)
        final long remainder = delta % DateUtils.MINUTE_IN_MILLIS;
        delta += remainder == 0 ? 0 : (DateUtils.MINUTE_IN_MILLIS - remainder);

        long days = delta / (1000 * 60 * 60 * 24);
        long remainingMillis = delta % (1000 * 60 * 60 * 24);

        long hours = remainingMillis / (1000 * 60 * 60);
        remainingMillis %= (1000 * 60 * 60);

        long minutes = remainingMillis / (1000 * 60);

        String daySeq = Utils.getNumberFormattedQuantityString(context, R.plurals.days, (int) days);
        String hourSeq = Utils.getNumberFormattedQuantityString(context, R.plurals.hours, (int) hours);
        String minSeq = Utils.getNumberFormattedQuantityString(context, R.plurals.minutes, (int) minutes);

        final boolean showDays = days > 0;
        final boolean showHours = hours > 0;
        final boolean showMinutes = minutes > 0;

        // Compute the index of the most appropriate time format based on the time delta.
        final int index = (showDays ? 1 : 0) | (showHours ? 2 : 0) | (showMinutes ? 4 : 0);

        return String.format(formats[index], daySeq, hourSeq, minSeq);
    }

    public static void popAlarmSetToast(Context context, long alarmTime) {
        final long alarmTimeDelta = alarmTime - System.currentTimeMillis();
        final String text = formatElapsedTimeUntilAlarm(context, alarmTimeDelta);
        Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
        ToastManager.setToast(toast);
        toast.show();
    }

    public static void popAlarmSetSnackbar(View snackbarAnchor, long alarmTime) {
        final long alarmTimeDelta = alarmTime - System.currentTimeMillis();
        final String text = formatElapsedTimeUntilAlarm(
                snackbarAnchor.getContext(), alarmTimeDelta);
        /*
        SnackbarManager.show(Snackbar.make(snackbarAnchor, text, Snackbar.LENGTH_SHORT));
        snackbarAnchor.announceForAccessibility(text);*/
        Toast.makeText(snackbarAnchor.getContext(), text, Toast.LENGTH_SHORT).show();
    }

    /**
     * @return {@code true} if the device has a back flash. {@code false} otherwise.
     */
    public static boolean hasBackFlash(Context context) {
        return false;
    }
}
