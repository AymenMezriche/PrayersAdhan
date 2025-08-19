/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.best.deskclock.data;

import static android.content.Context.AUDIO_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.media.AudioManager.FLAG_SHOW_UI;
import static android.media.AudioManager.STREAM_ALARM;
import static android.provider.Settings.ACTION_SOUND_SETTINGS;

import static com.best.deskclock.settings.PreferencesDefaultValues.DARK_THEME;
import static com.best.deskclock.settings.PreferencesDefaultValues.LIGHT_THEME;
import static com.best.deskclock.settings.PreferencesDefaultValues.SYSTEM_THEME;
import static com.best.deskclock.settings.PreferencesKeys.KEY_THEME;
import static com.best.deskclock.utils.Utils.enforceMainLooper;
import static com.best.deskclock.utils.Utils.enforceNotMainLooper;

import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;

import com.best.deskclock.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * All application-wide data is accessible through this singleton.
 */
public final class DataModel {

    /**
     * The single instance of this data model that exists for the life of the application.
     */
    private static final DataModel sDataModel = new DataModel();

    private Handler mHandler;

    private Context mContext;

    /**
     * The model from which alarm data are fetched.
     */
    private AlarmModel mAlarmModel;

    /**
     * The model from which data about settings that silence alarms are fetched.
     */
    private SilentSettingsModel mSilentSettingsModel;

    /**
     * The model from which notification data are fetched.
     */
    private NotificationModel mNotificationModel;

    /**
     * The model from which ringtone data are fetched.
     */
    private RingtoneModel mRingtoneModel;

    private DataModel() {
    }

    public static DataModel getDataModel() {
        return sDataModel;
    }

    /**
     * Initializes the data model with the context and shared preferences to be used.
     */
    public void init(Context context, SharedPreferences prefs) {
        if (mContext != context) {
            mContext = context.getApplicationContext();

            final String themeValue = prefs.getString(KEY_THEME, SYSTEM_THEME);
            switch (themeValue) {
                case SYSTEM_THEME ->
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                case LIGHT_THEME ->
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                case DARK_THEME ->
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            }

            mNotificationModel = new NotificationModel();
            mRingtoneModel = new RingtoneModel(mContext, prefs);
            mAlarmModel = new AlarmModel(prefs, mRingtoneModel);
            mSilentSettingsModel = new SilentSettingsModel(mContext, mNotificationModel);
        }
    }

    /**
     * Convenience for {@code run(runnable, 0)}, i.e. waits indefinitely.
     */
    public void run(Runnable runnable) {
        try {
            run(runnable, 0);
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Updates all timers and the stopwatch after the device has shutdown and restarted.
     */
    public void updateAfterReboot() {
        enforceMainLooper();
    }

    /**
     * Updates all timers and the stopwatch after the device's time has changed.
     */
    public void updateAfterTimeSet() {
        enforceMainLooper();
    }

    /**
     * Posts a runnable to the main thread and blocks until the runnable executes. Used to access
     * the data model from the main thread.
     *
     * @noinspection SynchronizationOnLocalVariableOrMethodParameter
     */
    public void run(Runnable runnable, long waitMillis) throws InterruptedException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
            return;
        }

        final ExecutedRunnable er = new ExecutedRunnable(runnable);
        getHandler().post(er);

        // Wait for the data to arrive, if it has not.
        synchronized (er) {
            if (!er.isExecuted()) {
                er.wait(waitMillis);
            }
        }
    }

    /**
     * @return a handler associated with the main thread
     */
    private synchronized Handler getHandler() {
        if (mHandler == null) {
            mHandler = new Handler(Looper.getMainLooper());
        }
        return mHandler;
    }

    /**
     * @return {@code true} when the application is open in the foreground; {@code false} otherwise
     */
    public boolean isApplicationInForeground() {
        enforceMainLooper();
        return mNotificationModel.isApplicationInForeground();
    }

    /**
     * @param inForeground {@code true} to indicate the application is open in the foreground
     */
    public void setApplicationInForeground(boolean inForeground) {
        enforceMainLooper();

        if (mNotificationModel.isApplicationInForeground() != inForeground) {
            mNotificationModel.setApplicationInForeground(inForeground);

            // Refresh all notifications in response to a change in app open state.
            mSilentSettingsModel.updateSilentState();
        }
    }

