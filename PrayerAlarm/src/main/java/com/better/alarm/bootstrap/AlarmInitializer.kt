package com.better.alarm.bootstrap

import android.app.Application
import android.os.Build
import android.view.ViewConfiguration
import androidx.preference.PreferenceManager
import com.better.alarm.R
import com.better.alarm.alarmapi.PrayerAlarmUpdater
import com.better.alarm.data.AlarmValue
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
import org.koin.java.KoinJavaComponent.getKoin
import java.util.concurrent.atomic.AtomicBoolean

object AlarmInitializer {
    private val started = AtomicBoolean(false)
    private lateinit var applicationContext: Application


    @JvmStatic
    fun init(application: Application) {
        this.applicationContext = application
        if (started.getAndSet(true)) return

        runCatching {
            ViewConfiguration::class
                .java
                .getDeclaredField("sHasPermanentMenuKey")
                .apply { isAccessible = true }
                .setBoolean(ViewConfiguration.get(application), false)
        }

        val koin = startKoin(applicationContext)

        //enable this we want to catch crashes and open email app
//        koin.get<BugReporter>().attachToMainThread(applicationContext)

        PreferenceManager.setDefaultValues(applicationContext, R.xml.preferences, false)

        koin.get<ScheduledReceiver>().start()
        koin.get<ToastPresenter>().start()
        koin.get<AlertServicePusher>()
        koin.get<BackgroundNotifications>()

        applicationContext.createNotificationChannels()

        // must be started the last, because otherwise we may loose intents from it.
        val alarmsLogger = koin.logger("Alarms")
        koin.get<Alarms>().start()

        // ✅ Update today's prayer times once per day
        CoroutineScope(Dispatchers.Default).launch {
            updatePrayerTimesOncePerDay()
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

    fun updatePrayerTimesOncePerDay(
    ) {
        // Get the updater instance from Koin
        val prayerAlarmUpdater: PrayerAlarmUpdater = getKoin().get()
        prayerAlarmUpdater.updatePrayerTimesOncePerDay()
    }
}





