/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import android.annotation.TargetApi;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.wso2.iot.agent.R;
import org.wso2.iot.agent.WorkProfileManager;
import org.wso2.iot.agent.api.DeviceState;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;
import org.wso2.iot.agent.utils.Response;

public class WorkProfileSelectionActivity extends AppCompatActivity {

    private Context context;
    private static final int TAG_BTN_ENABLE_PROFILE = 0;
    private static final int TAG_BTN_SKIP_PROFILE = 2;
    DevicePolicyManager manager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enable_work_profile);

        context = this.getApplicationContext();
        manager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

        DeviceState state = new DeviceState(context);
        Response androidForWorkCompatibility = state.evaluateAndroidForWorkCompatibility();
        boolean isDeviceActive = Preference.getBoolean(context, Constants.PreferenceFlag.DEVICE_ACTIVE);

        if (CommonUtils.isNetworkAvailable(context)) {
            if (isDeviceActive || Constants.SKIP_WORK_PROFILE_CREATION) {
                skipToEnrollment();
            }
            if (Constants.OWNERSHIP_COSU.equals(Constants.DEFAULT_OWNERSHIP)) {
                skipToEnrollment();
            } else if (androidForWorkCompatibility.getCode()) {
                manageAndroidForWorkReception();
            } else {
                skipToEnrollment();
            }
        } else {
            Toast.makeText(context, context.getResources().getString(R.string.network_not_available_message),
                           Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void manageAndroidForWorkReception() {
        if (manager.isProfileOwnerApp(getApplicationContext().getPackageName())) {
                /* If the managed profile is already set up, we show the enrollment screen. */
            skipToEnrollment();
        } else {
            displayProfileProvisionPromptScreen();
        }
    }
    private View.OnClickListener onClickListenerButtonClicked = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            int iTag = (Integer) view.getTag();

            switch (iTag) {
                case TAG_BTN_ENABLE_PROFILE:
                    startManagedProfileManager();
                    break;
                case TAG_BTN_SKIP_PROFILE:
                    skipToEnrollment();
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * Start WorkProfileManager which configures Android Managed Profile Feature.
     */
    private void startManagedProfileManager() {
        Intent ManagedProfileManager = new Intent(getApplicationContext(), WorkProfileManager.class);
        startActivity(ManagedProfileManager);
        finish();
    }

    /**
     * Go to the Enrollment Screen if the user don't want a separate managed profile.
     */
    private void skipToEnrollment() {
        Intent intent = new Intent(context, ServerConfigsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    /**
     * Display Manage-profile provisioning prompt
     */
    private void displayProfileProvisionPromptScreen(){
        Button btnEnableMngProfile;
        Button btnSkipProfile;

        btnEnableMngProfile = (Button) findViewById(R.id.btnSetupWorkProfile);
        btnEnableMngProfile.setTag(TAG_BTN_ENABLE_PROFILE);
        btnEnableMngProfile.setOnClickListener(onClickListenerButtonClicked);

        btnSkipProfile = (Button) findViewById(R.id.btnSkipProfile);
        btnSkipProfile.setTag(TAG_BTN_SKIP_PROFILE);
        btnSkipProfile.setOnClickListener(onClickListenerButtonClicked);
    }
}
