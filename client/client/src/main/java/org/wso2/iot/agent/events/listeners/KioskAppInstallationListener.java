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

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.services.AgentDeviceAdminReceiver;
import org.wso2.iot.agent.services.operation.OperationManagerCOSU;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

public class KioskAppInstallationListener extends BroadcastReceiver {
    private static final String TAG = OperationManagerCOSU.class.getSimpleName();
    private DevicePolicyManager devicePolicyManager;
    private ComponentName cdmfDeviceAdmin;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onReceive(Context context, Intent intent) {
        devicePolicyManager =
                (DevicePolicyManager) context.getApplicationContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
        cdmfDeviceAdmin = AgentDeviceAdminReceiver.getComponentName(context.getApplicationContext());

        String lockTaskPackages;
        String packageName = intent.getData().getEncodedSchemeSpecificPart();
        if (!Constants.ALLOW_MULTIPLE_APPS_IN_COSU_MODE) {
            Preference.putString(context.getApplicationContext(),
                    Constants.KIOSK_APP_PACKAGE_NAME, packageName);
            lockTaskPackages =
                    context.getApplicationContext().getPackageName()
                            + context.getString(R.string.kiosk_application_package_split_regex)
                            + packageName;
        } else {
            String currentList = Preference.getString(context, Constants.KIOSK_APP_PACKAGE_NAME);
            if (currentList == null) {
                Preference.putString(context.getApplicationContext(),
                        Constants.KIOSK_APP_PACKAGE_NAME, packageName);
                lockTaskPackages =
                        context.getApplicationContext().getPackageName()
                                + context.getString(R.string.kiosk_application_package_split_regex)
                                + packageName;
            } else {
                if (!currentList.contains(packageName)) {
                    Preference.putString(context.getApplicationContext(),
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
                    Preference.putString(context.getApplicationContext(),
                            Constants.KIOSK_APP_PACKAGE_NAME, newPackageList);
                    lockTaskPackages = context.getApplicationContext().getPackageName() +
                            context.getString(R.string.kiosk_application_package_split_regex) +
                            newPackageList;
                }
            }
        }
        devicePolicyManager.setLockTaskPackages(cdmfDeviceAdmin,
                lockTaskPackages.split(context.getString(R.string.kiosk_application_package_split_regex)));
        addIfPermissionEnforcementExist(packageName, context);
        launchKioskApp(context, packageName);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void addIfPermissionEnforcementExist(String installedPackageName, Context context) {
        JSONObject permittedApp;
        String permittedPackageName;
        String permissionName;
        int permissionType;

        try {
            JSONArray permittedAppsData = new JSONArray(Preference.getString(context,Constants.RuntimePermissionPolicy.PERMITTED_APP_DATA));
            for(int i = 0; i <permittedAppsData.length(); i++) {
                permittedApp = new JSONObject(permittedAppsData.getString(i));
                permittedPackageName = permittedApp.getString(Constants.RuntimePermissionPolicy.PACKAGE_NAME);
                if(permittedPackageName.equals(installedPackageName)) {
                    permissionName = permittedApp.getString(Constants.RuntimePermissionPolicy.PERMISSION_NAME);
                    permissionType = Integer.parseInt(permittedApp.getString(Constants.RuntimePermissionPolicy.PERMISSION_TYPE));

                    if(permissionName.equals(Constants.RuntimePermissionPolicy.ALL_PERMISSIONS)){
                        String[] permissionList = context.getResources().getStringArray(R.array.runtime_permission_list_array);
                        for(String permission: permissionList){
                            devicePolicyManager.setPermissionGrantState(cdmfDeviceAdmin, permittedPackageName, permission, permissionType);
                        }
                    }
                    devicePolicyManager.setPermissionGrantState(cdmfDeviceAdmin, permittedPackageName, permissionName, permissionType);
                    break;
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON format..");
        }
    }

    private void launchKioskApp(Context context, String packageName) {
        Intent launchIntent = context.getApplicationContext().getPackageManager()
                .getLaunchIntentForPackage(packageName);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (launchIntent != null) {
            context.getApplicationContext().startActivity(launchIntent);
        }
    }

}