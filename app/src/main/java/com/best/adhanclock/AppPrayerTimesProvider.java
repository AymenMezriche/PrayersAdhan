package com.best.adhanclock;

import android.content.Context;


import androidx.annotation.NonNull;

import com.better.alarm.alarmapi.PrayerTimesProvider;
import com.better.alarm.alarmapi.Result;
import com.better.alarm.data.PrayerTime;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class AppPrayerTimesProvider implements PrayerTimesProvider {
    private final Context context;

    public AppPrayerTimesProvider(Context context) {
        this.context = context;
    }

    private List<PrayerTime> fromDayPrayerTimes(DayPrayerTimes dayPrayerTimes) {
        List<PrayerTime> prayerTimes = new ArrayList<>();
        prayerTimes.add(new PrayerTime("Fajr", parseHour(dayPrayerTimes.getFajr()), parseMinute(dayPrayerTimes.getFajr())));
        prayerTimes.add(new PrayerTime("Dhuhr", parseHour(dayPrayerTimes.getDhuhr()), parseMinute(dayPrayerTimes.getDhuhr())));
        prayerTimes.add(new PrayerTime("Asr", parseHour(dayPrayerTimes.getAsr()), parseMinute(dayPrayerTimes.getAsr())));
        prayerTimes.add(new PrayerTime("Maghrib", parseHour(dayPrayerTimes.getMaghrib()), parseMinute(dayPrayerTimes.getMaghrib())));
        prayerTimes.add(new PrayerTime("Isha", parseHour(dayPrayerTimes.getIsha()), parseMinute(dayPrayerTimes.getIsha())));
        return prayerTimes;
    }

    private int parseHour(String time) {
        return Integer.parseInt(time.split(":")[0]);
    }

    private int parseMinute(String time) {
        return Integer.parseInt(time.split(":")[1]);
    }
    @Override
    public void getTodayPrayerTimes(@NotNull Calendar day, @NotNull Function1<? super @NotNull Result<@NotNull List<@NotNull PrayerTime>>, @NotNull Unit> callback) {

        new Thread(() -> {
            try {
                String dayKey = String.format("%02d-%02d-%04d",
                        day.get(Calendar.DAY_OF_MONTH),
                        day.get(Calendar.MONTH) + 1,
                        day.get(Calendar.YEAR));

                AppDatabase db = DatabaseClient.getInstance(context).getAppDatabase();
                DayPrayerTimesDao dao = db.getDayPrayerTimesDao();
                DayPrayerTimes dayPrayerTimes = dao.getByDate(dayKey);

                if (dayPrayerTimes != null) {
                    List<PrayerTime> result = fromDayPrayerTimes(dayPrayerTimes);
                    callback.invoke(Result.success(result));
                } else {
                    callback.invoke(Result.error(new Exception("No prayer times for today")));
                }

            } catch (Exception e) {
                // FIX: Also specify the generic type here
                callback.invoke(Result.Companion.<List<PrayerTime>>error(e));
            }
        }).start();

    }
}
