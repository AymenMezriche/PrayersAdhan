@file:JvmName("PrayerAlarmManager")

package com.better.alarm.alarmapi

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import com.better.alarm.alarmapi.Formatter.todayString
import com.better.alarm.data.PrayerTime
import com.better.alarm.domain.IAlarmsManager
import com.better.alarm.domain.Store
import org.koin.java.KoinJavaComponent.getKoin
import java.util.Calendar

class PrayerAlarmUpdater(
    private val alarmsManager: IAlarmsManager,
    private val store: Store,
    private val prefs: SharedPreferences,
) {


    fun updatePrayerTimesOncePerDay(
    ) {
        val todayKey = todayString()
        val defaultAlarmsDate = DefaultAlarmsTracker.creationDate

        if (defaultAlarmsDate == todayKey) {
            Log.d(
                "PrayerUpdate",
                "⏭ Default alarms just created today — skipping prayer time update"
            )
            return
        }

        val today = todayString()
        val lastUpdateDate = prefs.getString("last_update_date", null)

        if (lastUpdateDate != today) {
            Log.d("PrayerUpdate", "⏰ Updating prayer times for $today")

            //this was for testing
            //val prayerTimes = getTodayPrayerTimes()

            val calendar = Calendar.getInstance()
            //the calender used to now the day that we are requesting their prayer times
            AlarmDependencies.prayerTimesProvider?.getTodayPrayerTimes(calendar) { result ->
                result
                    .onSuccess { prayerTimes ->
                        Log.d("PrayerUpdate", "Got prayer times: $prayerTimes")
                        updateAllAlarmsTime(prefs, prayerTimes)
                    }
                    .onError { e ->
                        Log.e("PrayerUpdate", "Failed to get prayer times", e)
                    }
            }


        } else {
            Log.d("PrayerUpdate", "⏭ Already updated today — skipping")
        }

    }

    fun updateAllAlarmsTime(prefs: SharedPreferences, prayerTimes: List<PrayerTime>) {
        Log.d("PrayerUpdate", "🕒 Updating all alarms with today's prayer times...")

        val today = todayString()

        // Ensure main-thread safe access to Koin + Store
        Handler(Looper.getMainLooper()).post {
            try {
                store.alarms().firstElement().subscribe({ alarms ->
                    var updatedCount = 0
                    val totalCount = alarms.size

                    alarms.forEach { alarm ->
                        val prayerMatch = prayerTimes.find { it.name == alarm.label }
                        if (prayerMatch != null &&
                            (alarm.hour != prayerMatch.hour || alarm.minutes != prayerMatch.minute)
                        ) {
                            alarmsManager.getAlarm(alarm.id)?.edit {
                                copy(hour = prayerMatch.hour, minutes = prayerMatch.minute)
                            }
                            updatedCount++
                            Log.d(
                                "PrayerUpdate",
                                "✅ Updated ${alarm.label} → ${prayerMatch.hour}:${prayerMatch.minute}"
                            )
                        }
                    }

                    Log.d(
                        "PrayerUpdate",
                        "🎉 Completed: updated $updatedCount of $totalCount alarms"
                    )

                    prefs.edit { putString("last_update_date", today) }
                    Log.d("PrayerUpdate", "✅ Updated alarms for $today")

                }, { error ->
                    Log.e("PrayerUpdate", "❌ Failed to read alarms", error)
                })
            } catch (e: Exception) {
                Log.e("PrayerUpdate", "❌ Fatal error while updating alarms", e)
            }
        }
    }

    /**
     * Finds all prayer alarms and updates their times based on the provided list, then ensures they are enabled.
     * This is intended to be called from external modules (like the `app` module) that do not have access to Koin.
     *
     * @param prayerTimes A list of [PrayerTime] objects for the current day.
     */
    fun updateAndEnablePrayerAlarms(prayerTimes: @JvmSuppressWildcards List<PrayerTime>) {
        Log.d(
            "PrayerUpdate",
            "🕒 Updating and enabling all prayer alarms with today's prayer times..."
        )

        // Ensure main-thread safe access to Koin + Store
        Handler(Looper.getMainLooper()).post {
            try {
                val alarmsManager: IAlarmsManager = getKoin().get()
                val store: Store = getKoin().get()

                store.alarms().firstElement().subscribe({ alarms ->
                    var updatedCount = 0
                    val totalCount = alarms.size

                    alarms.forEach { alarmValue ->
                        val prayerMatch = prayerTimes.find { it.name == alarmValue.label }
                        if (prayerMatch != null) {

                            alarmsManager.getAlarm(alarmValue.id)?.edit {
                                copy(
                                    hour = prayerMatch.hour,
                                    minutes = prayerMatch.minute,
                                    isEnabled = true
                                )
                            }
                            updatedCount++
                        }
                    }

                    Log.d(
                        "PrayerUpdate",
                        "🎉 Completed: updated and enabled $updatedCount of $totalCount alarms"
                    )

                }, { error ->
                    Log.e("PrayerUpdate", "❌ Failed to read alarms", error)
                })
            } catch (e: Exception) {
                Log.e("PrayerUpdate", "❌ Fatal error while updating alarms", e)
            }
        }
    }

    /**
     * Enables or disables a specific alarm by its ID.
     *
     * @param alarmId The ID of the alarm to update.
     * @param isEnabled True to enable the alarm, false to disable it.
     * @param callback A callback function that receives a Result indicating success or failure.
     */
    fun setAlarmEnabled(
        alarmId: Int,
        isEnabled: Boolean,
        callback: (Result<Unit>) -> Unit
    ) {
        Log.d("PrayerUpdate", "🕒 Setting alarm ID $alarmId enabled status to: $isEnabled")
        try {
            val alarm = alarmsManager.getAlarm(alarmId)
            if (alarm != null) {
                alarm.edit {
                    copy(isEnabled = isEnabled)
                }
                Log.d("PrayerUpdate", "✅ Successfully updated alarm ID $alarmId")
                callback(Result.success(Unit)) // Signal success
            } else {
                val errorMessage = "❌ Alarm with ID $alarmId not found."
                Log.e("PrayerUpdate", errorMessage)
                callback(Result.error(NoSuchElementException(errorMessage)))
            }
        } catch (e: Exception) {
            Log.e("PrayerUpdate", "❌ Failed to update alarm ID $alarmId", e)
            callback(Result.error(e))
        }
    }

}

/* Used to track when default alarms
 were created to avoid redundant prayer time updates on the same day.
 */
object DefaultAlarmsTracker {
    // Store as yyyy-MM-dd string
    var creationDate: String? = null
}

