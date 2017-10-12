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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

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

import java.util.Locale;

/**
 * Broadcast receiver for device boot action used to start agent local
 * notification service at device startup.
 */
public class AgentStartupReceiver extends BroadcastReceiver {
    public static final int DEFAULT_INT_VALUE = 0;
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
        setRecurringAlarm(context.getApplicationContext());
        if (!EventRegistry.eventListeningStarted) {
            EventRegistry registerEvent = new EventRegistry(context.getApplicationContext());
            registerEvent.register();
        }
        if (!CommonUtils.isServiceRunning(context, LocationService.class)) {
            Intent serviceIntent = new Intent(context, LocationService.class);
            context.startService(serviceIntent);
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Constants.AGENT_UPDATED_BROADCAST_ACTION.equals(action)) {
            if (Constants.OWNERSHIP_COSU.equals(Constants.DEFAULT_OWNERSHIP)) {
                Preference.putBoolean(context.getApplicationContext(), Constants.AGENT_FRESH_START, true);
                Intent i = new Intent(context, SplashActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }
        }
    }

    private boolean isNetworkConnected(Context context, Intent intent) {
        if (intent.getExtras() != null) {
            final ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
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
     * Initiates device notifier on device startup.
     *
     * @param context - Application context.
     */
    private void setRecurringAlarm(Context context) {
        String mode = Preference.getString(context, Constants.PreferenceFlag.NOTIFIER_TYPE);
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

        int interval = Preference.getInt(context, context.getResources().getString(R.string.shared_pref_frequency));
        if (interval == DEFAULT_INT_VALUE) {
            interval = Constants.DEFAULT_INTERVAL;
        }

        if (mode == null) {
            mode = Constants.NOTIFIER_LOCAL;
        }

        if (Preference.getBoolean(context, Constants.PreferenceFlag.REGISTERED) && Constants.NOTIFIER_LOCAL.equals(
                mode.trim().toUpperCase(Locale.ENGLISH))) {

            Intent alarmIntent = new Intent(context, AlarmReceiver.class);
            PendingIntent recurringAlarmIntent =
                    PendingIntent.getBroadcast(context,
                            Constants.DEFAULT_REQUEST_CODE,
                            alarmIntent,
                            PendingIntent.FLAG_CANCEL_CURRENT);
            AlarmManager alarmManager =
                    (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, Constants.DEFAULT_START_INTERVAL,
                    interval, recurringAlarmIntent);
            Log.i(TAG, "Setting up alarm manager for polling every " + interval + " milliseconds.");
        }
    }

}