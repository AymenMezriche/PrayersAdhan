/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.better.alarm.ui.main

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.ChangeTransform
import android.transition.Fade
import android.transition.Slide
import android.transition.TransitionSet
import android.view.Gravity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.better.alarm.R
import com.better.alarm.bootstrap.AlarmInitializer
import com.better.alarm.bootstrap.globalLogger
import com.better.alarm.data.AlarmValue
import com.better.alarm.data.AlarmsRepository
import com.better.alarm.data.DataStoreAlarmsRepository
import com.better.alarm.domain.Store
import com.better.alarm.logger.Logger
import com.better.alarm.notifications.NotificationSettings
import com.better.alarm.platform.checkPermissions
import com.better.alarm.ui.details.AlarmDetailsFragment
import com.better.alarm.ui.list.AlarmsListFragment
import com.better.alarm.ui.settings.SettingsFragment
import com.better.alarm.ui.state.BackPresses
import com.better.alarm.ui.state.EditedAlarm
import com.better.alarm.ui.themes.DynamicThemeHandler
import com.better.alarm.ui.toast.formatToast
import com.google.android.material.snackbar.Snackbar
import io.reactivex.disposables.Disposables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.awaitFirst
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/** This activity displays a list of alarms and optionally a details fragment. */
class AlarmsListActivity() : AppCompatActivity() {
    private val mActionBarHandler: ActionBarHandler by lazy {
        ActionBarHandler(this, viewModel, backPresses)
    }
    private val logger: Logger by globalLogger("AlarmsListActivity")
    private val store: Store by inject()

    private var snackbarDisposable = Disposables.disposed()

