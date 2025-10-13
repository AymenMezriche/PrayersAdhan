package com.better.alarm.alarmapi

import com.better.alarm.data.PrayerTime
import java.util.Calendar

interface PrayerTimesProvider {
    fun getTodayPrayerTimes(
        date: Calendar,
        callback: (Result<List<PrayerTime>>) -> Unit)
}
