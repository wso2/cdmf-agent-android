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

package org.wso2.iot.agent.services;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.beans.Device;
import org.wso2.iot.agent.utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class NetworkInfoService extends Service {

    private static final String TAG = NetworkInfoService.class.getSimpleName();

    private static int cellSignalStrength = 99; // Invalid signal strength is represented with 99.
    private static ObjectMapper mapper;
    private static NetworkInfo info;
    private static List<ScanResult> wifiScanResults;
    private static NetworkInfoService thisInstance;
    private WifiManager wifiManager;
    private WifiReceiver receiverWifi;
    private TelephonyManager telephonyManager;

    private static final int DEFAULT_AGE = 0;
    private static final String MAC_ADDRESS = "macAddress";
    private static final String SIGNAL_STRENGTH = "signalStrength";
    private static final String AGE = "age";
    private static final String CHANNEL = "channel";
    private static final String SNR = "signalToNoiseRatio";
    private final IBinder mBinder = new NetworkInfoService.LocalBinder();

    private PhoneStateListener deviceNetworkStatusListener = new PhoneStateListener() {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            super.onSignalStrengthsChanged(signalStrength);
            if (signalStrength.isGsm()) {
                if (signalStrength.getGsmSignalStrength() != 99) {
                    // this is the equation used to convert a valid gsm signal to dbm.
                    setCellSignalStrength(signalStrength.getGsmSignalStrength() * 2 - 113);
                } else {
                    setCellSignalStrength(signalStrength.getGsmSignalStrength());
                }
            } else {
                setCellSignalStrength(signalStrength.getCdmaDbm());
            }
        }
    };

    class WifiReceiver extends BroadcastReceiver {
        // This method call when number of wifi connections changed
        public void onReceive(Context c, Intent intent) {
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Wifi scan result found");
            }
            wifiScanResults = wifiManager.getScanResults();
        }
    }

    class LocalBinder extends Binder {
        NetworkInfoService getService() {
            return NetworkInfoService.this;
        }
    }

    public NetworkInfoService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        thisInstance = this;
        if (Constants.DEBUG_MODE_ENABLED){
            Log.d(TAG, "Creating service");
        }
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        receiverWifi = new WifiReceiver();
        mapper = new ObjectMapper();
        // Register broadcast receiver
        // Broadcast receiver will automatically call when number of wifi connections changed
        registerReceiver(receiverWifi, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        // start scanning wifi
        startWifiScan();
        info = getNetworkInfo();
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        class LooperThread extends Thread {
            public void run() {
                if (Looper.myLooper() == null) {
                    Looper.prepare();
                }
                telephonyManager.listen(deviceNetworkStatusListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
                Looper.loop();
            }
        }
        new LooperThread().run();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        telephonyManager.listen(deviceNetworkStatusListener, PhoneStateListener.LISTEN_NONE);
        unregisterReceiver(receiverWifi);
        thisInstance = null;
        if (Constants.DEBUG_MODE_ENABLED){
            Log.d(TAG, "Service destroyed.");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Network data such as  connection type and signal details can be fetched with this method.
     *
     * @return String representing network details.
     * @throws AndroidAgentException
     */
    public static String getNetworkStatus() throws AndroidAgentException {
        if (thisInstance == null) {
            Log.e(TAG, "Service not instantiated");
            throw new AndroidAgentException(TAG + " is not started.");
        }
        info = thisInstance.getNetworkInfo();
        String payload = null;
        if(info != null) {
            List<Device.Property> properties = new ArrayList<>();
            Device.Property property = new Device.Property();
            property.setName(Constants.Device.CONNECTION_TYPE);
            property.setValue(info.getTypeName());
            properties.add(property);

            if ((info.isConnected())) {
                if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
                    property = new Device.Property();
                    property.setName(Constants.Device.MOBILE_CONNECTION_TYPE);
                    property.setValue(info.getSubtypeName());
                    properties.add(property);
                }
                if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                    property = new Device.Property();
                    property.setName(Constants.Device.WIFI_SSID);
                    // NetworkInfo API of Android seem to add extra "" to SSID, therefore escaping it.
                    property.setValue(String.valueOf(thisInstance.getWifiSSID()).replaceAll("\"", ""));
                    properties.add(property);

                    property = new Device.Property();
                    property.setName(Constants.Device.WIFI_SIGNAL_STRENGTH);
                    property.setValue(String.valueOf(thisInstance.getWifiSignalStrength()));
                    properties.add(property);
                }
            }

            property = new Device.Property();
            property.setName(Constants.Device.MOBILE_SIGNAL_STRENGTH);
            property.setValue(String.valueOf(getCellSignalStrength()));
            properties.add(property);

            try {
                payload = mapper.writeValueAsString(properties);
            } catch (JsonProcessingException e) {
                String errorMsg = "Error occurred while parsing " +
                        "network property property object to json.";
                Log.e(TAG, errorMsg, e);
                throw new AndroidAgentException(errorMsg, e);
            }
        }
        return payload;
    }

    public static int getCellSignalStrength() {
        return cellSignalStrength;
    }

    public static void setCellSignalStrength(int cellSignalStrength) {
        NetworkInfoService.cellSignalStrength = cellSignalStrength;
    }

    public static String getWifiScanResult() throws AndroidAgentException {
        if (wifiScanResults != null) {
            try {
                JSONArray scanResults = new JSONArray();
                JSONObject scanResult;
                for (ScanResult result : wifiScanResults) {
                    scanResult = new JSONObject();
                    scanResult.put(MAC_ADDRESS, result.BSSID);
                    scanResult.put(SIGNAL_STRENGTH, result.level);
                    scanResult.put(AGE, DEFAULT_AGE);
                    scanResult.put(CHANNEL, result.frequency);
                    scanResult.put(SNR, result.level); // temporarily added
                    scanResults.put(scanResult);
                }
                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, "Wifi scan result: " + scanResults.toString());
                }
                // scanning for next round
                thisInstance.startWifiScan();
                return scanResults.toString();
            } catch (JSONException e) {
                String msg = "Error occurred while retrieving wifi scan results";
                Log.e(TAG, msg, e);
                throw new AndroidAgentException(msg);
            }
        }
        return null;
    }

    private int getWifiSignalStrength() {
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            return wifiInfo.getRssi();
        }
        return -1;
    }

    private String getWifiSSID() {
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            return wifiInfo.getSSID();
        }
        return null;
    }

    private void startWifiScan() {
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Wifi scanning started...");
        }
        wifiManager.startScan();
    }

    private NetworkInfo getNetworkInfo() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return connectivityManager.getActiveNetworkInfo();
    }
}
