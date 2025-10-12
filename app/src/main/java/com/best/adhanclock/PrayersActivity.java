package com.best.adhanclock;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.best.deskclock.alarms.AlarmUpdateHandler;
import com.better.alarmhelper.databinding.ActivityAddAlarmBinding;
import com.better.alarmhelper.databinding.ActivityGetPrayersBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class PrayersActivity extends AppCompatActivity {

    private static final String TAG = "PrayersActivity";
    private ActivityGetPrayersBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, com.better.alarmhelper.R.layout.activity_get_prayers);

        binding.btnGetPrayers.setOnClickListener(v -> {
            Thread thread = new Thread(() -> getPrayerTimesFromServer(2025, 36.3491635, 7.409499));
            thread.start();
        });

    }


    public void getPrayerTimesFromServer(int year, double latitude, double longitude) {
        //adding to database
        AppDatabase db = DatabaseClient.getInstance(PrayersActivity.this).getAppDatabase();
        DayPrayerTimesDao dao = db.getDayPrayerTimesDao();

        String apiUrl = getPrayerTimesUrl(year, latitude, longitude);

        Log.i("locationTag", "this is the request url " + apiUrl);

        RequestQueue queue = Volley.newRequestQueue(PrayersActivity.this);
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, apiUrl, null, response -> {
            try {

                runOnUiThread(() -> {
                    binding.tvResult.setText("Started saving data");
                });

                List<DayPrayerTimes> prayerTimesList = new ArrayList<>();
                JSONObject data = response.getJSONObject("data");

                for (int j = 1; j <= 12; j++) {
                    JSONArray jsonArrayMonth = data.getJSONArray(String.valueOf(j));
                    for (int i = 0; i < jsonArrayMonth.length(); i++) {
                        JSONObject timings = jsonArrayMonth.getJSONObject(i).getJSONObject("timings");

                        String fajr = timings.getString("Fajr");
                        String Sunrise = timings.getString("Sunrise");
                        String Dhuhr = timings.getString("Dhuhr");
                        String Asr = timings.getString("Asr");
                        String Maghrib = timings.getString("Maghrib");
                        String Isha = timings.getString("Isha");
                        String Imsak = timings.getString("Imsak");


                        String date = jsonArrayMonth.getJSONObject(i).getJSONObject("date").getJSONObject("gregorian").getString("date");

                        @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
                        Date date1 = dateFormat.parse(date);
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(date1);


                        int mont = cal.get(Calendar.MONTH) + 1;
                        Log.e("timing", "cal month : " + mont + "date foramt " + date);

                        DayPrayerTimes dayPrayerTimes = new DayPrayerTimes(
                                cal.get(Calendar.DAY_OF_MONTH),
                                cal.get(Calendar.MONTH) + 1,
                                cal.get(Calendar.YEAR),
                                fajr.substring(0, 5),
                                Sunrise.substring(0, 5),
                                Dhuhr.substring(0, 5),
                                Asr.substring(0, 5),
                                Maghrib.substring(0, 5),
                                Isha.substring(0, 5),
                                Imsak.substring(0, 5),
                                date
                        );
                        prayerTimesList.add(dayPrayerTimes);
                    }
                }

                new Thread(() -> {
                    try {
                        if (!prayerTimesList.isEmpty()) dao.deleteAll();
                        dao.insert(prayerTimesList);
                        runOnUiThread(() -> binding.tvResult.setText("Finished"));
                    } catch (Exception e) {
                        binding.btnGetPrayers.setEnabled(true);
                        runOnUiThread(() -> binding.tvResult.setText("Error occurred " + e.getMessage()));
                        Log.e(TAG, "error while inserting time item " + e.getMessage());
                    }
                }).start();

            } catch (JSONException | ParseException e) {
                runOnUiThread(() -> binding.tvResult.setText("Error occurred " + e.getMessage()));
                Log.e(TAG, "error while inserting time item " + e.getMessage());
                e.printStackTrace();
            }
        }, error -> {
            runOnUiThread(() -> binding.tvResult.setText("Error occurred " + error.getMessage()));
            Log.e(TAG, "error " + error.getMessage());
        });
        queue.add(jsonObjectRequest);


    }

    @NonNull
    private static String getPrayerTimesUrl(int year, double latitude, double longitude) {
        return "https://api.aladhan.com/v1/calendar/" + year + "?latitude=" + latitude + "&" + "longitude=" + longitude + "&method=" + 19;
    }

}
