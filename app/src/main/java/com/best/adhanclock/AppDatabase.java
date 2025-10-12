package com.best.adhanclock;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {DayPrayerTimes.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract DayPrayerTimesDao getDayPrayerTimesDao();
}
