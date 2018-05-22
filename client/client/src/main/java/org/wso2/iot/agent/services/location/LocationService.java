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
import android.content.Intent;
import android.content.pm.PackageManager;
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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.activities.AlreadyRegisteredActivity;
import org.wso2.iot.agent.events.beans.EventPayload;
import org.wso2.iot.agent.events.publisher.HttpDataPublisher;
import org.wso2.iot.agent.services.LocationUpdateReceiver;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * This class holds the function implementations of the location service.
 */
public class LocationService extends Service implements LocationListener {

    private static final String TAG = LocationService.class.getSimpleName();

    private FusedLocationProviderClient mFusedLocationClient = null;
    private LocationManager locationManager = null;
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 1; //If more than 1Km
    private static final long MIN_TIME_BW_UPDATES = 1000 * 60 * 5; //If more than 5 minutes
    private static final long FASTEST_INTERVAL = 1000 * 60; //Only receive at most once a minute
    private List<String> providers = new ArrayList<>();
    private boolean isUpdateRequested = false;
    private final IBinder mBinder = new LocalBinder();
    private long lastPublishedLocationTime;

    private LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            Location location = null;
            long locationTime = 0;
            for (Location l : locationResult.getLocations()) {
                if (l != null && l.getTime() > locationTime) {
                    locationTime = l.getTime();
                    location = l;
                }
            }
            onLocationChanged(location);
        }
    };

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
        if (Constants.ASK_TO_ENABLE_LOCATION) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    int locationSetting = Settings.Secure.getInt(this.getContentResolver(),
                            Settings.Secure.LOCATION_MODE);
                    if (locationSetting == 0) {
                        CommonUtils.displayNotification(this,
                                R.drawable.ic_warning_white_24dp,
                                this.getResources().getString(R.string.title_need_location),
                                this.getResources().getString(R.string.msg_need_location),
                                AlreadyRegisteredActivity.class,
                                Constants.LOCATION_DISABLED,
                                Constants.LOCATION_DISABLED_NOTIFICATION_ID);
                    }
                } catch (Settings.SettingNotFoundException e) {
                    Log.w(TAG, "Location setting is not available on this device");
                }
            } else {
                Log.w(TAG, "Location setting retrieval is not supported by API level "
                        + android.os.Build.VERSION.SDK_INT);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            displayPermissionMissingNotification();
            return START_NOT_STICKY;
        }
        if (locationManager == null && mFusedLocationClient != null) {
            mFusedLocationClient.getLastLocation()
                    .addOnSuccessListener(new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            // Got last known location. In some rare situations this can be null.
                            if (location != null) {
                                broadcastLocation(location);
                            } else {
                                Log.w(TAG, "No last known location found");
                            }
                        }
                    });
        } else if (locationManager != null && mFusedLocationClient == null) {
            Location location = null;
            long locationTime = 0;
            //We are trying for all enabled providers
            List<String> _providers = locationManager.getProviders(true);
            for (String p : _providers) {
                Location l = locationManager.getLastKnownLocation(p);
                if (l != null && l.getTime() > locationTime) {
                    locationTime = l.getTime();
                    location = l;
                } else if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, "Last known location from provider " + p
                            + " is not found or too old.");
                }
            }
            if (location != null) {
                broadcastLocation(location);
            } else {
                Log.w(TAG, "No last known location found");
            }
        }
        return START_STICKY;
    }

    private void broadcastLocation(Location location) {
        Intent broadcastIntent = new Intent(this, LocationUpdateReceiver.class);
        broadcastIntent.setAction(Constants.LOCATION_UPDATE_BROADCAST_ACTION);
        broadcastIntent.putExtra(Constants.Location.LOCATION, location);
        sendBroadcast(broadcastIntent);
        publishLocationInfo(location);
    }

    @Override
    public void onCreate() {
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Creating service");
        }
        if (isPlayServicesAvailable()) {
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        } else {
            Log.i(TAG, "Play services not available on this device. Using location manager");
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        }
        requestLocationUpdates();
    }

    @Override
    public void onDestroy() {
        if (isUpdateRequested) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                displayPermissionMissingNotification();
                return;
            }
            if (locationManager != null) {
                try {
                    locationManager.removeUpdates(this);
                    isUpdateRequested = false;
                } catch (Exception ex) {
                    Log.e(TAG, "fail to remove location listeners", ex);
                }
            }
            if (mFusedLocationClient != null) {
                mFusedLocationClient.removeLocationUpdates(mLocationCallback);
                isUpdateRequested = false;
            }
        }
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Service destroyed.");
        }
    }

    /**
     * In this method, it gets the latest location updates from gps/ network.
     */
    private void requestLocationUpdates() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            displayPermissionMissingNotification();
            return;
        }
        if (locationManager != null) {
            try {
                providers = locationManager.getProviders(true);
                if (providers != null && !providers.isEmpty()) {
                    if (isUpdateRequested) {
                        locationManager.removeUpdates(this);
                    }
                    for (String provider : providers) {
                        if (Constants.DEBUG_MODE_ENABLED) {
                            Log.d(TAG, "Requesting locations from provider: " + provider);
                        }
                        locationManager.requestLocationUpdates(provider, MIN_TIME_BW_UPDATES,
                                MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                    }
                    isUpdateRequested = true;
                } else {
                    Log.w(TAG, "No suitable location providers found");
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "No network or GPS Switched off.", e);
            }
        } else if (mFusedLocationClient != null) {
            LocationRequest mLocationRequest = new LocationRequest();
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            mLocationRequest.setInterval(MIN_TIME_BW_UPDATES);
            mLocationRequest.setFastestInterval(FASTEST_INTERVAL);

            // Create LocationSettingsRequest object using location request
            LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
            builder.addLocationRequest(mLocationRequest);
            LocationSettingsRequest locationSettingsRequest = builder.build();

            // Check whether location settings are satisfied
            // https://developers.google.com/android/reference/com/google/android/gms/location/SettingsClient
            SettingsClient settingsClient = LocationServices.getSettingsClient(this);
            settingsClient.checkLocationSettings(locationSettingsRequest);

            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback,
                    null /* Looper */);
            isUpdateRequested = true;
        } else {
            Log.e(TAG, "Location manager or Fused Location Client is not available.");
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
                Log.d(TAG, "Location changed> lat:" + location.getLatitude()
                        + " lon:" + location.getLongitude() + " provider:" + location.getProvider());
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
        requestLocationUpdates();
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Provider disabled: " + provider);
        }
        if (providers.contains(provider)) {
            requestLocationUpdates();
        }
    }

    /**
     * To publish the location event to the server
     *
     * @param location location details
     */
    private void publishLocationInfo(Location location) {
        if (!Constants.LOCATION_PUBLISHING_ENABLED) {
            return;
        }
        String locationPayload = getLocationPayload(location);
        if (lastPublishedLocationTime < location.getTime()) {
            EventPayload eventPayload = new EventPayload();
            eventPayload.setPayload(locationPayload);
            eventPayload.setType(Constants.EventListeners.LOCATION_EVENT_TYPE);
            HttpDataPublisher httpDataPublisher = new HttpDataPublisher(this);
            httpDataPublisher.publish(eventPayload);
            lastPublishedLocationTime = location.getTime();
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Location Event is published.");
            }
        } else {
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Ignore publishing. Duplicate location timestamp.");
            }
        }
    }

    /**
     * Returns the location payload.
     *
     * @return - Location info payload as a string
     */
    private String getLocationPayload(Location deviceLocation) {
        String locationString = null;
        double latitude = deviceLocation.getLatitude();
        double longitude = deviceLocation.getLongitude();
        if (latitude != 0 && longitude != 0) {
            JSONObject locationObject = new JSONObject();
            try {
                locationObject.put(Constants.LocationInfo.LATITUDE, latitude);
                locationObject.put(Constants.LocationInfo.LONGITUDE, longitude);
                locationObject.put(Constants.LocationInfo.TIME_STAMP, new Date().getTime());
                locationString = locationObject.toString();
            } catch (JSONException e) {
                Log.e(TAG, "Error occurred while creating a location payload for location " +
                        "event publishing", e);
            }
        }
        return locationString;
    }

    private void displayPermissionMissingNotification() {
        CommonUtils.displayNotification(this,
                R.drawable.ic_warning_white_24dp,
                this.getResources().getString(R.string.title_need_permissions),
                this.getResources().getString(R.string.msg_need_permissions),
                AlreadyRegisteredActivity.class,
                Constants.PERMISSION_MISSING,
                Constants.PERMISSION_MISSING_NOTIFICATION_ID);
    }

    /**
     * Check the device to make sure it has the Google Play Services.
     */
    private boolean isPlayServicesAvailable() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        return (resultCode == ConnectionResult.SUCCESS);
    }
}
