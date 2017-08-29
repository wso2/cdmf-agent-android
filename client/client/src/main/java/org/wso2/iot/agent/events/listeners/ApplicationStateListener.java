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

package org.wso2.iot.agent.events.listeners;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.events.EventRegistry;
import org.wso2.iot.agent.events.beans.ApplicationStatus;
import org.wso2.iot.agent.events.beans.EventPayload;
import org.wso2.iot.agent.events.publisher.HttpDataPublisher;
import org.wso2.iot.agent.services.AgentDeviceAdminReceiver;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

import java.util.Objects;

/**
 * Listening to application state changes such as an app getting installed, uninstalled,
 * upgraded and data cleared.
 */
public class ApplicationStateListener extends BroadcastReceiver implements AlertEventListener {
    private static final String TAG = ApplicationStateListener.class.getName();
    private Context context;

    @Override
    public void startListening() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_DATA_CLEARED);
        intentFilter.addDataScheme("package");
        EventRegistry.context.registerReceiver(this, intentFilter);
    }

    @Override
    public void stopListening() {
        if (EventRegistry.context != null) {
            EventRegistry.context.unregisterReceiver(this);
        }
    }

    @Override
    public void publishEvent(String payload, String type) {
        if (Constants.EventListeners.EVENT_LISTENING_ENABLED) {
            if (Constants.EventListeners.APPLICATION_STATE_LISTENER) {
                EventPayload eventPayload = new EventPayload();
                eventPayload.setPayload(payload);
                eventPayload.setType(type);
                HttpDataPublisher httpDataPublisher = new HttpDataPublisher();
                httpDataPublisher.publish(eventPayload);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onReceive(Context context, final Intent intent) {
        String status = null;
        ApplicationStatus applicationState;
        this.context = context;
        switch (intent.getAction()) {
            case Intent.ACTION_PACKAGE_ADDED:
                status = "added";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    applyEnforcement(intent.getData().getEncodedSchemeSpecificPart());
                }
                break;
            case Intent.ACTION_PACKAGE_REMOVED:
                status = "removed";
                break;
            case Intent.ACTION_PACKAGE_REPLACED:
                status = "upgraded";
                break;
            case Intent.ACTION_PACKAGE_DATA_CLEARED:
                status = "dataCleared";
                break;
            default:
                Log.i(TAG, "Invalid intent received");
        }
        if (status != null) {
            String packageName = intent.getData().getEncodedSchemeSpecificPart();
            applicationState = new ApplicationStatus();
            applicationState.setState(status);
            applicationState.setPackageName(packageName);
            try {
                String appState = CommonUtils.toJSON(applicationState);
                publishEvent(appState, Constants.EventListeners.APPLICATION_STATE);
                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, appState);
                }
            } catch (AndroidAgentException e) {
                Log.e(TAG, "Could not convert to JSON");
            }
            if (Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction()) &&
                    Constants.AGENT_PACKAGE.equals(packageName)){
                Intent broadcastIntent = new Intent();
                broadcastIntent.setAction(Constants.AGENT_UPDATED_BROADCAST_ACTION);
                context.sendBroadcast(broadcastIntent);
            }
        }
    }

    /**
     * This method will check if the app just installed is allowed if a app white-listing policy is enforced.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void applyEnforcement(String packageName) {
        DevicePolicyManager devicePolicyManager;
        ComponentName cdmfDeviceAdmin;
        devicePolicyManager =
                (DevicePolicyManager) context.getApplicationContext().
                        getSystemService(Context.DEVICE_POLICY_SERVICE);
        cdmfDeviceAdmin = AgentDeviceAdminReceiver.getComponentName(context.getApplicationContext());
        if(devicePolicyManager.isProfileOwnerApp(cdmfDeviceAdmin.getPackageName())) {
            String permittedPackageName;
            JSONObject permittedApp;
            String permissionName;
            Boolean isAllowed = false;
            String whiteListAppsPref = Preference.
                    getString(context, Constants.AppRestriction.WHITE_LIST_APPS);
            if(whiteListAppsPref != null) {
                try {
                    JSONArray whiteListApps = new JSONArray(whiteListAppsPref);
                    for (int i = 0; i < whiteListApps.length(); i++) {
                        permittedApp = new JSONObject(whiteListApps.getString(i));
                        permittedPackageName = permittedApp.
                                getString(Constants.AppRestriction.PACKAGE_NAME);
                        if (Objects.equals(permittedPackageName, packageName)) {
                            permissionName = permittedApp.
                                    getString(Constants.AppRestriction.RESTRICTION_TYPE);
                            if (permissionName.equals(Constants.AppRestriction.WHITE_LIST)) {
                                isAllowed = true;
                                break;
                            }
                        }
                    }
                    if (!isAllowed) {
                        String disallowedApps = Preference.
                                getString(context, Constants.AppRestriction.DISALLOWED_APPS);
                        disallowedApps = disallowedApps +
                                context.getString(R.string.whitelist_package_split_regex) +
                                packageName;
                        Preference.putString(context, Constants.
                                AppRestriction.DISALLOWED_APPS, disallowedApps);
                        devicePolicyManager.
                                setApplicationHidden(cdmfDeviceAdmin, packageName, true);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Invalid JSON format..");
                }
            }
        }
    }
}
