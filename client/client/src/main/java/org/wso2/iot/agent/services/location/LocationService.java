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

package org.wso2.iot.agent.services.location;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.gson.Gson;

import org.wso2.iot.agent.AgentReceptionActivity;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

/**
 * This class holds the function implementations of the location service.
 */
public class LocationService extends Service implements LocationListener {

    private static final String TAG = LocationService.class.getSimpleName();

    private static Location location = null;
    private LocationManager locationManager = null;
    private Context context;
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 100;
    private static final long MIN_TIME_BW_UPDATES = 1000 * 60;
    private String provider = null;
    private boolean isUpdateRequested = false;
    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        LocationService getService() {
            return LocationService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        context = getApplicationContext();
        if (locationManager == null) {
            locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);
        }
        class LooperThread extends Thread {
            public void run() {
                if (Looper.myLooper() == null) {
                    Looper.prepare();
                }
                LocationService.this.setLocation();
            }
        }
        new LooperThread().run();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationManager != null && isUpdateRequested) {
            if (Build.VERSION.SDK_INT >= 23
                    && ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                displayPermissionMissingNotification();
                return;
            }
            try {
                locationManager.removeUpdates(this);
                isUpdateRequested = false;
            } catch (Exception ex) {
                Log.e(TAG, "fail to remove location listeners", ex);
            }
        }
    }

    /**
     * In this method, it gets the latest location updates from gps/ network.
     */
    private void setLocation() {
        if (locationManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= 23
                        && ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        && ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    displayPermissionMissingNotification();
                    return;
                }
                Criteria criteria = new Criteria();
                criteria.setAccuracy(Criteria.ACCURACY_FINE);
                criteria.setAltitudeRequired(false);
                criteria.setBearingRequired(false);
                provider = locationManager.getBestProvider(criteria, true);
                if (provider != null) {
                    if (isUpdateRequested) {
                        locationManager.removeUpdates(this);
                    }
                    if (Constants.DEBUG_MODE_ENABLED){
                        Log.d(TAG, "Requesting locations from provider: " + provider);
                    }
                    locationManager.requestLocationUpdates(provider, MIN_TIME_BW_UPDATES,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                    isUpdateRequested = true;
                    location = locationManager.getLastKnownLocation(provider);
                    if (location != null) {
                        Preference.putString(context, context.getResources().getString(R.string.shared_pref_location),
                                new Gson().toJson(location));
                    }
                } else {
                    Log.w(TAG, "No suitable location providers found");
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "No network/GPS Switched off.", e);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            Preference.putString(context, context.getResources().getString(R.string.shared_pref_location),
                                 new Gson().toJson(location));
            if (Constants.DEBUG_MODE_ENABLED){
                Log.d(TAG, "Location changed> lat:" + location.getLatitude() + " lon:" + location.getLongitude() + " provider:" + location.getProvider());
            }
        }
        LocationService.location = location;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (Constants.DEBUG_MODE_ENABLED){
            Log.d(TAG, "Status changed to: " + status + " Provider: " + provider);
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        if (Constants.DEBUG_MODE_ENABLED){
            Log.d(TAG, "Provider enabled: " + provider);
        }
        setLocation();
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (Constants.DEBUG_MODE_ENABLED){
            Log.d(TAG, "Provider disabled: " + provider);
        }
        if (this.provider != null && this.provider.equals(provider)) {
            setLocation();
        }
    }

    private void displayPermissionMissingNotification(){
        CommonUtils.displayNotification(context,
                R.drawable.notification,
                context.getResources().getString(R.string.title_need_permissions),
                context.getResources().getString(R.string.msg_need_permissions),
                AgentReceptionActivity.class,
                Constants.PERMISSION_MISSING,
                Constants.PERMISSION_MISSING_NOTIFICATION_ID);
    }
}
