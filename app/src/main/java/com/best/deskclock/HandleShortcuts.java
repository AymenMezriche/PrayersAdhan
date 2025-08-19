/*
 * Copyright (C) 2016 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.best.deskclock;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.best.deskclock.utils.LogUtils;

public class HandleShortcuts extends Activity {

    private static final LogUtils.Logger LOGGER = new LogUtils.Logger("HandleShortcuts");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();

        try {
            final String action = intent.getAction();
            if (action != null) {

            }
        } catch (Exception e) {
            LOGGER.e("Error handling intent: " + intent, e);
            setResult(RESULT_CANCELED);
        } finally {
            finish();
        }
    }
}
