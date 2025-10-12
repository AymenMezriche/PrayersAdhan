package com.best.adhanclock;

import android.content.Context;

import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DatabaseClient {
 
    private static DatabaseClient mInstance;
    
    //our app database object
    private final AppDatabase appDatabase;
 
    private DatabaseClient(Context mCtx) {
        //creating the app database with Room database builder
        //MyToDos is the name of the database
        appDatabase = Room.databaseBuilder(mCtx,AppDatabase.class,"PrayerTimes")
                .build();
    }
 
    public static synchronized DatabaseClient getInstance(Context mCtx) {
        if (mInstance == null) {
            mInstance = new DatabaseClient(mCtx);
        }
        return mInstance;
    }
 
    public AppDatabase getAppDatabase() {
        return appDatabase;
    }

}

