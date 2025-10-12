/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.best.deskclock;

import static android.media.AudioManager.STREAM_ALARM;
import static com.best.deskclock.AlarmSelectionActivity.ACTION_DISMISS;
import static com.best.deskclock.AlarmSelectionActivity.EXTRA_ACTION;
import static com.best.deskclock.AlarmSelectionActivity.EXTRA_ALARMS;
import static com.best.deskclock.DeskClockApplication.getDefaultSharedPreferences;
import static com.best.deskclock.provider.AlarmInstance.FIRED_STATE;
import static com.best.deskclock.provider.AlarmInstance.SNOOZE_STATE;
import static com.best.deskclock.uidata.UiDataModel.Tab.ALARMS;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.AlarmClock;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

import com.best.deskclock.alarms.AlarmStateManager;
import com.best.deskclock.controller.Controller;
import com.best.deskclock.data.DataModel;
import com.best.deskclock.data.SettingsDAO;
import com.best.deskclock.data.Weekdays;
import com.best.deskclock.events.Events;
import com.best.deskclock.provider.Alarm;
import com.best.deskclock.provider.AlarmInstance;
import com.best.deskclock.uidata.UiDataModel;
import com.best.deskclock.utils.AlarmUtils;
import com.best.deskclock.utils.LogUtils;
import com.best.deskclock.utils.RingtoneUtils;
import com.best.deskclock.utils.Utils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This activity is never visible. It processes all public intents defined by {@link AlarmClock}
 * that apply to alarms and timers. Its definition in AndroidManifest.xml requires callers to hold
 * the com.android.alarm.permission.SET_ALARM permission to complete the requested action.
 */
public class HandleApiCalls extends Activity {

    private static final LogUtils.Logger LOGGER = new LogUtils.Logger("HandleApiCalls");

    private Context mAppContext;
    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mAppContext = getApplicationContext();
        mPrefs = getDefaultSharedPreferences(mAppContext);

