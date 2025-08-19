/*
 * Copyright (C) 2016 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.best.deskclock.controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.UserManager;
import android.provider.AlarmClock;

import androidx.annotation.RequiresApi;

import com.best.deskclock.DeskClock;
import com.best.deskclock.HandleApiCalls;
import com.best.deskclock.R;
import com.best.deskclock.events.Events;
import com.best.deskclock.events.ShortcutEventTracker;
import com.best.deskclock.uidata.UiDataModel;
import com.best.deskclock.utils.LogUtils;

import java.util.Arrays;

@RequiresApi(Build.VERSION_CODES.N_MR1)
class ShortcutController {

    private final Context mContext;
    private final ComponentName mComponentName;
    private final ShortcutManager mShortcutManager;
    private final UserManager mUserManager;

    ShortcutController(Context context) {
        mContext = context;
        mComponentName = new ComponentName(mContext, DeskClock.class);
        mShortcutManager = mContext.getSystemService(ShortcutManager.class);
        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        Controller.getController().addEventTracker(new ShortcutEventTracker(mContext));
        }

    void updateShortcuts() {
        if (!mUserManager.isUserUnlocked()) {
            LogUtils.i("Skipping shortcut update because user is locked.");
            return;
        }
        try {
            final ShortcutInfo alarm = createNewAlarmShortcut();
            mShortcutManager.setDynamicShortcuts(
                    Arrays.asList(alarm));
        } catch (IllegalStateException e) {
            LogUtils.wtf(e);
        }
    }

    private ShortcutInfo createNewAlarmShortcut() {
        final Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM)
                .setClass(mContext, HandleApiCalls.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Events.EXTRA_EVENT_LABEL, R.string.label_shortcut);
        final String setAlarmShortcut = UiDataModel.getUiDataModel()
                .getShortcutId(R.string.category_alarm, R.string.action_create);
        return new ShortcutInfo.Builder(mContext, setAlarmShortcut)
                .setIcon(Icon.createWithResource(mContext, R.drawable.shortcut_new_alarm))
                .setActivity(mComponentName)
                .setShortLabel(mContext.getString(R.string.shortcut_new_alarm_short))
                .setLongLabel(mContext.getString(R.string.shortcut_new_alarm_long))
                .setIntent(intent)
                .setRank(0)
                .build();
    }
}
