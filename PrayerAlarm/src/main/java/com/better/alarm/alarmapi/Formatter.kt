package com.better.alarm.alarmapi

import java.util.Calendar

object Formatter {
    fun todayString(): String {
        val cal = Calendar.getInstance()
        return String.format(
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1, // MONTH is 0-based
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }
}