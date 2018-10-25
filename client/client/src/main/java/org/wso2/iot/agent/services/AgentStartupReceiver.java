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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.activities.SplashActivity;
import org.wso2.iot.agent.beans.Operation;
import org.wso2.iot.agent.events.EventRegistry;
import org.wso2.iot.agent.services.location.LocationService;
import org.wso2.iot.agent.services.operation.OperationProcessor;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

import java.io.IOException;
import java.util.List;

/**
 * Broadcast receiver for device boot action used to start agent local
 * notification service at device startup.
 */
public class AgentStartupReceiver extends BroadcastReceiver {

    public static final int DEFAULT_ID = -1;
    private static final String TAG = AgentStartupReceiver.class.getSimpleName();
    private Resources resources;

    @Override
    public void onReceive(final Context context, Intent intent) {
        String action = intent.getAction();
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Action received: " + action);
        }
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)
                && !isNetworkConnected(context, intent)) {
            return;
        }
        if (!Preference.getBoolean(context, Constants.PreferenceFlag.DEVICE_ACTIVE)) {
            Log.e(TAG, "Device is not active");
            return;
        }

        this.resources = context.getApplicationContext().getResources();
        int lastRebootOperationId = Preference.getInt(context, resources.getString(R.string.shared_pref_reboot_op_id));
        if (lastRebootOperationId != 0) {
            Preference.putString(context, resources.getString(R.string.shared_pref_reboot_status),
                    context.getResources().getString(R.string.operation_value_completed));
            Preference.putString(context, resources.getString(R.string.shared_pref_reboot_result),
                    context.getResources().getString(R.string.operation_value_completed));
        }

        setDeviceLock(context.getApplicationContext());

        if (!EventRegistry.eventListeningStarted) {
            EventRegistry registerEvent = new EventRegistry(context.getApplicationContext());
            registerEvent.register();
        }

        if (!CommonUtils.isServiceRunning(context, LocationService.class)) {
            Intent serviceIntent = new Intent(context, LocationService.class);
            context.startService(serviceIntent);
        }

        if (Preference.getBoolean(context, Constants.PreferenceFlag.REGISTERED)) {
            LocalNotification.startPolling(context);
        }

        /**
         * Clear the device info and wifi info payloads stored in shared preference
         * when the device restarts.
         */
        if(Preference.getString(context, Constants.LAST_DEVICE_INFO_SHARED_PREF) != null) {
            Preference.putString(context, Constants.LAST_DEVICE_INFO_SHARED_PREF,null);
        }

        if(Preference.getString(context, Constants.LAST_WIFI_SCAN_RESULT_SHARED_PREF) != null) {
            Preference.putString(context, Constants.LAST_WIFI_SCAN_RESULT_SHARED_PREF,null);
        }

        if(Preference.getString(context, Constants.LAST_APP_LIST_SHARED_PREF) != null) {
            Preference.putString(context, Constants.LAST_APP_LIST_SHARED_PREF,null);
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Constants.AGENT_UPDATED_BROADCAST_ACTION.equals(action)) {
            if (Constants.OWNERSHIP_COSU.equals(Constants.DEFAULT_OWNERSHIP)) {
                Preference.putBoolean(context.getApplicationContext(), Constants.AGENT_FRESH_START, true);
                Intent i = new Intent(context, SplashActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            String policyBundle = Preference.getString(context, Constants.PreferenceFlag.APPLIED_POLICY);
            if (policyBundle != null) {
                PolicyOperationsMapper operationsMapper = new PolicyOperationsMapper();
                ObjectMapper mapper = new ObjectMapper();
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
                try {
                    List<Operation> operations = mapper.readValue(
                            policyBundle,
                            mapper.getTypeFactory().
                                    constructCollectionType(List.class, Operation.class));
                    OperationProcessor operationProcessor = new OperationProcessor(context);
                    for (org.wso2.iot.agent.beans.Operation op : operations) {
                        op = operationsMapper.getOperation(op);
                        operationProcessor.doTask(op);
                    }
                    if (Constants.DEBUG_MODE_ENABLED) {
                        Log.d(TAG, "Policy applied on agent startup.");
                    }
                } catch (AndroidAgentException | IOException e) {
                    Log.e(TAG, "Error occurred when applying stored policies.", e);
                }
            }
        }
    }

    private boolean isNetworkConnected(Context context, Intent intent) {
        if (intent.getExtras() != null) {
            final ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return false;
            }
            final NetworkInfo ni = connectivityManager.getActiveNetworkInfo();

            if (ni != null && ni.isConnectedOrConnecting()) {
                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, "Network " + ni.getTypeName() + " connected");
                }
                return true;
            } else if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, Boolean.FALSE)) {
                Log.i(TAG, "There's no network connectivity");
            }
        }
        return false;
    }

    /**
     * Set device lock on device startup if enabled.
     *
     * @param context - Application context.
     */
    private void setDeviceLock(Context context) {
        boolean isLocked = Preference.getBoolean(context, Constants.IS_LOCKED);
        String lockMessage = Preference.getString(context, Constants.LOCK_MESSAGE);

        if (lockMessage == null || lockMessage.isEmpty()) {
            lockMessage = resources.getString(R.string.txt_lock_activity);
        }

        if (isLocked) {
            Operation lockOperation = new Operation();
            lockOperation.setId(DEFAULT_ID);
            lockOperation.setCode(Constants.Operation.DEVICE_LOCK);
            try {
                JSONObject payload = new JSONObject();
                payload.put(Constants.ADMIN_MESSAGE, lockMessage);
                payload.put(Constants.IS_HARD_LOCK_ENABLED, true);
                lockOperation.setPayLoad(payload.toString());
                OperationProcessor operationProcessor = new OperationProcessor(context);
                operationProcessor.doTask(lockOperation);
            } catch (AndroidAgentException e) {
                Log.e(TAG, "Error occurred while executing hard lock operation at the device startup");
            } catch (JSONException e) {
                Log.e(TAG, "Error occurred while building hard lock operation payload");
            }
        }
    }

}