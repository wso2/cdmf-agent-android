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
package org.wso2.iot.agent.events.listeners;

import android.annotation.TargetApi;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.wso2.iot.agent.R;
import org.wso2.iot.agent.services.AgentDeviceAdminReceiver;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;
import static android.security.KeyStore.getApplicationContext;

public class KioskAppInstallationListener extends BroadcastReceiver {

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onReceive(Context context, Intent intent) {
        DevicePolicyManager devicePolicyManager =
                (DevicePolicyManager) getApplicationContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName cdmfDeviceAdmin;
        cdmfDeviceAdmin = AgentDeviceAdminReceiver.getComponentName(getApplicationContext());

        String lockTaskPackages;
        String packageName = intent.getData().getEncodedSchemeSpecificPart();
        if (!Constants.ALLOW_MULTIPLE_APPS_IN_COSU_MODE) {
            Preference.putString(getApplicationContext(),
                    Constants.KIOSK_APP_PACKAGE_NAME, packageName);
            lockTaskPackages =
                    context.getApplicationContext().getPackageName()
                            + context.getString(R.string.kiosk_application_package_split_regex)
                            + packageName;
        } else {
            String currentList = Preference.getString(context, Constants.KIOSK_APP_PACKAGE_NAME);
            if (currentList == null) {
                Preference.putString(getApplicationContext(),
                        Constants.KIOSK_APP_PACKAGE_NAME, packageName);
                lockTaskPackages =
                        context.getApplicationContext().getPackageName()
                                + context.getString(R.string.kiosk_application_package_split_regex)
                                + packageName;
            } else {
                if (!currentList.contains(packageName)) {
                    Preference.putString(getApplicationContext(),
                            Constants.KIOSK_APP_PACKAGE_NAME, currentList
                                    + context.getString(R.string.kiosk_application_package_split_regex)
                                    + packageName);

                    lockTaskPackages =
                            context.getApplicationContext().getPackageName()
                                    + context.getString(R.string.kiosk_application_package_split_regex)
                                    + currentList
                                    + context.getString(R.string.kiosk_application_package_split_regex)
                                    + packageName;

                } else {
                    String newPackageList = "";
                    String[] currentListArr =
                            currentList.split(context.getString(R.string.kiosk_application_package_split_regex));
                    for (String appPN : currentListArr) {
                        if (!appPN.equals(packageName)) {
                            newPackageList += appPN
                                    + context.getString(R.string.kiosk_application_package_split_regex);
                        }
                    }
                    newPackageList += packageName;
                    Preference.putString(getApplicationContext(),
                            Constants.KIOSK_APP_PACKAGE_NAME, newPackageList);
                    lockTaskPackages = context.getApplicationContext().getPackageName() +
                            context.getString(R.string.kiosk_application_package_split_regex) +
                            newPackageList;
                }
            }
        }
        devicePolicyManager.setLockTaskPackages(cdmfDeviceAdmin,
                lockTaskPackages.split(context.getString(R.string.kiosk_application_package_split_regex)));
        //Setting permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : Constants.ANDROID_COSU_PERMISSIONS) {
                devicePolicyManager.setPermissionGrantState(cdmfDeviceAdmin,
                        packageName, permission,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            }
        }
        launchKioskApp(packageName);
    }

    private void launchKioskApp(String packageName) {
        Intent launchIntent = getApplicationContext().getPackageManager()
                .getLaunchIntentForPackage(packageName);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (launchIntent != null) {
            getApplicationContext().startActivity(launchIntent);
        }
    }
}