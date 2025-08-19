// SPDX-License-Identifier: GPL-3.0-only

package com.best.alarmclock;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.best.deskclock.R;
import com.best.deskclock.settings.MaterialYouNextAlarmWidgetSettingsFragment;
import com.best.deskclock.widget.CollapsingToolbarBaseActivity;

/**
 * Class called when the user launches the widget configuration from the widget.
 */
public class WidgetConfiguration {

    public static class MaterialYouNextAlarmWidgetConfiguration extends CollapsingToolbarBaseActivity {

        @Override
        protected String getActivityTitle() {
            return getString(R.string.digital_widget);
        }

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            showFragmentFromWidget(this, savedInstanceState, new MaterialYouNextAlarmWidgetSettingsFragment());
        }
    }

    public static void showFragmentFromWidget(AppCompatActivity activity, Bundle savedInstanceState, Fragment fragment) {
        WidgetUtils.isLaunchedFromWidget = true;

        if (savedInstanceState == null) {
            activity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content_frame, fragment)
                    .disallowAddToBackStack()
                    .commit();
        }
    }

}
