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
package org.wso2.iot.agent.services.kiosk;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import org.wso2.iot.agent.KioskActivity;
import org.wso2.iot.agent.activities.KioskRestrictionActivity;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

import java.util.Calendar;

/**
 * This class handles generating alarms to lock and unlock device to adhere with restriction of
 * operation time.
 */
public class KioskAlarmReceiver extends WakefulBroadcastReceiver{
    private String TAG = KioskAlarmReceiver.class.getSimpleName();

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent service;
        Activity currentlyLockedActivity;
        if (intent.getBooleanExtra(Constants.Operation.ENABLE_LOCK, false)){
            Log.d(TAG,"OnReceive EnableLock false." );
            currentlyLockedActivity = new KioskActivity();
            currentlyLockedActivity.stopLockTask();
            service = new Intent(context, KioskLockDownService.class);
            buildAlarm(context, false, false);
            startWakefulService(context, service);
        }
        else if (!intent.getBooleanExtra(Constants.Operation.ENABLE_LOCK, true)){
            Log.d(TAG,"OnReceive enableLock true" );
            currentlyLockedActivity = new KioskRestrictionActivity();
            currentlyLockedActivity.stopLockTask();
            service = new Intent(context, KioskUnlockService.class);
            buildAlarm(context, true, false);
            startWakefulService(context, service);
        }
    }

    /**
     * Initial call to start the Alarm generation.
     * @param context Context of the application.
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void startAlarm(Context context){
        buildAlarm(context, true, true);
        Log.d(TAG,"Starting Alarm with initial Lock operation" );
    }

    /**
     * Generic function to generate alarm to both lock and unlock scenarios.
     * @param context Context of the application.
     * @param enableLock Set whether its a lock alarm or unlock alarm.
     * @param isInitialRun Set if its the initial alarm or not.
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void buildAlarm(Context context, boolean enableLock, boolean isInitialRun){
        AlarmManager alarmManager;
        PendingIntent pendingIntent;
        Calendar calendar;
        int separator;
        int currentTime;
        alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(Preference.getBoolean(context, Constants.PreferenceCOSUProfile.ENABLE_LOCKDOWN)) {
            int freezeTime = Preference.getInt(context, Constants.PreferenceCOSUProfile.FREEZE_TIME);
            int releaseTime = Preference.getInt(context, Constants.PreferenceCOSUProfile.RELEASE_TIME);

            Intent receiverIntent = new Intent(context, KioskAlarmReceiver.class);
            receiverIntent.putExtra(Constants.Operation.ENABLE_LOCK, enableLock);

            if(!enableLock) {
                /* if releaseTime is after freezeTime, then needs to set unlock alarm in next day since
                 device operation time extends to next day as well. */
                calendar = getCalendar(releaseTime, freezeTime > releaseTime);
                //receiverIntent.setAction("unlock");
                //separator = 75;
            }
            else if(isInitialRun) {
                currentTime = getCurrentTimeInMinutes();
                /* if currentTime is larger than freezeTime, that means freeze time of current day is
                already passed. Therefore need to set alarm in next day. */
                calendar = getCalendar(freezeTime, currentTime > freezeTime );
                //receiverIntent.setAction("lock");
                //separator = 50;
            }
            else {
                /* if releaseTime is larger than freezeTime, that means the next freezeTime comes in
                next day. Therefore need to set alarm in next day. */
                calendar = getCalendar(freezeTime, releaseTime > freezeTime);
                //receiverIntent.setAction("lock");
                //separator = 50;
            }
            pendingIntent = PendingIntent.
                    getBroadcast(context, 55, receiverIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
            Log.d(TAG,"Build alarm to restrict device to " + freezeTime+ ":" +releaseTime );
        }
    }

    /**
     * Get calender object set with given time.
     * @param time Time need to set in the calendar.
     * @param increaseDate If needs to set alarm in next day.
     * @return calender object with given time set.
     */
    private Calendar getCalendar(int time, boolean increaseDate) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, time / 60);
        calendar.set(Calendar.MINUTE, time % 60);
        calendar.set(Calendar.SECOND, 0);
        if (increaseDate) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        Log.d(TAG,"Calendar set to " + time/60 + ":" + time%60 );
        return calendar;
    }

    /**
     * Get current time in minutes.
     * @return current system time in minutes.
     */
    private int getCurrentTimeInMinutes(){
        int hours;
        int minutes;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        hours = calendar.get(Calendar.HOUR_OF_DAY);
        minutes = calendar.get(Calendar.MINUTE);
        return hours * 60 + minutes;
    }

}