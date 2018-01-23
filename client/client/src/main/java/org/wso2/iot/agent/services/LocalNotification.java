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
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import org.wso2.iot.agent.R;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

import java.util.Locale;

/**
 * Local notification is a communication mechanism that essentially,
 * polls to server based on a predefined to retrieve pending data.
 */
public class LocalNotification {

    private static final String TAG = LocalNotification.class.getSimpleName();
    private static final int DEFAULT_INT_VALUE = 0;

    public static void startPolling(Context context) {
        int interval = Preference.getInt(context, context.getResources().getString(R.string.shared_pref_frequency));
        if (interval == DEFAULT_INT_VALUE) {
            interval = Constants.DEFAULT_INTERVAL;
        }
        stopPolling(context);
        if (!Preference.getBoolean(context, Constants.PreferenceFlag.LOCAL_NOTIFIER_INVOKED_PREF_KEY)) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                Log.e(TAG, "Alarm manager unavailable");
                return;
            }

            Preference.putBoolean(context, Constants.PreferenceFlag.LOCAL_NOTIFIER_INVOKED_PREF_KEY, true);
            Intent alarm = new Intent(context, AlarmReceiver.class);
            PendingIntent recurringAlarm = PendingIntent.getBroadcast(context,
                    Constants.DEFAULT_REQUEST_CODE, alarm, PendingIntent.FLAG_CANCEL_CURRENT);
            String notifierType = Preference.getString(context, Constants.PreferenceFlag.NOTIFIER_TYPE);
            if (notifierType == null) {
                notifierType = Constants.NOTIFIER_LOCAL;
            }
            long triggerAtMillis = SystemClock.elapsedRealtime() + Constants.DEFAULT_START_INTERVAL;
            if (Constants.NOTIFIER_LOCAL.equals(notifierType.trim().toUpperCase(Locale.ENGLISH))) {
                alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis,
                        interval, recurringAlarm);
                Log.i(TAG, "Polling started! Interval: " + interval);
            } else if (Constants.FCM_FALLBACK_PULL_ENABLED) {
                interval = Constants.DEFAULT_FCM_INTERVAL;
                alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, triggerAtMillis,
                        interval, recurringAlarm);
                Log.i(TAG, "Fallback Polling started! Interval: " + interval);
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME, triggerAtMillis, recurringAlarm);
                Log.i(TAG, "Scheduled single polling to sync with the server.");
            }
        }
    }

    public static void stopPolling(Context context) {
        if (Preference.getBoolean(context, Constants.PreferenceFlag.LOCAL_NOTIFIER_INVOKED_PREF_KEY)) {
            Preference.putBoolean(context, Constants.PreferenceFlag.LOCAL_NOTIFIER_INVOKED_PREF_KEY, false);
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                Log.e(TAG, "Alarm manager unavailable");
                return;
            }
            Intent alarm = new Intent(context, AlarmReceiver.class);
            PendingIntent sender = PendingIntent.getBroadcast(context,
                    Constants.DEFAULT_REQUEST_CODE, alarm, DEFAULT_INT_VALUE);
            alarmManager.cancel(sender);
            Log.i(TAG, "Polling stopped!");
        }
    }
}