        try {
            final Intent intent = getIntent();
            final String action = intent == null ? null : intent.getAction();
            if (action == null) {
                return;
            }
            LOGGER.i("onCreate: " + intent);

            switch (action) {
                case AlarmClock.ACTION_SET_ALARM -> handleSetAlarm(intent);
                case AlarmClock.ACTION_SHOW_ALARMS -> handleShowAlarms();
                case AlarmClock.ACTION_DISMISS_ALARM -> handleDismissAlarm(intent);
                case AlarmClock.ACTION_SNOOZE_ALARM -> handleSnoozeAlarm();
            }
        } catch (Exception e) {
            LOGGER.wtf(e);
        } finally {
            finish();
        }
    }

    private void handleDismissAlarm(Intent intent) {
        // Change to the alarms tab.
        UiDataModel.getUiDataModel().setSelectedTab(ALARMS);

        // Open DeskClock which is now positioned on the alarms tab.
        startActivity(new Intent(mAppContext, DeskClock.class));

        new DismissAlarmAsync(mAppContext, intent, this).execute();
    }

    public static void dismissAlarm(Alarm alarm, Activity activity) {
        final Context context = activity.getApplicationContext();
        final AlarmInstance instance = AlarmInstance.getNextUpcomingInstanceByAlarmId(
                context.getContentResolver(), alarm.id);
        if (instance == null) {
            final String reason = context.getString(com.better.alarmhelper.R.string.no_alarm_scheduled_for_this_time);
            Controller.getController().notifyVoiceFailure(activity, reason);
            LOGGER.i("No alarm instance to dismiss");
            return;
        }

        dismissAlarmInstance(instance, activity);
    }

    public static void dismissAlarmInstance(AlarmInstance instance, Activity activity) {
        Utils.enforceNotMainLooper();

        final Context context = activity.getApplicationContext();
        final Date alarmTime = instance.getAlarmTime().getTime();
        final String time = DateFormat.getTimeFormat(context).format(alarmTime);

        if (instance.mAlarmState == FIRED_STATE || instance.mAlarmState == SNOOZE_STATE) {
            // Always dismiss alarms that are fired or snoozed.
            AlarmStateManager.deleteInstanceAndUpdateParent(context, instance);
        } else if (isAlarmWithin24Hours(instance)) {
            // Upcoming alarms are always predismissed.
            AlarmStateManager.setPreDismissState(context, instance);
        } else {
            // Otherwise the alarm cannot be dismissed at this time.
            final String reason = context.getString(
                    com.better.alarmhelper.R.string.alarm_cant_be_dismissed_still_more_than_24_hours_away, time);
            Controller.getController().notifyVoiceFailure(activity, reason);
            LOGGER.i("Can't dismiss alarm more than 24 hours in advance");
        }

        // Log the successful dismissal.
        final String reason = context.getString(com.better.alarmhelper.R.string.alarm_is_dismissed, time);
        Controller.getController().notifyVoiceSuccess(activity, reason);
        LOGGER.i("Alarm dismissed: " + instance);
        Events.sendAlarmEvent(com.better.alarmhelper.R.string.action_dismiss, com.better.alarmhelper.R.string.label_intent);
    }

    private static boolean isAlarmWithin24Hours(AlarmInstance alarmInstance) {
        final Calendar nextAlarmTime = alarmInstance.getAlarmTime();
        final long nextAlarmTimeMillis = nextAlarmTime.getTimeInMillis();
        return nextAlarmTimeMillis - System.currentTimeMillis() <= DateUtils.DAY_IN_MILLIS;
    }

    private static class DismissAlarmAsync {

        private final Context mContext;
        private final Intent mIntent;
        private final Activity mActivity;

        public DismissAlarmAsync(Context context, Intent intent, Activity activity) {
            mContext = context;
            mIntent = intent;
            mActivity = activity;
        }

        protected void execute() {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                final ContentResolver cr = mContext.getContentResolver();
                final List<Alarm> alarms = getEnabledAlarms(mContext);
                if (alarms.isEmpty()) {
                    final String reason = mContext.getString(com.better.alarmhelper.R.string.no_scheduled_alarms);
                    Controller.getController().notifyVoiceFailure(mActivity, reason);
                    LOGGER.i("No scheduled alarms");
                    return;
                }

                // remove Alarms in MISSED, DISMISSED, and PREDISMISSED states
                for (Iterator<Alarm> i = alarms.iterator(); i.hasNext();) {
                    final AlarmInstance instance = AlarmInstance.getNextUpcomingInstanceByAlarmId(
                            cr, i.next().id);
                    if (instance == null || instance.mAlarmState > FIRED_STATE) {
                        i.remove();
                    }
                }

                final String searchMode = mIntent.getStringExtra(
                        AlarmClock.EXTRA_ALARM_SEARCH_MODE);
                if (searchMode == null && alarms.size() > 1) {
                    // shows the UI where user picks which alarm they want to DISMISS
                    final Intent pickSelectionIntent = new Intent(mContext,
                            AlarmSelectionActivity.class)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(EXTRA_ACTION, ACTION_DISMISS)
                            .putExtra(EXTRA_ALARMS, alarms.toArray(new Parcelable[0]));
                    mContext.startActivity(pickSelectionIntent);
                    final String voiceMessage = mContext.getString(com.better.alarmhelper.R.string.pick_alarm_to_dismiss);
                    Controller.getController().notifyVoiceSuccess(mActivity, voiceMessage);
                    return;
                }

                // fetch the alarms that are specified by the intent
                final FetchMatchingAlarmsAction fmaa =
                        new FetchMatchingAlarmsAction(mContext, alarms, mIntent, mActivity);
                fmaa.run();
                final List<Alarm> matchingAlarms = fmaa.getMatchingAlarms();

                // If there are multiple matching alarms and it wasn't expected
                // disambiguate what the user meant
                if (!AlarmClock.ALARM_SEARCH_MODE_ALL.equals(searchMode) &&
                        matchingAlarms.size() > 1) {
                    final Intent pickSelectionIntent = new Intent(mContext,
                            AlarmSelectionActivity.class)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(EXTRA_ACTION, ACTION_DISMISS)
                            .putExtra(EXTRA_ALARMS, matchingAlarms.toArray(new Parcelable[0]));
                    mContext.startActivity(pickSelectionIntent);
                    final String voiceMessage = mContext.getString(com.better.alarmhelper.R.string.pick_alarm_to_dismiss);
                    Controller.getController().notifyVoiceSuccess(mActivity, voiceMessage);
                    return;
                }

                // Apply the action to the matching alarms
                for (Alarm alarm : matchingAlarms) {
                    dismissAlarm(alarm, mActivity);
                    LOGGER.i("Alarm dismissed: " + alarm);
                }
            });
        }

        private static List<Alarm> getEnabledAlarms(Context context) {
            final String selection = String.format("%s=?", Alarm.ENABLED);
            final String[] args = { "1" };
            return Alarm.getAlarms(context.getContentResolver(), selection, args);
        }
    }

    private void handleSnoozeAlarm() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            final Context context = getApplicationContext();
            final ContentResolver cr = context.getContentResolver();
            final List<AlarmInstance> alarmInstances = AlarmInstance.getInstancesByState(
                    cr, FIRED_STATE);
            if (alarmInstances.isEmpty()) {
                final String reason = context.getString(com.better.alarmhelper.R.string.no_firing_alarms);
                Controller.getController().notifyVoiceFailure(this, reason);
                LOGGER.i("No firing alarms");
                return;
            }

            for (AlarmInstance firingAlarmInstance : alarmInstances) {
                snoozeAlarm(firingAlarmInstance, context, this);
            }
        });
    }

    static void snoozeAlarm(AlarmInstance alarmInstance, Context context, Activity activity) {
        Utils.enforceNotMainLooper();

        final String time = DateFormat.getTimeFormat(context).format(
                alarmInstance.getAlarmTime().getTime());
        final String reason = context.getString(com.better.alarmhelper.R.string.alarm_is_snoozed, time);
        AlarmStateManager.setSnoozeState(context, alarmInstance, true);

        Controller.getController().notifyVoiceSuccess(activity, reason);
        LOGGER.i("Alarm snoozed: " + alarmInstance);
        Events.sendAlarmEvent(com.better.alarmhelper.R.string.action_snooze, com.better.alarmhelper.R.string.label_intent);
    }

    /***
     * Processes the SET_ALARM intent
     * @param intent Intent passed to the app
     */
    private void handleSetAlarm(Intent intent) {
        // Validate the hour, if one was given.
        int hour = -1;
        if (intent.hasExtra(AlarmClock.EXTRA_HOUR)) {
            hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, hour);
            if (hour < 0 || hour > 23) {
                final int mins = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0);
                final String voiceMessage = getString(com.better.alarmhelper.R.string.invalid_time, hour, mins, " ");
                Controller.getController().notifyVoiceFailure(this, voiceMessage);
                LOGGER.i("Illegal hour: " + hour);
                return;
            }
        }

        // Validate the minute, if one was given.
        final int minutes = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0);
        if (minutes < 0 || minutes > 59) {
            final String voiceMessage = getString(com.better.alarmhelper.R.string.invalid_time, hour, minutes, " ");
            Controller.getController().notifyVoiceFailure(this, voiceMessage);
            LOGGER.i("Illegal minute: " + minutes);
            return;
        }

        final boolean skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false);
        final ContentResolver cr = getContentResolver();

        // If time information was not provided an existing alarm cannot be located and a new one
        // cannot be created so show the UI for creating the alarm from scratch per spec.
        if (hour == -1) {
            // Change to the alarms tab.
            UiDataModel.getUiDataModel().setSelectedTab(ALARMS);

            // Intent has no time or an invalid time, open the alarm creation UI.
            final Intent createAlarm = Alarm.createIntent(this, DeskClock.class, Alarm.INVALID_ID)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(AlarmClockFragment.ALARM_CREATE_NEW_INTENT_EXTRA, true);

            // Open DeskClock which is now positioned on the alarms tab.
            startActivity(createAlarm);
            final String voiceMessage = getString(com.better.alarmhelper.R.string.invalid_time, hour, minutes, " ");
            Controller.getController().notifyVoiceFailure(this, voiceMessage);
            LOGGER.i("Missing alarm time; opening UI");
            return;
        }

        final StringBuilder selection = new StringBuilder();
        final List<String> argsList = new ArrayList<>();
        setSelectionFromIntent(intent, hour, minutes, selection, argsList);

        // Try to locate an existing alarm using the intent data.
        final String[] args = argsList.toArray(new String[0]);
        final List<Alarm> alarms = Alarm.getAlarms(cr, selection.toString(), args);

        final Alarm alarm;
        if (!alarms.isEmpty()) {
            // Enable the first matching alarm.
            alarm = alarms.get(0);
            alarm.enabled = true;
            Alarm.updateAlarm(cr, alarm);

            // Delete all old instances.
            AlarmStateManager.deleteAllInstances(this, alarm.id);

            Events.sendAlarmEvent(com.better.alarmhelper.R.string.action_update, com.better.alarmhelper.R.string.label_intent);
            LOGGER.i("Updated alarm: " + alarm);
        } else {
            // No existing alarm could be located; create one using the intent data.
            alarm = new Alarm();
            updateAlarmFromIntent(alarm, intent);
            applyAlarmSettings(alarm, mAppContext, mPrefs);

            // Save the new alarm.
            Alarm.addAlarm(cr, alarm);

            Events.sendAlarmEvent(com.better.alarmhelper.R.string.action_create, com.better.alarmhelper.R.string.label_intent);
            LOGGER.i("Created new alarm: " + alarm);
        }

        // Schedule the next instance.
        final Calendar now = DataModel.getDataModel().getCalendar();
        final AlarmInstance alarmInstance = alarm.createInstanceAfter(now);
        setupInstance(alarmInstance, skipUi);

        final String time = DateFormat.getTimeFormat(this)
                .format(alarmInstance.getAlarmTime().getTime());
        Controller.getController().notifyVoiceSuccess(this, getString(com.better.alarmhelper.R.string.alarm_is_set, time));
    }

    private void handleShowAlarms() {
        Events.sendAlarmEvent(com.better.alarmhelper.R.string.action_show, com.better.alarmhelper.R.string.label_intent);

        // Open DeskClock positioned on the alarms tab.
        UiDataModel.getUiDataModel().setSelectedTab(ALARMS);
        startActivity(new Intent(this, DeskClock.class));
    }

    private void setupInstance(AlarmInstance instance, boolean skipUi) {
        AlarmInstance.addInstance(this.getContentResolver(), instance);
        AlarmStateManager.registerInstance(this, instance, true);
        AlarmUtils.popAlarmSetToast(this, instance.getAlarmTime().getTimeInMillis());
        if (!skipUi) {
            // Change to the alarms tab.
            UiDataModel.getUiDataModel().setSelectedTab(ALARMS);

            // Open DeskClock which is now positioned on the alarms tab.
            final Intent showAlarm = Alarm.createIntent(this, DeskClock.class, instance.mAlarmId)
                    .putExtra(AlarmClockFragment.SCROLL_TO_ALARM_INTENT_EXTRA, instance.mAlarmId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(showAlarm);
        }
    }

    /**
     * @param alarm  the alarm to be updated
     * @param intent the intent containing new alarm field values to merge into the {@code alarm}
     */
    private static void updateAlarmFromIntent(Alarm alarm, Intent intent) {
        alarm.label = getLabelFromIntent(intent, alarm.label);
        alarm.hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, alarm.hour);
        alarm.minutes = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, alarm.minutes);
        alarm.alert = getAlertFromIntent(intent, alarm.alert);
        alarm.daysOfWeek = getDaysFromIntent(intent, alarm.daysOfWeek);
    }

    /**
     * Applies default application-level settings to the given {@link Alarm} instance.
     *
     * <p>This method sets the alarm's behavior based on user preferences stored in
     * {@link SharedPreferences}. It is typically used when creating a new alarm to ensure
     * consistency with global settings such as vibration, flash, ringtone timeout,
     * auto-deletion after use,the auto silence duration, the snooze duration and
     * the crescendo duration.</p>
     *
     * @param alarm the {@link Alarm} object to which default settings will be applied
     * @param prefs the {@link SharedPreferences} containing the user's default alarm preferences
     */
    private static void applyAlarmSettings(Alarm alarm, Context context, SharedPreferences prefs) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        alarm.enabled = true;
        alarm.vibrate = SettingsDAO.areAlarmVibrationsEnabledByDefault(prefs);
        alarm.flash = SettingsDAO.shouldTurnOnBackFlashForTriggeredAlarm(prefs);
        alarm.deleteAfterUse = SettingsDAO.isOccasionalAlarmDeletedByDefault(prefs);
        alarm.autoSilenceDuration = SettingsDAO.getAlarmTimeout(prefs);
        alarm.snoozeDuration = SettingsDAO.getSnoozeLength(prefs);
        alarm.crescendoDuration = SettingsDAO.getAlarmVolumeCrescendoDuration(prefs);
        alarm.alarmVolume = audioManager.getStreamVolume(STREAM_ALARM);
    }

    private static String getLabelFromIntent(Intent intent, String defaultLabel) {
        final String message = Objects.requireNonNull(
                intent.getExtras()).getString(AlarmClock.EXTRA_MESSAGE, defaultLabel);
        return message == null ? "" : message;
    }

    private static Weekdays getDaysFromIntent(Intent intent, Weekdays defaultWeekdays) {
        if (!intent.hasExtra(AlarmClock.EXTRA_DAYS)) {
            return defaultWeekdays;
        }

        final List<Integer> days = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS);
        if (days != null) {
            final int[] daysArray = new int[days.size()];
            for (int i = 0; i < days.size(); i++) {
                daysArray[i] = days.get(i);
            }
            return Weekdays.fromCalendarDays(daysArray);
        } else {
            // API says to use an ArrayList<Integer> but we allow the user to use a int[] too.
            final int[] daysArray = intent.getIntArrayExtra(AlarmClock.EXTRA_DAYS);
            if (daysArray != null) {
                return Weekdays.fromCalendarDays(daysArray);
            }
        }
        return defaultWeekdays;
    }

    private static Uri getAlertFromIntent(Intent intent, Uri defaultUri) {
        final String alert = intent.getStringExtra(AlarmClock.EXTRA_RINGTONE);
        if (alert == null) {
            return defaultUri;
        } else if (AlarmClock.VALUE_RINGTONE_SILENT.equals(alert) || alert.isEmpty()) {
            return RingtoneUtils.RINGTONE_SILENT;
        }

        return Uri.parse(alert);
    }

    /**
     * Assemble a database where clause to search for an alarm matching the given {@code hour} and
     * {@code minutes} as well as all of the optional information within the {@code intent}
     * including:
     *
     * <ul>
     *     <li>alarm message</li>
     *     <li>repeat days</li>
     *     <li>vibration setting</li>
     *     <li>ringtone uri</li>
     * </ul>
     *
     * @param intent    contains details of the alarm to be located
     * @param hour      the hour of the day of the alarm
     * @param minutes   the minute of the hour of the alarm
     * @param selection an out parameter containing a SQL where clause
     * @param args      an out parameter containing the values to substitute into the {@code selection}
     */
    private void setSelectionFromIntent(
            Intent intent,
            int hour,
            int minutes,
            StringBuilder selection,
            List<String> args) {
        selection.append(Alarm.HOUR).append("=?");
        args.add(String.valueOf(hour));
        selection.append(" AND ").append(Alarm.MINUTES).append("=?");
        args.add(String.valueOf(minutes));

        if (intent.hasExtra(AlarmClock.EXTRA_MESSAGE)) {
            selection.append(" AND ").append(Alarm.LABEL).append("=?");
            args.add(getLabelFromIntent(intent, ""));
        }

        // Days is treated differently than other fields because if days is not specified, it
        // explicitly means "not recurring".
        selection.append(" AND ").append(Alarm.DAYS_OF_WEEK).append("=?");
        args.add(String.valueOf(getDaysFromIntent(intent, Weekdays.NONE).getBits()));

        if (intent.hasExtra(AlarmClock.EXTRA_VIBRATE)) {
            selection.append(" AND ").append(Alarm.VIBRATE).append("=?");
            args.add(intent.getBooleanExtra(AlarmClock.EXTRA_VIBRATE, false) ? "1" : "0");
        }

        if (intent.hasExtra(AlarmClock.EXTRA_RINGTONE)) {
            selection.append(" AND ").append(Alarm.RINGTONE).append("=?");

            // If the intent explicitly specified a NULL ringtone, treat it as the default ringtone.
            final Uri defaultRingtone = DataModel.getDataModel().getDefaultAlarmRingtoneUriFromSettings();
            final Uri ringtone = getAlertFromIntent(intent, defaultRingtone);
            args.add(ringtone.toString());
        }
    }
}
