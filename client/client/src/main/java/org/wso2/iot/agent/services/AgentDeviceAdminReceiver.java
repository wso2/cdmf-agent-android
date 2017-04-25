/*
 * Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.iot.agent.services;

import android.annotation.TargetApi;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.ServerDetails;
import org.wso2.iot.agent.beans.ServerConfig;
import org.wso2.iot.agent.proxy.interfaces.APIResultCallBack;
import org.wso2.iot.agent.proxy.utils.Constants.HTTP_METHODS;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

import java.util.Map;

/**
 * This is the component that is responsible for actual device administration.
 * It becomes the receiver when a policy is applied. It is important that we
 * subclass DeviceAdminReceiver class here and to implement its only required
 * method onEnabled().
 */
public class AgentDeviceAdminReceiver extends DeviceAdminReceiver implements APIResultCallBack {

    private static final String TAG = AgentDeviceAdminReceiver.class.getName();
    public static final String DISALLOW_SAFE_BOOT = "no_safe_boot";

    /**
     * Called when this application is approved to be a device administrator.
     */
    @Override
    public void onEnabled(final Context context, Intent intent) {
        super.onEnabled(context, intent);

        if (!Constants.OWNERSHIP_COSU.equals(Constants.DEFAULT_OWNERSHIP)) {
            Preference.putBoolean(context, Constants.PreferenceFlag.DEVICE_ACTIVE, true);
            String notifier = Preference.getString(context, Constants.PreferenceFlag.NOTIFIER_TYPE);
            if (Constants.NOTIFIER_LOCAL.equals(notifier)) {
                LocalNotification.startPolling(context);
            }
        }
    }

    /**
     * Called when this application is no longer the device administrator.
     */
    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);

        Preference.putBoolean(context, Constants.PreferenceFlag.DEVICE_ACTIVE, false);
        if (!Constants.OWNERSHIP_COSU.equals(Constants.DEFAULT_OWNERSHIP)) {
            Toast.makeText(context, R.string.device_admin_disabled,
                    Toast.LENGTH_LONG).show();
            String regId = Preference
                    .getString(context, Constants.PreferenceFlag.REG_ID);
            if (regId != null && !regId.isEmpty()) {
                startUnRegistration(context);
            } else {
                Log.e(TAG, "Registration ID is already null");
            }
        }
    }

    /**
     * Start un-registration process.
     *
     * @param context - Application context.
     */
    public void startUnRegistration(Context context) {
        if (!Constants.OWNERSHIP_COSU.equals(Constants.DEFAULT_OWNERSHIP)) {
            String regId = Preference.getString(context, Constants.PreferenceFlag.REG_ID);
            if (regId != null && !regId.isEmpty()) {
                String serverIP = Constants.DEFAULT_HOST;
                String prefIP = Preference.getString(context, Constants.PreferenceFlag.IP);
                if (prefIP != null) {
                    serverIP = prefIP;
                }
                if (serverIP != null && !serverIP.isEmpty()) {
                    ServerConfig utils = new ServerConfig();
                    utils.setServerIP(serverIP);

                    CommonUtils.callSecuredAPI(context,
                            utils.getAPIServerURL(context) + Constants.UNREGISTER_ENDPOINT + regId,
                            HTTP_METHODS.DELETE,
                            null, AgentDeviceAdminReceiver.this,
                            Constants.UNREGISTER_REQUEST_CODE);
                    try {
                        LocalNotification.stopPolling(context);
                        CommonUtils.unRegisterClientApp(context, AgentDeviceAdminReceiver.this);
                        CommonUtils.clearAppData(context);
                    } catch (AndroidAgentException e) {
                        Log.e(TAG, "Error occurred while removing Oauth application", e);
                    }
                } else {
                    Log.e(TAG, "There is no valid IP to contact the server");
                }
            }
        }
    }

    @Override
    public void onPasswordChanged(Context context, Intent intent) {
        super.onPasswordChanged(context, intent);
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "onPasswordChanged.");
        }
    }

    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        super.onPasswordFailed(context, intent);
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "onPasswordFailed.");
        }
    }

    @Override
    public void onPasswordSucceeded(Context context, Intent intent) {
        super.onPasswordSucceeded(context, intent);
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "onPasswordSucceeded.");
        }
    }

    @Override
    public void onReceiveAPIResult(Map<String, String> arg0, int arg1) {
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Unregistered." + arg0.toString());
        }
    }

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        if (Constants.OWNERSHIP_COSU.equals(Constants.DEFAULT_OWNERSHIP)) {
            DevicePolicyManager devicePolicyManager =
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName cdmDeviceAdmin = AgentDeviceAdminReceiver.getComponentName(context);

            if (!devicePolicyManager.isAdminActive(cdmDeviceAdmin)) {
                startDeviceAdminPrompt(context, cdmDeviceAdmin);
            }

            // Setting Agent App to Lock Task mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                devicePolicyManager.setLockTaskPackages(cdmDeviceAdmin,
                        new String[]{context.getApplicationContext().getPackageName()});
            }

            // Disallowing Safe Boot
            setUserRestriction(devicePolicyManager, cdmDeviceAdmin, DISALLOW_SAFE_BOOT, true);

            //Setting permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                for(String permission: Constants.ANDROID_COSU_PERMISSIONS){
                    devicePolicyManager.setPermissionGrantState(cdmDeviceAdmin,
                            context.getApplicationContext().getPackageName(), permission,
                            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
                }
            }

            Intent launch = new Intent(context, ServerDetails.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
        } else {
            Intent launch = new Intent(context, EnableProfileActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            context.startActivity(launch);
        }

    }

    private void startDeviceAdminPrompt(Context context, final ComponentName cdmDeviceAdmin) {
        Intent deviceAdminIntent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        deviceAdminIntent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cdmDeviceAdmin);
        deviceAdminIntent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getResources().getString(R.string.device_admin_enable_alert));
        context.startActivity(deviceAdminIntent);
    }

    /**
     * Generates a {@link ComponentName} that is used throughout the app.
     *
     * @return a {@link ComponentName}
     */
    public static ComponentName getComponentName(Context context) {
        return new ComponentName(context.getApplicationContext(), AgentDeviceAdminReceiver.class);
    }

    public void onLockTaskModeEntering(Context context, Intent intent, String pkg) {
        Toast.makeText(context, "Device is locked", Toast.LENGTH_LONG).show();
    }

    public void onLockTaskModeExiting(Context context, Intent intent) {
        Toast.makeText(context, "Device is unlocked", Toast.LENGTH_SHORT).show();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setUserRestriction(DevicePolicyManager devicePolicyManager,
                                    ComponentName adminComponentName, String restriction, boolean disallow) {
        if (disallow) {
            devicePolicyManager.addUserRestriction(adminComponentName, restriction);
        } else {
            devicePolicyManager.clearUserRestriction(adminComponentName, restriction);
        }
    }

}

