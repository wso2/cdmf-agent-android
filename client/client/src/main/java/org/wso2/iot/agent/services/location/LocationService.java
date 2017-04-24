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

package org.wso2.iot.agent.services.location;

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
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import org.wso2.iot.agent.AgentReceptionActivity;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;

import java.util.List;

/**
 * This class holds the function implementations of the location service.
 */
public class LocationService extends Service implements LocationListener {

    private static final String TAG = LocationService.class.getSimpleName();

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
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Starting service with ID: " + startId);
        }
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                int locationSetting = Settings.Secure.getInt(context.getContentResolver(),
                        Settings.Secure.LOCATION_MODE);
                if (locationSetting == 0) {
                    CommonUtils.displayNotification(context,
                            R.drawable.notification,
                            context.getResources().getString(R.string.title_need_location),
                            context.getResources().getString(R.string.msg_need_location),
                            AgentReceptionActivity.class,
                            Constants.LOCATION_DISABLED,
                            Constants.LOCATION_DISABLED_NOTIFICATION_ID);
                }
            } catch (Settings.SettingNotFoundException e) {
                Log.w(TAG, "Location setting is not available on this device");
            }
        } else {
            Toast.makeText(context, R.string.msg_need_location, Toast.LENGTH_LONG).show();
            Log.w(TAG, "Location setting retrieval is not supported by API level "
                    + android.os.Build.VERSION.SDK_INT);
        }
        if (Build.VERSION.SDK_INT >= 23
                && ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            displayPermissionMissingNotification();
            return START_STICKY;
        }
        Location location = null;
        //We are trying for all enabled providers
        List<String> providers = locationManager.getProviders(true);
        for (String p : providers) {
            location = locationManager.getLastKnownLocation(p);
            if (location != null) {
                break;
            } else if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "No last known location found for provider " + p);
            }
        }
        if (location != null) {
            broadcastLocation(location);
        } else {
            Log.w(TAG, "No last known location found");
        }
        return START_STICKY;
    }

    private void broadcastLocation(Location location) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(Constants.LOCATION_UPDATE_BROADCAST_ACTION);
        broadcastIntent.putExtra(Constants.Location.LOCATION, location);
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onCreate() {
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Creating service");
        }
        context = getApplicationContext();
        if (locationManager == null) {
            locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);
        }
        setLocation();
    }

    @Override
    public void onDestroy() {
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
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Service destroyed.");
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
                    if (Constants.DEBUG_MODE_ENABLED) {
                        Log.d(TAG, "Requesting locations from provider: " + provider);
                    }
                    locationManager.requestLocationUpdates(provider, MIN_TIME_BW_UPDATES,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                    isUpdateRequested = true;
                } else {
                    Log.w(TAG, "No suitable location providers found");
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "No network or GPS Switched off.", e);
            }
        } else {
            Log.e(TAG, "Location manager is not available.");
            stopSelf();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Location changed> lat:" + location.getLatitude() + " lon:" + location.getLongitude() + " provider:" + location.getProvider());
            }
            broadcastLocation(location);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Status changed to: " + status + " Provider: " + provider);
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Provider enabled: " + provider);
        }
        setLocation();
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Provider disabled: " + provider);
        }
        if (this.provider != null && this.provider.equals(provider)) {
            setLocation();
        }
    }

    private void displayPermissionMissingNotification() {
        CommonUtils.displayNotification(context,
                R.drawable.notification,
                context.getResources().getString(R.string.title_need_permissions),
                context.getResources().getString(R.string.msg_need_permissions),
                AgentReceptionActivity.class,
                Constants.PERMISSION_MISSING,
                Constants.PERMISSION_MISSING_NOTIFICATION_ID);
    }
}
