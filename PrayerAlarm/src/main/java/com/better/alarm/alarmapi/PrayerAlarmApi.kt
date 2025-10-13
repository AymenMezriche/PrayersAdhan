@file:JvmName("PrayerAlarmManager")

package com.better.alarm.alarmapi

import com.better.alarm.data.PrayerTime
import org.koin.java.KoinJavaComponent.getKoin

/**
 * Public API for the app module to interact with the PrayerAlarm library.
 * This class acts as a static bridge, hiding the internal Koin dependency injection from the Java caller.
 */

/**
 * Finds all prayer alarms and updates their times based on the provided list, then ensures they are enabled.
 * This is intended to be called from external modules (like the `app` module) that do not have access to Koin.
 *
 * @param prayerTimes A list of [PrayerTime] objects for the current day.
 */
fun updateAndEnablePrayerAlarms(prayerTimes: @JvmSuppressWildcards List<PrayerTime>) {
    // This function acts as a bridge. It uses Koin internally...
    val prayerAlarmUpdater: PrayerAlarmUpdater = getKoin().get()
    // ...and delegates the call to the real class instance which handles the logic.
    prayerAlarmUpdater.updateAndEnablePrayerAlarms(prayerTimes)
}

/**
 * Enables or disables a specific prayer alarm by its ID.
 *
 * @param alarmId The ID of the alarm to update.
 * @param isEnabled True to enable the alarm, false to disable it.
 * @param callback A callback function that receives a [Result] indicating success or failure.
 */
fun setAlarmEnabled(
    alarmId: Int,
    isEnabled: Boolean,
    callback: ((Result<Unit>) -> Unit)
) {
    val prayerAlarmUpdater: PrayerAlarmUpdater = getKoin().get()
    prayerAlarmUpdater.setAlarmEnabled(alarmId, isEnabled, callback)
}