    private val viewModel: MainViewModel by viewModel()
    private val backPresses: BackPresses by inject()
    private val dynamicThemeHandler: DynamicThemeHandler by inject()

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        logger.debug { "new $intent, ${intent?.extras}" }
        if (intent?.getStringExtra("reason") == SettingsFragment.themeChangeReason) {
            finish()
            startActivity(
                Intent(this, AlarmsListActivity::class.java).apply {
                    putExtra("openDrawerOnCreate", true)
                })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        //outState.putInt("version", BuildConfig.VERSION_CODE)
        viewModel.editing().value?.writeInto(outState)
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        AlarmInitializer.init(application)
        setTheme(dynamicThemeHandler.defaultTheme())
        super.onCreate(savedInstanceState)
        viewModel.openDrawerOnCreate = intent?.getBooleanExtra("openDrawerOnCreate", false) ?: false
        /*todo we comment this to avoid build error of 'BuildConfig.VERSION_CODE'
            val prevVersion = savedInstanceState?.getInt("version", BuildConfig.VERSION_CODE)
        if (prevVersion == BuildConfig.VERSION_CODE) {
            val restored = editedAlarmFromSavedInstanceState(savedInstanceState)
            logger.trace { "Restored $this with $restored" }
            restored?.let { viewModel.edit(it) }
        } else {
            viewModel.hideDetails()
        }*/

        if (!resources.getBoolean(R.bool.isTablet)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        setContentView(R.layout.list_activity)

        val context = this
        lifecycleScope.launch {
            checkPermissions(context, store.alarms().awaitFirst().map { it.alarmtone })
        }

        backPresses.onBackPressed(lifecycle) { finish() }

        printDataStoreFiles(applicationContext)
        dumpAllAlarmsUsingKoin();

        printNextAlarm(this);

//      testUpdateAlarm(0, 22, 21)
        
        // Uncomment the line below to test prayer alarm creation
        // createPrayerTimeAlarms()
        
        // Uncomment the line below to test enable all alarms
        // enableAllAlarms()
    }

    override fun onStart() {
        super.onStart()
        configureTransactions()
        configureSnackbar()
    }

    private fun configureSnackbar() {
        snackbarDisposable =
            store
                .sets()
                .withLatestFrom(store.uiVisible) { set, uiVisible -> set to uiVisible }
                .subscribe { (set: Store.AlarmSet, uiVisible: Boolean) ->
                    if (uiVisible) {
                        showSnackbar(set)
                    }
                }
    }

    /**
     * using the main container instead of a root view here fixes
     * https://github.com/yuriykulikov/AlarmClock/issues/372
     */
    private fun showSnackbar(set: Store.AlarmSet) {
        val toastText = formatToast(applicationContext, set.millis)
        Snackbar.make(findViewById(R.id.main_fragment_container), toastText, Snackbar.LENGTH_LONG)
            .apply {
                val text = view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                text.gravity = Gravity.CENTER_HORIZONTAL
                text.textAlignment = View.TEXT_ALIGNMENT_CENTER
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        NotificationSettings.checkNotificationPermissionsAndSettings(this)
        store.uiVisible.onNext(true)
    }

    override fun onPause() {
        super.onPause()
        store.uiVisible.onNext(false)
        viewModel.awaitStored()
    }

    override fun onStop() {
        super.onStop()
        snackbarDisposable.dispose()
    }

    override fun onDestroy() {
        logger.debug { "$this" }
        super.onDestroy()
        this.mActionBarHandler.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return supportActionBar?.let { mActionBarHandler.onCreateOptionsMenu(menu, menuInflater, it) }
            ?: false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return mActionBarHandler.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        backPresses.backPressed("AlarmsListActivity.onBackPressed")
    }

    private fun configureTransactions() {
        viewModel
            .editing()
            .onEach { edited ->
                when {
                    isDestroyed -> return@onEach
                    edited != null -> showDetails(edited)
                    else -> showList()
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun showList() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.main_fragment_container)
        if (currentFragment is AlarmsListFragment) {
            logger.trace { "skipping fragment transition, because already showing $currentFragment" }
            return
        } else {
            val details: AlarmDetailsFragment? = currentFragment as? AlarmDetailsFragment
            logger.trace { "transition from: $details to AlarmsListFragment" }
            supportFragmentManager.commit(allowStateLoss = true) {
                replace(
                    R.id.main_fragment_container,
                    AlarmsListFragment().apply {
                        arguments = Bundle()
                        enterTransition = TransitionSet().addTransition(Fade())
                        sharedElementEnterTransition = moveTransition()
                        allowEnterTransitionOverlap = true
                    })
                details?.exitTransition = Fade()
                details?.rowHolder?.run {
                    addSharedElement(digitalClock, "clock${details.editedAlarmId}")
                    addSharedElement(container, "onOff${details.editedAlarmId}")
                    addSharedElement(detailsButton, "detailsButton${details.editedAlarmId}")
                }
            }
        }
    }

    private fun showDetails(edited: EditedAlarm) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.main_fragment_container)
        if (currentFragment is AlarmDetailsFragment) {
            logger.trace { "skipping fragment transition, because already showing $currentFragment" }
        } else {
            val listFragment = currentFragment as? AlarmsListFragment
            logger.trace { "transition from: $currentFragment to AlarmDetailsFragment" }

            supportFragmentManager.commit(allowStateLoss = true) {
                replace(
                    R.id.main_fragment_container,
                    AlarmDetailsFragment().apply {
                        arguments = Bundle()
                        enterTransition = TransitionSet().addTransition(Slide()).addTransition(Fade())
                        sharedElementEnterTransition = moveTransition()
                        allowEnterTransitionOverlap = true
                    })
                listFragment?.exitTransition = Fade()
                listFragment?.transitionRowHolder?.run {
                    addSharedElement(digitalClock, "clock")
                    addSharedElement(container, "onOff")
                    addSharedElement(detailsButton, "detailsButton")
                }
            }
        }
    }

    private fun moveTransition(): TransitionSet {
        return TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(ChangeBounds())
            addTransition(ChangeTransform())
        }
    }

    /** restores an [EditedAlarm] from SavedInstanceState. Counterpart of [EditedAlarm.writeInto]. */
    @OptIn(ExperimentalSerializationApi::class)
    private fun editedAlarmFromSavedInstanceState(savedInstanceState: Bundle): EditedAlarm? {
        return if (savedInstanceState.getBoolean("isEdited")) {
            val restored =
                ProtoBuf.decodeFromByteArray(
                    AlarmValue.serializer(), savedInstanceState.getByteArray("edited") ?: ByteArray(0)
                )
            EditedAlarm(savedInstanceState.getBoolean("isNew"), restored)
        } else {
            null
        }
    }

