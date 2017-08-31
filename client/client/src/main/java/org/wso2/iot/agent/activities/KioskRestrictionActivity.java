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
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.wso2.iot.agent.R;
import org.wso2.iot.agent.utils.Constants;

/**
 * This class handles device lock-down functionality.
 */
public class KioskRestrictionActivity extends Activity {
    private TextView textViewWipeData;
    TextView textViewKiosk;
    TextView textViewLaunch;
    int kioskExit;
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kiosk_lock_down);
        textViewLaunch = (TextView) findViewById(R.id.textViewLaunch);
        textViewLaunch.setText(Constants.DEVICE_LOCK_NOTICE);
        textViewKiosk = (TextView) findViewById(R.id.textViewKiosk);
        if(Constants.COSU_SECRET_EXIT){
            textViewKiosk.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    kioskExit++;
                    Toast.makeText(getApplicationContext(),Constants.DEVICE_LOCK_NOTICE,Toast.LENGTH_LONG).show();
                    if(kioskExit == 6){
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            stopLockTask();
                        }
                        finish();
                    }
                }
            });
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startLockTask();
        }

        textViewWipeData = (TextView) this.findViewById(R.id.textViewWipeData);
        if(Constants.DEFAULT_OWNERSHIP == Constants.OWNERSHIP_COSU && Constants.DISPLAY_WIPE_DEVICE_BUTTON){
            textViewWipeData.setVisibility(View.VISIBLE);
            textViewWipeData.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    new AlertDialog.Builder(KioskRestrictionActivity.this)
                            .setTitle(getString(R.string.app_name))
                            .setMessage(R.string.wipe_confirmation)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    DevicePolicyManager devicePolicyManager = (DevicePolicyManager)
                                            getApplicationContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                                        devicePolicyManager.
                                                wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE |
                                                        DevicePolicyManager.WIPE_RESET_PROTECTION_DATA);
                                    }
                                }})
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                }
            });
        }
    }
    @Override
    public void onBackPressed() {

    }
}