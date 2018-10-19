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
package org.wso2.emm.agent.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.emm.agent.AndroidAgentException;
import org.wso2.emm.agent.R;
import org.wso2.emm.agent.beans.Operation;
import org.wso2.emm.agent.events.EventRegistry;
import org.wso2.emm.agent.services.operation.OperationProcessor;
import org.wso2.emm.agent.utils.Constants;
import org.wso2.emm.agent.utils.Preference;

import java.util.Locale;

/**
 * Broadcast receiver for device boot action used to start agent local
 * notification service at device startup.
 */
public class DeviceStartupIntentReceiver extends BroadcastReceiver {

    public static final int DEFAULT_ID = -1;
    private Resources resources;
    private static final String TAG = "DeviceStartupIntent";

    @Override
    public void onReceive(final Context context, Intent intent) {
        this.resources = context.getApplicationContext().getResources();
        int lastRebootOperationId = Preference.getInt(context, resources.getString(R.string.shared_pref_reboot_op_id));
        if (lastRebootOperationId != 0) {
            Preference.putBoolean(context, resources.getString(R.string.shared_pref_reboot_done), true);
        }

        setRecurringAlarm(context.getApplicationContext());
        if (!EventRegistry.eventListeningStarted) {
            EventRegistry registerEvent = new EventRegistry(context.getApplicationContext());
            registerEvent.register();
        }
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

        if (mode == null) {
            mode = Constants.NOTIFIER_LOCAL;
        }

        if (Preference.getBoolean(context, Constants.PreferenceFlag.REGISTERED) && Constants.NOTIFIER_LOCAL.equals(
                mode.trim().toUpperCase(Locale.ENGLISH))) {
            LocalNotification.startPolling(context);
        }

        /**
         * Clear the device info and wifi info payloads stored in shared preference
         * when the device restarts.
         */
        if (Preference.getString(context, Constants.LAST_DEVICE_INFO_SHARED_PREF) != null) {
            Preference.putString(context, Constants.LAST_DEVICE_INFO_SHARED_PREF, null);
        }
        if (Preference.getString(context, Constants.LAST_WIFI_SCAN_RESULT_SHARED_PREF) != null) {
            Preference.putString(context, Constants.LAST_WIFI_SCAN_RESULT_SHARED_PREF, null);
        }
    }

}