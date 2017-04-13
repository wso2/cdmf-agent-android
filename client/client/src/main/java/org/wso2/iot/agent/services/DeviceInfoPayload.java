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

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.api.DeviceInfo;
import org.wso2.iot.agent.api.DeviceState;
import org.wso2.iot.agent.api.RuntimeInfo;
import org.wso2.iot.agent.beans.Device;
import org.wso2.iot.agent.beans.Power;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * This class handles building of the device information payload to be sent to the server.
 */
public class DeviceInfoPayload {
    private DeviceInfo deviceInfo;
    private Device device;
    private Context context;
    private static final String TAG = DeviceInfoPayload.class.getName();
    private ObjectMapper mapper;
    private DeviceState phoneState;
    private String registrationId;

    public DeviceInfoPayload(Context context) {
        this.context = context.getApplicationContext();
        deviceInfo = new DeviceInfo(context);
        mapper = new ObjectMapper();
        registrationId = Preference.getString(context, Constants.FCM_REG_ID);
        phoneState = new DeviceState(context);
    }

    /**
     * Builds device information payload.
     *
     * @param type  - Device ownership type.
     * @param owner - Current user name.
     */
    public void build(String type, String owner) throws AndroidAgentException {
        device = new Device();
        Device.EnrolmentInfo info = new Device.EnrolmentInfo();
        //setting up basic details of the device
        info.setOwner(owner);
        info.setOwnership(type.equals(Constants.OWNERSHIP_BYOD) ? Device.EnrolmentInfo.OwnerShip.BYOD
                                                                : Device.EnrolmentInfo.OwnerShip.COPE);
        device.setEnrolmentInfo(info);
        getInfo();
    }

    /**
     * This method builds the payload including device current state.
     *
     * @throws AndroidAgentException
     */
    public void build() throws AndroidAgentException {
        device = new Device();
        getInfo();
    }

