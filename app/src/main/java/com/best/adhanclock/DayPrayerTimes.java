package com.best.adhanclock;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "PrayerTimesTable",indices = {@Index(value = {"date"},unique = true)})
public class DayPrayerTimes implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    private int day;
    @NonNull
    private int month;
    @NonNull
    private int year;

    @NonNull
    private String fajr;
    @NonNull
    private String Sunrise;
    @NonNull
    private String Dhuhr;
    @NonNull
    private String Asr;
    @NonNull
    private String Maghrib;
    @NonNull
    private String Isha;
    @NonNull
    private String Imsak;
    @NonNull
    private String date;


    public DayPrayerTimes() {
    }

    public DayPrayerTimes(int day, int month, int year, @NonNull String fajr, @NonNull String sunrise, @NonNull String dhuhr, @NonNull String asr, @NonNull String maghrib, @NonNull String isha, @NonNull String imsak, @NonNull String date) {
        this.day = day;
        this.month = month;
        this.year = year;
        this.fajr = fajr;
        Sunrise = sunrise;
        Dhuhr = dhuhr;
        Asr = asr;
        Maghrib = maghrib;
        Isha = isha;
        Imsak = imsak;
        this.date = date;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    @NonNull
    public String getFajr() {
        return fajr;
    }

    public void setFajr(@NonNull String fajr) {
        this.fajr = fajr;
    }

    @NonNull
    public String getSunrise() {
        return Sunrise;
    }

    public void setSunrise(@NonNull String sunrise) {
        Sunrise = sunrise;
    }

    @NonNull
    public String getDhuhr() {
        return Dhuhr;
    }

    public void setDhuhr(@NonNull String dhuhr) {
        Dhuhr = dhuhr;
    }

    @NonNull
    public String getAsr() {
        return Asr;
    }

    public void setAsr(@NonNull String asr) {
        Asr = asr;
    }

    @NonNull
    public String getMaghrib() {
        return Maghrib;
    }

    public void setMaghrib(@NonNull String maghrib) {
        Maghrib = maghrib;
    }

    @NonNull
    public String getIsha() {
        return Isha;
    }

    public void setIsha(@NonNull String isha) {
        Isha = isha;
    }

    @NonNull
    public String getImsak() {
        return Imsak;
    }

    public void setImsak(@NonNull String imsak) {
        Imsak = imsak;
    }

    @NonNull
    public String getDate() {
        return date;
    }

    public void setDate(@NonNull String date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return "DayPrayerTimes{" +
                "id=" + id +
                ", day=" + day +
                ", month=" + month +
                ", year=" + year +
                ", fajr='" + fajr + '\'' +
                ", Sunrise='" + Sunrise + '\'' +
                ", Dhuhr='" + Dhuhr + '\'' +
                ", Asr='" + Asr + '\'' +
                ", Maghrib='" + Maghrib + '\'' +
                ", Isha='" + Isha + '\'' +
                ", Imsak='" + Imsak + '\'' +
                ", date='" + date + '\'' +
                '}';
    }
}