    /**
     * Saves EditedAlarm into SavedInstanceState. Counterpart of [editedAlarmFromSavedInstanceState]
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun EditedAlarm.writeInto(outState: Bundle?) {
        val toWrite: EditedAlarm = this
        outState?.run {
            putBoolean("isNew", isNew)
            putBoolean("isEdited", true)
            putByteArray("edited", ProtoBuf.encodeToByteArray(AlarmValue.serializer(), value))
            logger.trace { "Saved state $toWrite" }
        }
    }

    fun printDataStoreFiles(context: Context) {
        val tag = "DataStoreLog"
        val dir = File(context.filesDir, "datastore")
        if (!dir.exists()) {
            Log.d(tag, "No datastore directory found at: ${dir.absolutePath}")
            return
        }

        dir.listFiles()?.forEach { file ->
            Log.d(tag, "📦 File: ${file.name}, size = ${file.length()} bytes")
            try {
                val content = file.readText()
                Log.d(tag, "------ Content of ${file.name} ------")
                Log.d(tag, content.take(500)) // show first 500 chars for safety
                Log.d(tag, "------ End ------")
            } catch (e: Exception) {
                Log.d(tag, "⚠️ Could not read ${file.name}: ${e.message}")
            }
        }
    }

    fun dumpAllAlarmsUsingKoin() {
        val repo = org.koin.java.KoinJavaComponent.getKoin().get<AlarmsRepository>()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val allAlarms = repo.query()
                if (allAlarms.isEmpty()) {
                    Log.d("AlarmDebug", "📭 No alarms found in repository.")
                } else {
                    Log.d("AlarmDebug", "📋 Found ${allAlarms.size} alarms:")
                    allAlarms.forEach { store ->
                        val a = store.value  // ✅ safe now, runs on main thread
                        Log.d(
                            "AlarmDebug",
                            """
                        ─────────────────────────────
                        🆔 id = ${a.id}
                        ⏰ time = ${a.hour}:${a.minutes.toString().padStart(2, '0')}
                        🔔 enabled = ${a.isEnabled}
                        📅 days = ${a.daysOfWeek}
                        📝 label = ${a.label}
                        ─────────────────────────────
                        """.trimIndent()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("AlarmDebug", "Failed to read alarms", e)
            }
        }
    }

    private fun testUpdateAlarm(id: Int, newHour: Int, newMinute: Int) {
        Log.d("AlarmDebug", "🛠️ Attempting to update alarm #$id to $newHour:$newMinute")
        val repo: AlarmsRepository = org.koin.java.KoinJavaComponent.getKoin().get()

        GlobalScope.launch(Dispatchers.IO) {
            if (repo is DataStoreAlarmsRepository) {
                repo.updateTimeSilently(id, newHour, newMinute)
                repo.awaitStored() // optional, ensures persisted
            }

            withContext(Dispatchers.Main) {
                Log.d("AlarmDebug", "✅ Alarm #$id updated to $newHour:$newMinute")
            }
        }
    }

    /**
     * Creates 5 Islamic prayer time alarms with appropriate labels and repeating daily
     * Call this function to set up all prayer time alarms at once
     */
    /*
    fun createPrayerTimeAlarms() {
        Log.d("PrayerAlarms", "🕌 Creating Islamic prayer time alarms...")
        
        // Ensure this runs on the main thread to avoid DataStore threading issues
        runOnUiThread {
            try {
                // Get the alarms manager to create new alarms
                val alarmsManager: com.better.alarm.domain.IAlarmsManager = org.koin.java.KoinJavaComponent.getKoin().get()
                
                // Prayer times (you can adjust these times as needed)
                // Format: (hour, minute, label)
                val prayerTimes = listOf(
                    Triple(5, 8, "Fajr"),      // Dawn prayer
                    Triple(12, 17, "Dhuhr"),    // Midday prayer
                    Triple(15, 30, "Asr"),      // Afternoon prayer
                    Triple(18, 2, "Maghrib"),  // Sunset prayer
                    Triple(19, 20, "Isha")       // Night prayer
                )
                
                // Create all days of week (0x7F = 127 in decimal = all 7 days selected)
                val allDaysOfWeek = com.better.alarm.data.DaysOfWeek(0x7F)
                
                prayerTimes.forEach { (hour, minute, label) ->
                    try {
                        // Create new alarm
                        val alarm = alarmsManager.createNewAlarm()
                        
                        // Configure the alarm
                        alarm.edit {
                            copy(
                                hour = hour,
                                minutes = minute,
                                isEnabled = true,
                                label = "🕌 $label Prayer",
                                daysOfWeek = allDaysOfWeek,
                                isDeleteAfterDismiss = false, // Keep alarm after dismiss for repeating
                                isVibrate = true,
                                alarmtone = com.better.alarm.data.Alarmtone.Default
                            )
                        }
                        
                        Log.d("PrayerAlarms", "✅ Created $label prayer alarm at ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}")
                        
                    } catch (e: Exception) {
                        Log.e("PrayerAlarms", "❌ Failed to create $label prayer alarm", e)
                    }
                }
                
                Log.d("PrayerAlarms", "🎉 All prayer time alarms created successfully!")
                
            } catch (e: Exception) {
                Log.e("PrayerAlarms", "❌ Failed to create prayer alarms", e)
            }
        }
    }
     */