    /**
     * Fetch all device runtime information.
     * @throws AndroidAgentException
     */
    private void getInfo() throws AndroidAgentException {

        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        if (!CommonUtils.isServiceRunning(context, NetworkInfoService.class) ){
            Intent serviceIntent = new Intent(context, NetworkInfoService.class);
            context.startService(serviceIntent);
        }

        Location deviceLocation = CommonUtils.getLocation(context);
        if (device == null) {
            device = new Device();
        }
        deviceInfo = new DeviceInfo(context);
        Power power = phoneState.getBatteryDetails();
        device.setDeviceIdentifier(deviceInfo.getDeviceId());
        device.setDescription(deviceInfo.getDeviceName());
        device.setName(deviceInfo.getDeviceName());

        List<Device.Property> properties = new ArrayList<>();

        Device.Property property = new Device.Property();
        property.setName(Constants.Device.SERIAL);
        property.setValue(deviceInfo.getDeviceSerialNumber());
        properties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.IMEI);
        property.setValue(telephonyManager.getDeviceId());
        properties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.IMSI);
        property.setValue(deviceInfo.getIMSINumber());
        properties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.MAC);
        property.setValue(deviceInfo.getMACAddress());
        properties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.MODEL);
        property.setValue(deviceInfo.getDeviceModel());
        properties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.VENDOR);
        property.setValue(deviceInfo.getDeviceManufacturer());
        properties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.OS);
        property.setValue(deviceInfo.getOsVersion());
        properties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.OS_BUILD_DATE);
        property.setValue(deviceInfo.getOSBuildDate());
        properties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.NAME);
        property.setValue(deviceInfo.getDeviceName());
        properties.add(property);

        if (deviceLocation != null) {
            double latitude = deviceLocation.getLatitude();
            double longitude = deviceLocation.getLongitude();

            if (latitude != 0 && longitude != 0) {
                property = new Device.Property();
                property.setName(Constants.Device.MOBILE_DEVICE_LATITUDE);
                property.setValue(String.valueOf(latitude));
                properties.add(property);

                property = new Device.Property();
                property.setName(Constants.Device.MOBILE_DEVICE_LONGITUDE);
                property.setValue(String.valueOf(longitude));
                properties.add(property);
            }
        }

        if (registrationId != null) {
            property = new Device.Property();
            property.setName(Constants.Device.FCM_TOKEN);
            property.setValue(registrationId);
            properties.add(property);
        }

        List<Device.Property> deviceInfoProperties = new ArrayList<>();

        property = new Device.Property();
        property.setName(Constants.Device.ENCRYPTION_STATUS);
        property.setValue(String.valueOf(deviceInfo.isEncryptionEnabled()));
        deviceInfoProperties.add(property);

        if ((deviceInfo.getSdkVersion() >= Build.VERSION_CODES.LOLLIPOP)) {
            property = new Device.Property();
            property.setName(Constants.Device.PASSCODE_STATUS);
            property.setValue(String.valueOf(deviceInfo.isPasscodeEnabled()));
            deviceInfoProperties.add(property);
        }

        property = new Device.Property();
        property.setName(Constants.Device.BATTERY_LEVEL);
        int batteryLevel = Math.round(power.getLevel());
        property.setValue(String.valueOf(batteryLevel));
        deviceInfoProperties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.MEMORY_INFO_INTERNAL_TOTAL);
        property.setValue(String.valueOf(phoneState.getTotalInternalMemorySize()));
        deviceInfoProperties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.MEMORY_INFO_INTERNAL_AVAILABLE);
        property.setValue(String.valueOf(phoneState.getAvailableInternalMemorySize()));
        deviceInfoProperties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.MEMORY_INFO_EXTERNAL_TOTAL);
        property.setValue(String.valueOf(phoneState.getTotalExternalMemorySize()));
        deviceInfoProperties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.MEMORY_INFO_EXTERNAL_AVAILABLE);
        property.setValue(String.valueOf(phoneState.getAvailableExternalMemorySize()));
        deviceInfoProperties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.NETWORK_OPERATOR);
        property.setValue(String.valueOf(deviceInfo.getNetworkOperatorName()));
        deviceInfoProperties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.PHONE_NUMBER);
        String mPhoneNumber = telephonyManager.getLine1Number();
        property.setValue(mPhoneNumber);
        deviceInfoProperties.add(property);

        String network = NetworkInfoService.getNetworkStatus();
        if(network != null) {
            property = new Device.Property();
            property.setName(Constants.Device.NETWORK_INFO);
            property.setValue(network);
            properties.add(property);
        }

        // adding wifi scan results..
        property = new Device.Property();
        property.setName(Constants.Device.WIFI_SCAN_RESULT);
        property.setValue(NetworkInfoService.getWifiScanResult());
        properties.add(property);

        RuntimeInfo runtimeInfo = new RuntimeInfo(context);
        String cpuInfoPayload;
        try {
            cpuInfoPayload = mapper.writeValueAsString(runtimeInfo.getCPUInfo());
        } catch (JsonProcessingException e) {
            String errorMsg = "Error occurred while parsing property CPU info object to json.";
            Log.e(TAG, errorMsg, e);
            throw new AndroidAgentException(errorMsg, e);
        }

        property = new Device.Property();
        property.setName(Constants.Device.CPU_INFO);
        property.setValue(cpuInfoPayload);
        properties.add(property);

        String ramInfoPayload;
        try {
            ramInfoPayload = mapper.writeValueAsString(runtimeInfo.getRAMInfo());
        } catch (JsonProcessingException e) {
            String errorMsg = "Error occurred while parsing property RAM info object to json.";
            Log.e(TAG, errorMsg, e);
            throw new AndroidAgentException(errorMsg, e);
        }

        property = new Device.Property();
        property.setName(Constants.Device.RAM_INFO);
        property.setValue(ramInfoPayload);
        properties.add(property);

        List<Device.Property> batteryProperties = new ArrayList<>();
        property = new Device.Property();
        property.setName(Constants.Device.BATTERY_LEVEL);
        property.setValue(String.valueOf(power.getLevel()));
        batteryProperties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.SCALE);
        property.setValue(String.valueOf(power.getScale()));
        batteryProperties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.BATTERY_VOLTAGE);
        property.setValue(String.valueOf(power.getVoltage()));
        batteryProperties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.HEALTH);
        property.setValue(String.valueOf(power.getHealth()));
        batteryProperties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.STATUS);
        property.setValue(String.valueOf(power.getStatus()));
        batteryProperties.add(property);

        property = new Device.Property();
        property.setName(Constants.Device.PLUGGED);
        property.setValue(String.valueOf(power.getPlugged()));
        batteryProperties.add(property);

        String batteryInfoPayload;
        try {
            batteryInfoPayload = mapper.writeValueAsString(batteryProperties);
        } catch (JsonProcessingException e) {
            String errorMsg = "Error occurred while parsing property battery info object to json.";
            Log.e(TAG, errorMsg, e);
            throw new AndroidAgentException(errorMsg, e);
        }

        property = new Device.Property();
        property.setName(Constants.Device.BATTERY_INFO);
        property.setValue(batteryInfoPayload);
        properties.add(property);

        // building device info json payload
        String deviceInfoPayload;
        try {
            deviceInfoPayload = mapper.writeValueAsString(deviceInfoProperties);
        } catch (JsonProcessingException e) {
            String errorMsg = "Error occurred while parsing property object to json.";
            Log.e(TAG, errorMsg, e);
            throw new AndroidAgentException(errorMsg, e);
        }
        property = new Device.Property();
        property.setName(Constants.Device.INFO);
        property.setValue(deviceInfoPayload);
        properties.add(property);

        device.setProperties(properties);
    }

    /**
     * Returns the final payload.
     *
     * @return - Device info payload as a string.
     */
    public String getDeviceInfoPayload() {
        try {
            if(Constants.DEBUG_MODE_ENABLED){
                Log.d(TAG, "device info " + device.toJSON());
            }
            return device.toJSON();
        } catch (AndroidAgentException e) {
            Log.e(TAG, "Error occurred while building device info payload", e);
        }
        return null;
    }

    /**
     * Returns the location payload.
     *
     * @return - Location info payload as a string
     */
    public String getLocationPayload() {
        Location deviceLocation = CommonUtils.getLocation(context);
        String locationString = null;
        if (deviceLocation != null) {
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
                    Log.e(TAG, "Error occured while creating a location payload for location event publishing", e);
                }
            }
        }
        return locationString;
    }
}