package com.best.adhanclock;

import static com.best.deskclock.DeskClockApplication.getContext;
import static com.best.deskclock.settings.PreferencesDefaultValues.ALARM_SNOOZE_DURATION_DISABLED;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.best.deskclock.R;
import com.best.deskclock.alarms.AlarmStateManager;
import com.best.deskclock.alarms.AlarmUpdateHandler;
import com.best.deskclock.data.Weekdays;
import com.best.deskclock.provider.Alarm;
import com.best.deskclock.provider.AlarmInstance;
import com.better.alarmhelper.databinding.ActivityAddAlarmBinding;

import java.util.Calendar;
import java.util.List;

public class AddAlarmActivity extends AppCompatActivity {

    private ActivityAddAlarmBinding binding;
    private int selectedHour = -1;
    private int selectedMinute = -1;
    private int selectedYear, selectedMonth, selectedDay;
    private AlarmUpdateHandler mAlarmUpdateHandler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, com.better.alarmhelper.R.layout.activity_add_alarm);

        ViewGroup main = findViewById(com.better.alarmhelper.R.id.main);
        mAlarmUpdateHandler = new AlarmUpdateHandler(AddAlarmActivity.this, null, main);

        binding.tvDate.setOnClickListener(v -> showDatePicker());
        binding.tvTime.setOnClickListener(v -> showTimePicker());

        binding.btnAddAlarm.setOnClickListener(v -> addAlarm());

        binding.btnGetPrayers.setOnClickListener(v -> {
            Intent intent = new Intent(AddAlarmActivity.this, PrayersActivity.class);
            startActivity(intent);
        });

        binding.btnLogNextAlarm.setOnClickListener(v -> {
            testGetNextAlarm();
        });

        handleAlarmsState();
    }

    private void handleAlarmsState() {
        final ContentResolver contentResolver = getContentResolver();
        final List<AlarmInstance> instances = AlarmInstance.getInstances(contentResolver, null);
        if (instances.isEmpty())
            addPrayerAlarmsForToday();
        else {
            AlarmStateManager.fixAlarmInstances(AddAlarmActivity.this);
            Toast.makeText(this, "Alarms already set for today", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDatePicker() {
        final Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedYear = year;
            selectedMonth = month;
            selectedDay = dayOfMonth;
            binding.tvDate.setText(year + "-" + (month + 1) + "-" + dayOfMonth);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker() {
        final Calendar calendar = Calendar.getInstance();
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            selectedHour = hourOfDay;
            selectedMinute = minute;
            binding.tvTime.setText(hourOfDay + ":" + String.format("%02d", minute));
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
    }

    private void addAlarm() {
        if (selectedHour == -1 || selectedMinute == -1) {
            Toast.makeText(this, "Please select time", Toast.LENGTH_SHORT).show();
            return;
        }

        // Prepare Alarm object
        Alarm alarm = new Alarm();
        alarm.hour = selectedHour;
        alarm.minutes = selectedMinute;
        alarm.enabled = true;
        alarm.label = binding.etMessage.getText().toString();

        Calendar calendar = Calendar.getInstance();
        if (selectedYear != 0) {
            alarm.year = selectedYear;
            alarm.month = selectedMonth;
            alarm.day = selectedDay;
        } else {
            // fallback today
            alarm.year = calendar.get(Calendar.YEAR);
            alarm.month = calendar.get(Calendar.MONTH);
            alarm.day = calendar.get(Calendar.DAY_OF_MONTH);
        }

        // Example: set some defaults (can be adjusted)
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        alarm.alarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        alarm.vibrate = true;
        alarm.deleteAfterUse = true;
        alarm.autoSilenceDuration = 1; // minutes

        alarm.alert = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.adhan1);

        // Insert into DB via handler
        mAlarmUpdateHandler.asyncAddAlarm(alarm);

//        Toast.makeText(this, "Alarm added", Toast.LENGTH_SHORT).show();
    }

    private void addPrayerAlarmsForToday() {
        getToDayPrayerTimes(dayPrayerTimes -> {
            if (dayPrayerTimes != null) {
                int[][] prayerTimes = fromPrayerTimes(dayPrayerTimes);
                runOnUiThread(() -> addPrayerAlarms(prayerTimes));
            } else {
                runOnUiThread(() -> Toast.makeText(this, "No prayer times found for today", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void getToDayPrayerTimes(AlarmStateManager.Callback<DayPrayerTimes> callback) {
        Thread thread = new Thread(() -> {
            // Example key: "19-01-2025"
            Calendar day = Calendar.getInstance();

            String key = String.format("%02d-%02d-%04d",
                    day.get(Calendar.DAY_OF_MONTH),
                    day.get(Calendar.MONTH) + 1, // add +1 because months are zero-based
                    day.get(Calendar.YEAR));

            Log.d("AddAlarmActivity", "Fetching prayer times for key: " + key);

            AppDatabase db = DatabaseClient.getInstance(getContext()).getAppDatabase();
            DayPrayerTimesDao dao = db.getDayPrayerTimesDao();
            DayPrayerTimes dayPrayerTimes = dao.getByDate(key);
            Log.d("AddAlarmActivity", "Fetched prayer times: " + (dayPrayerTimes != null ? dayPrayerTimes.toString() : "null"));
            callback.onResult(dayPrayerTimes);
        });
        thread.start();
    }

    private int[][] fromPrayerTimes(DayPrayerTimes dayPrayerTimes) {
        // Convert prayer times from DayPrayerTimes to int[][] format
        // Assuming prayer times are in "HH:mm" format
        String[] prayerTimesStr = {
                dayPrayerTimes.getFajr(),
                dayPrayerTimes.getDhuhr(),
                dayPrayerTimes.getAsr(),
                dayPrayerTimes.getMaghrib(),
                dayPrayerTimes.getIsha()
        };

        int[][] prayerTimes = new int[prayerTimesStr.length][2];
        for (int i = 0; i < prayerTimesStr.length; i++) {
            String[] parts = prayerTimesStr[i].split(":");
            prayerTimes[i][0] = Integer.parseInt(parts[0]); // hour
            prayerTimes[i][1] = Integer.parseInt(parts[1]); // minute
        }
        return prayerTimes;
    }

    private void addPrayerAlarms(int[][] prayerTimes) {
//        AlarmStateManager.fixAlarmInstances(AddAlarmActivity.this);

        String[] prayerNames = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};

        for (int i = 0; i < prayerTimes.length; i++) {
            int hour = prayerTimes[i][0];
            int minute = prayerTimes[i][1];

            Alarm alarm = new Alarm();
            alarm.hour = hour;
            alarm.minutes = minute;
            alarm.enabled = true;
            alarm.label = prayerNames[i];

            Calendar calendar = Calendar.getInstance();
            alarm.year = calendar.get(Calendar.YEAR);
            alarm.month = calendar.get(Calendar.MONTH);
            alarm.day = calendar.get(Calendar.DAY_OF_MONTH);

            // Defaults
            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            alarm.alarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            alarm.vibrate = false;
            alarm.deleteAfterUse = false;
            alarm.autoSilenceDuration = 1;
            alarm.crescendoDuration = 60; //this is in seconds
            alarm.snoozeDuration = ALARM_SNOOZE_DURATION_DISABLED;
            alarm.alert = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.adhan1);

            final int[] daysArray = {1, 2, 3, 4, 5, 6, 7}; // Every day
            alarm.daysOfWeek = Weekdays.fromCalendarDays(daysArray);

            // Save to DB
            mAlarmUpdateHandler.asyncAddAlarm(alarm);
        }

        Toast.makeText(this, "Prayer alarms added", Toast.LENGTH_SHORT).show();
    }

    private void testGetNextAlarm() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        AlarmStateManager.getPrayerTimeForTest(calendar, "Fajr", hourMinute -> {
            runOnUiThread(() -> {
                if (hourMinute != null) {
                    Toast.makeText(this, "Next Asr prayer time: " + hourMinute[0] + ":" + hourMinute[1], Toast.LENGTH_SHORT).show();
                    Log.d("AddAlarmActivity", "Next Asr prayer time: " + hourMinute[0] + ":" + hourMinute[1]);
                } else {
                    Log.d("AddAlarmActivity", "No Asr prayer time found for today");
                    Toast.makeText(this, "No Asr prayer time found for today", Toast.LENGTH_SHORT).show();
                }
            });

        });

    }


}
