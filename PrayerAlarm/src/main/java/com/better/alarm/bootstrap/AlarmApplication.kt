/*
 * Copyright (C) 2012 Yuriy Kulikov yuriy.kulikov.87@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.better.alarm.bootstrap

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.view.ViewConfiguration
import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import com.better.alarm.R
import com.better.alarm.bootstrap.AlarmApplicationInit.startOnce
import com.better.alarm.data.AlarmValue
import com.better.alarm.data.AlarmsRepository
import com.better.alarm.data.PrayerTime
import com.better.alarm.domain.Alarms
import com.better.alarm.domain.AlarmsScheduler
import com.better.alarm.domain.Store
import com.better.alarm.logger.BugReporter
import com.better.alarm.notifications.BackgroundNotifications
import com.better.alarm.notifications.createNotificationChannels
import com.better.alarm.receivers.ScheduledReceiver
import com.better.alarm.services.AlertServicePusher
import com.better.alarm.ui.toast.ToastPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean

class AlarmApplication : MultiDexApplication() {
    var defaultAlarmsInserted: Boolean = false  // ← new flag

    override fun onCreate() {
        startOnce(this)
        super.onCreate()
    }

    companion object {
        @JvmStatic
        fun startOnce(application: Application) {
            application.startOnce()
        }
    }
}

private object AlarmApplicationInit {
    private val started = AtomicBoolean(false)

    @SuppressLint("SoonBlockedPrivateApi")
    fun Application.startOnce() {
        if (started.getAndSet(true)) {
            return
        }

        runCatching {
            ViewConfiguration::class
                .java
                .getDeclaredField("sHasPermanentMenuKey")
                .apply { isAccessible = true }
                .setBoolean(ViewConfiguration.get(this), false)
        }

        val koin = startKoin(applicationContext)

        koin.get<BugReporter>().attachToMainThread(this)

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

        koin.get<ScheduledReceiver>().start()
        koin.get<ToastPresenter>().start()
        koin.get<AlertServicePusher>()
        koin.get<BackgroundNotifications>()

        createNotificationChannels()

        // must be started the last, because otherwise we may loose intents from it.
        val alarmsLogger = koin.logger("Alarms")
        koin.get<Alarms>().start()

        // ✅ Update today's prayer times once per day
        CoroutineScope(Dispatchers.Default).launch {
            val repository = koin.get<AlarmsRepository>()
            val prefs = PreferenceManager.getDefaultSharedPreferences(this@startOnce)
            maybeUpdatePrayerTimesOncePerDay(repository, prefs)
        }

        alarmsLogger.debug { "Started alarms, SDK is " + Build.VERSION.SDK_INT }
        // start scheduling alarms after all alarms have been started
        koin.get<AlarmsScheduler>().start()

        with(koin.get<Store>()) {
            // register logging after startup has finished to avoid logging( O(n) instead of O(n log n) )
            alarms()
                .distinctUntilChanged()
                .map { it.toSet() }
                .startWith(emptySet<AlarmValue>())
                .buffer(2, 1)
                .map { (prev, next) -> next.minus(prev).map { it.toString() } }
                .distinctUntilChanged()
                .subscribe { lines -> lines.forEach { alarmsLogger.debug { it } } }
        }
    }
    // Helper function to get today's date as string
    fun todayString(): String {
        val cal = Calendar.getInstance()
        return String.format("%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1, // MONTH is 0-based
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    suspend fun maybeUpdatePrayerTimesOncePerDay(
        repository: AlarmsRepository,
        prefs: SharedPreferences
    ) {

        // In maybeUpdatePrayerTimesOncePerDay()
        val todayKey = todayString()
        val defaultAlarmsDate = DefaultAlarmsTracker.creationDate

        if (defaultAlarmsDate == todayKey) {
            Log.d("PrayerUpdate", "⏭ Default alarms just created today — skipping prayer time update")
            return
        }

        val today = todayString()
        val lastUpdateDate = prefs.getString("last_update_date", null)

        /*if (lastUpdateDate != today) {
            Log.d("PrayerUpdate", "⏰ Updating prayer times for $today")

            val alarms = repository.query()
            val prayerTimes = getTodayPrayerTimes() // returns List<PrayerTime>

            Log.d("PrayerUpdate", "start calling updateTimeSilently for each alarm")
            withContext(Dispatchers.Main) {
                alarms.forEach { alarm ->
                    val label = alarm.value.label
                    val newTime = prayerTimes.find { it.name == label }
                    if (newTime != null) {
                        (repository as? DataStoreAlarmsRepository)?.updateTimeSilently(
                            alarm.id,
                            newTime.hour,
                            newTime.minute
                        )
                    }
                }
            }

            // Ensure all updates are actually stored
            (repository as? DataStoreAlarmsRepository)?.awaitStored()

            prefs.edit().putString("last_update_date", today).apply()
            Log.d("PrayerUpdate", "✅ Updated alarms for $today")

            // ✅ Now reading back from repository guarantees the persisted values
            //val updatedAlarms = repository.query()
            //updatedAlarms.forEach { Log.d("PrayerUpdate", "Alarm after update: ${it.value}") }

        } else {
            Log.d("PrayerUpdate", "⏭ Already updated today — skipping")
        }*/

        if (lastUpdateDate != today) {
            Log.d("PrayerUpdate", "⏰ Updating prayer times for $today")

            val prayerTimes = getTodayPrayerTimes()

            updateAllAlarmsTime(prefs,prayerTimes)

        } else {
            Log.d("PrayerUpdate", "⏭ Already updated today — skipping")
        }

    }

    fun getTodayPrayerTimes(): List<PrayerTime> {
        return listOf(
            PrayerTime("Fajr", 5, 15),
            PrayerTime("Dhuhr", 12, 30),
            PrayerTime("Asr", 15, 45),
            PrayerTime("Maghrib", 18, 18),
            PrayerTime("Isha", 20, 18)
        )
    }
}
fun todayString(): String {
    val cal = Calendar.getInstance()
    return String.format("%04d-%02d-%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1, // MONTH is 0-based
        cal.get(Calendar.DAY_OF_MONTH)
    )
}

fun updateAllAlarmsTime(prefs: SharedPreferences, prayerTimes: List<PrayerTime>) {
    Log.d("PrayerUpdate", "🕒 Updating all alarms with today's prayer times...")

    val today = todayString()

    // Ensure main-thread safe access to Koin + Store
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        try {
            val alarmsManager: com.better.alarm.domain.IAlarmsManager = org.koin.java.KoinJavaComponent.getKoin().get()
            val store: com.better.alarm.domain.Store = org.koin.java.KoinJavaComponent.getKoin().get()

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
                        Log.d("PrayerUpdate", "✅ Updated ${alarm.label} → ${prayerMatch.hour}:${prayerMatch.minute}")
                    }
                }

                Log.d("PrayerUpdate", "🎉 Completed: updated $updatedCount of $totalCount alarms")

                prefs.edit().putString("last_update_date", today).apply()
                Log.d("PrayerUpdate", "✅ Updated alarms for $today")

            }, { error ->
                Log.e("PrayerUpdate", "❌ Failed to read alarms", error)
            })
        } catch (e: Exception) {
            Log.e("PrayerUpdate", "❌ Fatal error while updating alarms", e)
        }
    }

}


object DefaultAlarmsTracker {
    // Store as yyyy-MM-dd string
    var creationDate: String? = null
}

