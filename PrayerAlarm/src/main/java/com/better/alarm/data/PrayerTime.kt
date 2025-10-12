package com.better.alarm.data

import java.io.Serializable

/**
 * Represents a single prayer time.
 * @param name The name of the prayer (e.g., Fajr, Dhuhr)
 * @param hour The hour in 24h format (0..23)
 * @param minute The minute (0..59)
 */
data class PrayerTime(
    val name: String,
    val hour: Int,
    val minute: Int
) : Serializable