    /**
     * Called when the notifications may be stale or absent from the notification manager and must
     * be rebuilt. e.g. after upgrading the application
     */
    public void updateAllNotifications() {
        enforceMainLooper();
    }



    /**
     * @return the uri of the default ringtone from the settings to play for all alarms when no user selection exists
     */
    public Uri getDefaultAlarmRingtoneUriFromSettings() {
        enforceMainLooper();
        return mAlarmModel.getDefaultAlarmRingtoneUriFromSettings();
    }

    /**
     * @return the uri of the ringtone from the settings to play for all alarms
     */
    public Uri getAlarmRingtoneUriFromSettings() {
        enforceMainLooper();
        return mAlarmModel.getAlarmRingtoneUriFromSettings();
    }

    /**
     * @return the title of the ringtone that is played for all alarms
     */
    public String getAlarmRingtoneTitle() {
        enforceMainLooper();
        return mAlarmModel.getAlarmRingtoneTitle();
    }

    /**
     * @param uri the uri of the ringtone from the settings to play for all alarms
     */
    public void setAlarmRingtoneUriFromSettings(Uri uri) {
        enforceMainLooper();
        mAlarmModel.setAlarmRingtoneUriFromSettings(uri);
    }

    /**
     * @param uri the uri of the ringtone of an existing alarm
     */
    public void setSelectedAlarmRingtoneUri(Uri uri) {
        enforceMainLooper();
        mAlarmModel.setSelectedAlarmRingtoneUri(uri);
    }

    /**
     * @return the current time in milliseconds
     */
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * @return milliseconds since boot, including time spent in sleep
     */
    public long elapsedRealtime() {
        return SystemClock.elapsedRealtime();
    }

    /**
     * @return {@code true} if 24 hour time format is selected; {@code false} otherwise
     */
    public boolean is24HourFormat() {
        return DateFormat.is24HourFormat(mContext);
    }

    /**
     * @return a new calendar object initialized to the {@link #currentTimeMillis()}
     */
    public Calendar getCalendar() {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        return calendar;
    }

    /**
     * Ringtone titles are cached because loading them is expensive. This method
     * <strong>must</strong> be called on a background thread and is responsible for priming the
     * cache of ringtone titles to avoid later fetching titles on the main thread.
     */
    public void loadRingtoneTitles() {
        enforceNotMainLooper();
        mRingtoneModel.loadRingtoneTitles();
    }

    /**
     * Recheck the permission to read each custom ringtone.
     */
    public void loadRingtonePermissions() {
        enforceNotMainLooper();
        mRingtoneModel.loadRingtonePermissions();
    }

    /**
     * @param uri the uri of a ringtone
     * @return the title of the ringtone with the {@code uri}; {@code null} if it cannot be fetched
     */
    public String getRingtoneTitle(Uri uri) {
        enforceMainLooper();
        return mRingtoneModel.getRingtoneTitle(uri);
    }

    /**
     * @param uri   the uri of an audio file to use as a ringtone
     * @param title the title of the audio content at the given {@code uri}
     */
    public Uri customRingtoneToAdd(Uri uri, String title) {
        enforceMainLooper();
        return mRingtoneModel.customRingtoneToAdd(uri, title);
    }

    /**
     * @param uri identifies the ringtone to remove
     */
    public void removeCustomRingtone(Uri uri) {
        enforceMainLooper();
        mRingtoneModel.removeCustomRingtone(uri);
    }

    /**
     * @return {@code true} if a custom ringtone with a given name and size is already present
     * to avoid adding duplicates. {@code false} otherwise.
     */
    public boolean isCustomRingtoneAlreadyAdded(String name, long size) {
        return mRingtoneModel.customRingtoneAlreadyAdded(name, size) != null;
    }

    /**
     * @return all available custom ringtones
     */
    public List<CustomRingtone> getCustomRingtones() {
        enforceMainLooper();
        return mRingtoneModel.getCustomRingtones();
    }

    /**
     * @param silentSettingsListener to be notified when alarm-silencing settings change
     */
    public void addSilentSettingsListener(OnSilentSettingsListener silentSettingsListener) {
        enforceMainLooper();
        mSilentSettingsModel.addSilentSettingsListener(silentSettingsListener);
    }