    /**
     * Enables all existing alarms in the app
     * Call this function to turn on all alarms at once
     */
    fun enableAllAlarms() {
        Log.d("EnableAllAlarms", "🔔 Enabling all existing alarms...")
        
        // Ensure this runs on the main thread to avoid DataStore threading issues
        runOnUiThread {
            try {
                // Get the alarms manager and store
                val alarmsManager: com.better.alarm.domain.IAlarmsManager = org.koin.java.KoinJavaComponent.getKoin().get()
                val store: Store = org.koin.java.KoinJavaComponent.getKoin().get()
                
                var enabledCount = 0
                var totalCount = 0
                
                // Subscribe to get all alarms and enable them
                store.alarms().firstElement().subscribe(
                    { alarms ->
                        totalCount = alarms.size
                        alarms.forEach { alarm ->
                            try {
                                // Enable the alarm if it's not already enabled
                                if (!alarm.isEnabled) {
                                    alarmsManager.getAlarm(alarm.id)?.edit { 
                                        copy(isEnabled = true) 
                                    }
                                    enabledCount++
                                    Log.d("EnableAllAlarms", "✅ Enabled alarm ID ${alarm.id} at ${alarm.hour}:${alarm.minutes.toString().padStart(2, '0')} - ${alarm.label}")
                                } else {
                                    Log.d("EnableAllAlarms", "ℹ️ Alarm ID ${alarm.id} already enabled")
                                }
                            } catch (e: Exception) {
                                Log.e("EnableAllAlarms", "❌ Failed to enable alarm ID ${alarm.id}", e)
                            }
                        }
                        
                        Log.d("EnableAllAlarms", "🎉 Completed! Enabled $enabledCount out of $totalCount alarms")
                        
                        // Show user feedback
                        runOnUiThread {
                            val message = if (enabledCount > 0) {
                                "Enabled $enabledCount alarm${if (enabledCount == 1) "" else "s"}! 🔔"
                            } else {
                                "All alarms were already enabled! ✅"
                            }
                            // You can add a toast here if needed
                            Log.i("EnableAllAlarms", message)
                        }
                    },
                    { error ->
                        Log.e("EnableAllAlarms", "❌ Failed to get alarms list", error)
                    }
                )
                
            } catch (e: Exception) {
                Log.e("EnableAllAlarms", "❌ Failed to enable all alarms", e)
            }
        }
    }

    /**
     * Retrieves and logs information about the next scheduled alarm for this application.
     * This requires the GET_SCHEDULED_EXACT_ALARM permission for Android 14+.
     *
     * @param context The application context.
     */
    fun printNextAlarm(context: Context) {
        // 1. Get the AlarmManager system service
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        if (alarmManager == null) {
            Log.e("NextAlarmInfo", "Could not get AlarmManager service.")
            return
        }

        try {
            // 2. Get the next scheduled alarm info
            // This returns info only for alarms set with setAlarmClock()
            val nextAlarm: AlarmManager.AlarmClockInfo? = alarmManager.nextAlarmClock

            // 3. Check if an alarm was found
            if (nextAlarm != null) {
                val triggerTimeMillis: Long = nextAlarm.triggerTime
                val triggerDate = Date(triggerTimeMillis)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss (zzzz)",
                    java.util.Locale.getDefault())

                val formattedTime = dateFormat.format(triggerDate)

                Log.i("NextAlarmInfo", "⏰ Next scheduled alarm:")
                Log.i("NextAlarmInfo", " -> Trigger Time (ms): $triggerTimeMillis")
                Log.i("NextAlarmInfo", " -> Formatted Time: $formattedTime")

                // You can also see the PendingIntent that will be triggered,
                // which is useful for debugging.
                Log.i("NextAlarmInfo", " -> PendingIntent: ${nextAlarm.showIntent}")

            } else {
                Log.w("NextAlarmInfo", "No next alarm clock is scheduled for this app.")
            }
        } catch (e: SecurityException) {
            Log.e("NextAlarmInfo", "SecurityException: Missing GET_SCHEDULED_EXACT_ALARM permission in AndroidManifest.xml?", e)
        } catch (e: Exception) {
            Log.e("NextAlarmInfo", "An error occurred while getting the next alarm.", e)
        }
    }
}

