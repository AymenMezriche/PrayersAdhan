package com.best.adhanclock;

import static androidx.room.OnConflictStrategy.REPLACE;

import android.database.Observable;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;
import io.reactivex.Single;

@Dao
public interface DayPrayerTimesDao {

    @Query("SELECT * FROM PrayerTimesTable")
    List<DayPrayerTimes> getAll();

    @Query("SELECT * FROM PrayerTimesTable WHERE date=:date")
    Single<DayPrayerTimes> getByDateSingle(String date);

    @Query("SELECT * FROM PrayerTimesTable WHERE date=:date")
    DayPrayerTimes getByDate(String date);

    /*
    @Query("SELECT * FROM PrayerTimesTable WHERE date in (:days)")
    Observable<List<DayPrayerTimes>> getDaysForCurrentWeek(String[] days);
*/
    @Query("SELECT COUNT(*) FROM PrayerTimesTable WHERE date=:date")
     int getByDateCount(String date);

    @Query("SELECT * FROM PrayerTimesTable WHERE date=:date")
    DayPrayerTimes getByDateWithoutObserver(String date);

    @Insert(onConflict = REPLACE)
    void insert(DayPrayerTimes dayPrayerTimes);

    @Insert(onConflict = REPLACE)
    void insert(List<DayPrayerTimes> dayPrayerTimesList);

    @Query("DELETE FROM prayertimestable")
    void deleteAll();

    @Query("SELECT COUNT(*) from PrayerTimesTable")
    Integer getTableSize();
}