    /**
     * @param silentSettingsListener to no longer be notified when alarm-silencing settings change
     */
    public void removeSilentSettingsListener(OnSilentSettingsListener silentSettingsListener) {
        enforceMainLooper();
        mSilentSettingsModel.removeSilentSettingsListener(silentSettingsListener);
    }

    /**
     * Indicates the display style of clocks.
     */
    public enum ClockStyle {ANALOG, ANALOG_MATERIAL, DIGITAL}

    /**
     * Indicates the preferred sort order of cities.
     */
    public enum CitySort {NAME, UTC_OFFSET}

    /**
     * Indicates the preferred behavior of power button when firing alarms.
     */
    public enum PowerButtonBehavior {NOTHING, SNOOZE, DISMISS}

    /**
     * Indicates the preferred behavior of volume button when firing alarms.
     */
    public enum VolumeButtonBehavior {CHANGE_VOLUME, SNOOZE_ALARM, DISMISS_ALARM, DO_NOTHING}

    /**
     * Indicates the reason alarms may not fire or may fire silently.
     */
    public enum SilentSetting {
        DO_NOT_DISTURB(R.string.alarms_blocked_by_dnd, 0, Predicate.FALSE, null),
        MUTED_VOLUME(R.string.alarm_volume_muted,
                R.string.unmute_alarm_volume,
                Predicate.TRUE,
                new UnmuteAlarmVolumeListener()),
        SILENT_RINGTONE(R.string.silent_default_alarm_ringtone,
                R.string.change_setting_action,
                new ChangeSoundActionPredicate(),
                new ChangeSoundSettingsListener());

        private final @StringRes
        int mLabelResId;
        private final @StringRes
        int mActionResId;
        private final Predicate<Context> mActionEnabled;
        private final View.OnClickListener mActionListener;

        SilentSetting(int labelResId, int actionResId, Predicate<Context> actionEnabled,
                      View.OnClickListener actionListener) {
            mLabelResId = labelResId;
            mActionResId = actionResId;
            mActionEnabled = actionEnabled;
            mActionListener = actionListener;
        }

        public @StringRes
        int getLabelResId() {
            return mLabelResId;
        }

        public @StringRes
        int getActionResId() {
            return mActionResId;
        }

        public View.OnClickListener getActionListener() {
            return mActionListener;
        }

        public boolean isActionEnabled(Context context) {
            return mLabelResId != 0 && mActionEnabled.apply(context);
        }

        private static class UnmuteAlarmVolumeListener implements View.OnClickListener {
            @Override
            public void onClick(View v) {
                // Set the alarm volume to 11/16th of max and show the slider UI.
                // 11/16th of max is the initial volume of the alarm stream on a fresh install.
                final Context context = v.getContext();
                final AudioManager am = (AudioManager) context.getSystemService(AUDIO_SERVICE);
                final int index = Math.round(am.getStreamMaxVolume(STREAM_ALARM) * 11f / 16f);
                am.setStreamVolume(STREAM_ALARM, index, FLAG_SHOW_UI);
            }
        }

        private static class ChangeSoundSettingsListener implements View.OnClickListener {
            @Override
            public void onClick(View v) {
                /* todo reactivate the sound setting open code
                final Context context = v.getContext();
                context.startActivity(new Intent(ACTION_SOUND_SETTINGS)
                        .addFlags(FLAG_ACTIVITY_NEW_TASK));
                */
            }
        }

        private static class ChangeSoundActionPredicate implements Predicate<Context> {
            @Override
            public boolean apply(Context context) {
/*
                /* todo reactivate the sound setting open code

              final Intent intent = new Intent(ACTION_SOUND_SETTINGS);
                try {
                    context.startActivity(intent);
                } catch (ActivityNotFoundException ex) {
                    Toast.makeText(context, "application_not_found", Toast.LENGTH_SHORT).show();
                }
  */
                return true;
            }
        }
    }

    /**
     * Used to execute a delegate runnable and track its completion.
     */
    private static class ExecutedRunnable implements Runnable {

        private final Runnable mDelegate;
        private boolean mExecuted;

        private ExecutedRunnable(Runnable delegate) {
            this.mDelegate = delegate;
        }

        @Override
        public void run() {
            mDelegate.run();

            synchronized (this) {
                mExecuted = true;
                notifyAll();
            }
        }

        private boolean isExecuted() {
            return mExecuted;
        }
    }
}
