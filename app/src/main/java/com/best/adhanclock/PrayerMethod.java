package com.best.adhanclock;

public class PrayerMethod {
    private int methodId;
    private String arabicName;

    public PrayerMethod(int methodId, String arabicName) {
        this.methodId = methodId;
        this.arabicName = arabicName;
    }

    // Getters and Setters
    public int getMethodId() {
        return methodId;
    }

    public void setMethodId(int methodId) {
        this.methodId = methodId;
    }

    public String getArabicName() {
        return arabicName;
    }

    public void setArabicName(String arabicName) {
        this.arabicName = arabicName;
    }
}