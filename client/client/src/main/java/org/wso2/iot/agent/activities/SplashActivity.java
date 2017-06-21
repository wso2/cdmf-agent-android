/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.iot.agent.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.wso2.iot.agent.BuildConfig;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.api.DeviceState;
import org.wso2.iot.agent.services.EnrollmentService;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;
import org.wso2.iot.agent.utils.Response;

import java.util.Calendar;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class SplashActivity extends Activity {

    private static final String TAG = SplashActivity.class.getSimpleName();

    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;
    private static Class<?> instantiatedActivityClass = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        if (Constants.AUTO_ENROLLMENT_BACKGROUND_SERVICE_ENABLED) {
            Intent intent = new Intent(this, EnrollmentService.class);
            startService(intent);
            Log.i(TAG, "Enrollment service started");
        } else if (instantiatedActivityClass != null) {
            startActivity();
        } else {
            String footerText = String.format(
                    getResources().getString(R.string.footer_text),
                    BuildConfig.VERSION_NAME,
                    Calendar.getInstance().get(Calendar.YEAR)
            );
            TextView textViewFooter = (TextView) findViewById(R.id.textViewFooter);
            textViewFooter.setText(footerText);
            View decorView = getWindow().getDecorView();
            // Hide the status bar.
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
            /* New Handler to start the WorkProfileSelectionActivity
             * and close this Splash-Screen after some seconds.*/
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    startActivity();
                }
            }, AUTO_HIDE_DELAY_MILLIS);
        }
    }

    private void startActivity() {
        if (Preference.hasPreferenceKey(this, Constants.TOKEN_EXPIRED) ||
                (Constants.DEFAULT_HOST != null && !Preference.getBoolean(this, Constants.PreferenceFlag.DEVICE_ACTIVE))) {
            instantiatedActivityClass = AuthenticationActivity.class;
        } else if (Constants.OWNERSHIP_COSU.equals(Constants.DEFAULT_OWNERSHIP)) {
            instantiatedActivityClass = ServerConfigsActivity.class;
        } else if (Preference.getBoolean(this, Constants.PreferenceFlag.DEVICE_ACTIVE)) {
            instantiatedActivityClass = AlreadyRegisteredActivity.class;
        } else if (hasWorkProfileCapability()) {
            instantiatedActivityClass = WorkProfileSelectionActivity.class;
        } else {
            instantiatedActivityClass = ServerConfigsActivity.class;
        }
        Intent intent = new Intent(getApplicationContext(), instantiatedActivityClass);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    /**
     * Check capability to have a separate managed profile.
     */
    private boolean hasWorkProfileCapability() {
        DeviceState state = new DeviceState(this);
        Response androidForWorkCompatibility = state.evaluateAndroidForWorkCompatibility();
        return androidForWorkCompatibility.getCode();
    }

}
