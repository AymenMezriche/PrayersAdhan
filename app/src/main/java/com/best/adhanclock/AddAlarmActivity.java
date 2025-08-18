package com.best.adhanclock;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.best.deskclock.R;
import com.best.deskclock.alarms.AlarmUpdateHandler;
import com.best.deskclock.databinding.ActivityAddAlarmBinding;
import com.best.deskclock.provider.Alarm;

import java.util.Calendar;

public class AddAlarmActivity extends AppCompatActivity {

    private ActivityAddAlarmBinding binding;
    private int selectedHour = -1;
    private int selectedMinute = -1;
    private int selectedYear, selectedMonth, selectedDay;
    private AlarmUpdateHandler mAlarmUpdateHandler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_add_alarm);

        ViewGroup main = findViewById(R.id.main);
        mAlarmUpdateHandler = new AlarmUpdateHandler(AddAlarmActivity.this, null,main);

        binding.tvDate.setOnClickListener(v -> showDatePicker());
        binding.tvTime.setOnClickListener(v -> showTimePicker());

        binding.btnAddAlarm.setOnClickListener(v -> addAlarm());
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

        // Insert into DB via handler
        mAlarmUpdateHandler.asyncAddAlarm(alarm);

//        Toast.makeText(this, "Alarm added", Toast.LENGTH_SHORT).show();
    }
}
