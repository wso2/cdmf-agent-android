package org.wso2.iot.agent.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import org.wso2.iot.agent.services.location.impl.OpenStreetMapService;
import org.wso2.iot.agent.utils.Constants;

public class LocationUpdateReceiver extends BroadcastReceiver {

    private static final String TAG = LocationUpdateReceiver.class.getSimpleName();
    private static Location location;

    public LocationUpdateReceiver() {
    }

    public static Location getLocation() {
        if (location == null) {
            Log.w(TAG, "Location not found");
        }
        return location;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.hasExtra(Constants.Location.LOCATION)) {
            location = (Location) intent.getExtra(Constants.Location.LOCATION);
        }
        if (location == null) {
            Log.w(TAG, "Location not received");
        } else {
            OpenStreetMapService.getInstance().fetchReverseGeoCodes(location);
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Location> Lat:" + location.getLatitude()
                        + " Lon:" + location.getLongitude()
                        + " Provider:" + location.getProvider());
            }
        }
    }
}
