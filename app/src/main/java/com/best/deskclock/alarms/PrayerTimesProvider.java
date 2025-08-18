package com.best.deskclock.alarms;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class PrayerTimesProvider {
    // Example test prayer times for 19-23 Aug 2025
    private static final Map<String, Map<String, int[]>> TEST_PRAYER_TIMES = new HashMap<String, Map<String, int[]>>() {{
        put("19-2025", new HashMap<String, int[]>() {{
            put("Fajr", new int[]{4, 30});
            put("Dhuhr", new int[]{12, 45});
            put("Asr", new int[]{16, 15});
            put("Maghrib", new int[]{19, 25});
            put("Isha", new int[]{21, 0});
        }});
        put("20-2025", new HashMap<String, int[]>() {{
            put("Fajr", new int[]{4, 31});
            put("Dhuhr", new int[]{12, 44});
            put("Asr", new int[]{16, 16});
            put("Maghrib", new int[]{19, 24});
            put("Isha", new int[]{21, 1});
        }});
        put("21-2025", new HashMap<String, int[]>() {{
            put("Fajr", new int[]{4, 32});
            put("Dhuhr", new int[]{12, 44});
            put("Asr", new int[]{16, 16});
            put("Maghrib", new int[]{19, 23});
            put("Isha", new int[]{21, 2});
        }});
        put("22-2025", new HashMap<String, int[]>() {{
            put("Fajr", new int[]{4, 33});
            put("Dhuhr", new int[]{12, 43});
            put("Asr", new int[]{16, 17});
            put("Maghrib", new int[]{19, 22});
            put("Isha", new int[]{21, 3});
        }});
        put("23-2025", new HashMap<String, int[]>() {{
            put("Fajr", new int[]{4, 34});
            put("Dhuhr", new int[]{12, 43});
            put("Asr", new int[]{16, 18});
            put("Maghrib", new int[]{19, 21});
            put("Isha", new int[]{21, 4});
        }});
    }};

    /**
     * Returns hour/minute for a given alarm day + label (prayer name).
     */
    public static int[] getPrayerTimeForTest(Calendar day, String label) {
        // Example key: "19-2025"
        String key = day.get(Calendar.DAY_OF_MONTH) + "-" + day.get(Calendar.YEAR);

        if (TEST_PRAYER_TIMES.containsKey(key)) {
            Map<String, int[]> prayersForDay = TEST_PRAYER_TIMES.get(key);
            if (prayersForDay.containsKey(label)) {
                return prayersForDay.get(label);
            }
        }
        // Default fallback
        return new int[]{day.get(Calendar.HOUR_OF_DAY), day.get(Calendar.MINUTE)};
    }


}
