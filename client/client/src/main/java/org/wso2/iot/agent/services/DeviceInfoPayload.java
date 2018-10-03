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

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.Base64;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.api.DeviceInfo;
import org.wso2.iot.agent.api.DeviceState;
import org.wso2.iot.agent.api.RuntimeInfo;
import org.wso2.iot.agent.beans.Device;
import org.wso2.iot.agent.beans.Power;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        info.setOwnership(Constants.OWNERSHIP_BYOD.equals(type) ? Device.EnrolmentInfo.OwnerShip.BYOD
                : Device.EnrolmentInfo.OwnerShip.COPE);
        device.setEnrolmentInfo(info);
        getInfo();
    }

    /**
     * This method builds the payload including device current state.
     *
     * @throws AndroidAgentException on error
     */
    public void build() throws AndroidAgentException {
        device = new Device();
        getInfo();
    }

    /**
     * Fetch all device runtime information.
     *
     * @throws AndroidAgentException on error
     */
    @SuppressLint({"HardwareIds"})
    private void getInfo() throws AndroidAgentException {

        HashMap<String, String> keyValPair = null;
        if (Preference.getString(context, "lastDeviceObject") != null) {
            String lastUpdatedInfoString = Preference.getString(context, "lastDeviceObject");
            byte[] lastUpdatedInfoByteArray = Base64.decode(lastUpdatedInfoString, Base64.DEFAULT);
            ByteArrayInputStream bais = new ByteArrayInputStream(lastUpdatedInfoByteArray);
            ObjectInputStream ois = null;
            try {
                ois = new ObjectInputStream(bais);
                keyValPair = (HashMap<String, String>) ois.readObject();
            } catch (IOException e) {
                throw new AndroidAgentException("Error occurred while deserialize Device object", e);
            } catch (ClassNotFoundException e) {
                throw new AndroidAgentException("Error occurred while casting to Device object", e);
            }
        } else {
            keyValPair = new HashMap<String, String>();
        }

        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        if (!CommonUtils.isServiceRunning(context, NetworkInfoService.class)) {
            Intent serviceIntent = new Intent(context, NetworkInfoService.class);
            context.startService(serviceIntent);
        }

        Location deviceLocation = CommonUtils.getLocation(context);
        if (device == null) {
            device = new Device();
        }

        deviceInfo = new DeviceInfo(context);
        Power power = phoneState.getBatteryDetails();
        if ((keyValPair.get(Constants.Device.DEVICE_IDENTIFIER) == null) ||
                !keyValPair.get(Constants.Device.DEVICE_IDENTIFIER).toString().equals(deviceInfo.getDeviceId())) {
            device.setDeviceIdentifier(deviceInfo.getDeviceId());
            keyValPair.put(Constants.Device.DEVICE_IDENTIFIER, deviceInfo.getDeviceId());
        }
        if ((keyValPair.get(Constants.Device.DEVICE_NAME) == null) ||
                !keyValPair.get(Constants.Device.DEVICE_NAME).toString().equals(deviceInfo.getDeviceName())) {
            device.setName(deviceInfo.getDeviceName());
            device.setDescription(deviceInfo.getDeviceName());
            keyValPair.put(Constants.Device.DEVICE_NAME, deviceInfo.getDeviceName());
        }

        List<Device.Property> properties = new ArrayList<>();

        Device.Property property = null;
        if ((keyValPair.get(Constants.Device.SERIAL) == null) ||
                !keyValPair.get(Constants.Device.SERIAL).toString().equals(deviceInfo.getDeviceSerialNumber())) {
            property = new Device.Property();
            property.setName(Constants.Device.SERIAL);
            property.setValue(deviceInfo.getDeviceSerialNumber());
            properties.add(property);
            keyValPair.put(Constants.Device.SERIAL, deviceInfo.getDeviceSerialNumber().toString());
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            if ((keyValPair.get(Constants.Device.IMEI) == null) || !keyValPair.get(Constants.Device.IMEI).toString()
                    .equals(telephonyManager.getDeviceId().toString())) {
                property = new Device.Property();
                property.setName(Constants.Device.IMEI);
                property.setValue(telephonyManager.getDeviceId());
                properties.add(property);
                keyValPair.put(Constants.Device.IMEI, telephonyManager.getDeviceId().toString());
            }
        }

        if ((keyValPair.get(Constants.Device.IMSI) == null) ||
                !keyValPair.get(Constants.Device.IMSI).toString().equals(deviceInfo.getIMSINumber().toString())) {
            property = new Device.Property();
            property.setName(Constants.Device.IMSI);
            property.setValue(deviceInfo.getIMSINumber());
            properties.add(property);
            keyValPair.put(Constants.Device.IMSI, deviceInfo.getIMSINumber().toString());
        }

        if ((keyValPair.get(Constants.Device.MAC) == null) ||
                !keyValPair.get(Constants.Device.MAC).toString().equals(deviceInfo.getMACAddress())) {
            property = new Device.Property();
            property.setName(Constants.Device.MAC);
            property.setValue(deviceInfo.getMACAddress());
            properties.add(property);
            keyValPair.put(Constants.Device.MAC, deviceInfo.getMACAddress().toString());
        }

        if ((keyValPair.get(Constants.Device.MODEL) == null) ||
                !keyValPair.get(Constants.Device.MODEL).toString()
                        .equals(deviceInfo.getDeviceModel().toString())) {
            property = new Device.Property();
            property.setName(Constants.Device.MODEL);
            property.setValue(deviceInfo.getDeviceModel());
            properties.add(property);
            keyValPair.put(Constants.Device.MODEL, deviceInfo.getDeviceModel().toString());
        }

        if ((keyValPair.get(Constants.Device.VENDOR) == null) ||
                !keyValPair.get(Constants.Device.VENDOR).toString()
                        .equals(deviceInfo.getDeviceManufacturer().toString())) {
            property = new Device.Property();
            property.setName(Constants.Device.VENDOR);
            property.setValue(deviceInfo.getDeviceManufacturer());
            properties.add(property);
            keyValPair.put(Constants.Device.VENDOR, deviceInfo.getDeviceManufacturer().toString());
        }

        if ((keyValPair.get(Constants.Device.OS) == null)
                || !keyValPair.get(Constants.Device.OS).toString().equals(deviceInfo.getOsVersion().toString())) {
            property = new Device.Property();
            property.setName(Constants.Device.OS);
            property.setValue(deviceInfo.getOsVersion());
            properties.add(property);
            keyValPair.put(Constants.Device.OS, deviceInfo.getOsVersion().toString());
        }

        if ((keyValPair.get(Constants.Device.OS_BUILD_DATE) == null)
                || !keyValPair.get(Constants.Device.OS_BUILD_DATE).toString()
                .equals(deviceInfo.getOSBuildDate().toString())) {
            property = new Device.Property();
            property.setName(Constants.Device.OS_BUILD_DATE);
            property.setValue(deviceInfo.getOSBuildDate());
            properties.add(property);
            keyValPair.put(Constants.Device.OS_BUILD_DATE, deviceInfo.getOSBuildDate().toString());
        }

        if (deviceLocation != null) {
            double latitude = deviceLocation.getLatitude();
            double longitude = deviceLocation.getLongitude();

            if (latitude != 0 && longitude != 0) {
                if ((keyValPair.get(Constants.Device.MOBILE_DEVICE_LATITUDE) == null)
                        || !keyValPair.get(Constants.Device.MOBILE_DEVICE_LATITUDE).toString()
                        .equals(String.valueOf(latitude))) {
                    property = new Device.Property();
                    property.setName(Constants.Device.MOBILE_DEVICE_LATITUDE);
                    property.setValue(String.valueOf(latitude));
                    properties.add(property);
                    keyValPair.put(Constants.Device.MOBILE_DEVICE_LATITUDE, String.valueOf(latitude));
                }

                if ((keyValPair.get(Constants.Device.MOBILE_DEVICE_LONGITUDE) == null)
                        || !keyValPair.get(Constants.Device.MOBILE_DEVICE_LONGITUDE).toString()
                        .equals(String.valueOf(longitude))) {
                    property = new Device.Property();
                    property.setName(Constants.Device.MOBILE_DEVICE_LONGITUDE);
                    property.setValue(String.valueOf(longitude));
                    properties.add(property);
                    keyValPair.put(Constants.Device.MOBILE_DEVICE_LONGITUDE, String.valueOf(longitude));
                }
            }
        }

        if (registrationId != null) {
            if ((keyValPair.get(Constants.Device.FCM_TOKEN) == null)
                    || !keyValPair.get(Constants.Device.FCM_TOKEN).toString()
                    .equals(registrationId)) {
                property = new Device.Property();
                property.setName(Constants.Device.FCM_TOKEN);
                property.setValue(registrationId);
                properties.add(property);
                keyValPair.put(Constants.Device.FCM_TOKEN, registrationId);
            }
        }

        List<Device.Property> deviceInfoProperties = new ArrayList<>();

        if ((keyValPair.get(Constants.Device.ENCRYPTION_STATUS) == null) || !keyValPair
                .get(Constants.Device.ENCRYPTION_STATUS).toString()
                .equals(String.valueOf(deviceInfo.isEncryptionEnabled()))) {
            property = new Device.Property();
            property.setName(Constants.Device.ENCRYPTION_STATUS);
            property.setValue(String.valueOf(deviceInfo.isEncryptionEnabled()));
            deviceInfoProperties.add(property);
            keyValPair.put(Constants.Device.ENCRYPTION_STATUS, String.valueOf(deviceInfo.isEncryptionEnabled()));
        }

        if ((deviceInfo.getSdkVersion() >= Build.VERSION_CODES.LOLLIPOP)) {
            if ((keyValPair.get(Constants.Device.PASSCODE_STATUS) == null) || !keyValPair
                    .get(Constants.Device.PASSCODE_STATUS).toString()
                    .equals(String.valueOf(deviceInfo.isPasscodeEnabled()))) {
                property = new Device.Property();
                property.setName(Constants.Device.PASSCODE_STATUS);
                property.setValue(String.valueOf(deviceInfo.isPasscodeEnabled()));
                deviceInfoProperties.add(property);
                keyValPair.put(Constants.Device.PASSCODE_STATUS, String.valueOf(deviceInfo.isPasscodeEnabled()));
            }
        }

        if ((keyValPair.get(Constants.Device.BATTERY_LEVEL) == null) ||
                !keyValPair.get(Constants.Device.BATTERY_LEVEL).toString()
                        .equals(String.valueOf(Math.round(power.getLevel())))) {
            property = new Device.Property();
            property.setName(Constants.Device.BATTERY_LEVEL);
            int batteryLevel = Math.round(power.getLevel());
            property.setValue(String.valueOf(batteryLevel));
            deviceInfoProperties.add(property);
            keyValPair.put(Constants.Device.BATTERY_LEVEL, String.valueOf(batteryLevel));
        }

        if ((keyValPair.get(Constants.Device.MEMORY_INFO_INTERNAL_TOTAL) == null) || !keyValPair
                .get(Constants.Device.MEMORY_INFO_INTERNAL_TOTAL).toString()
                .equals(String.valueOf(phoneState.getTotalInternalMemorySize()))) {
            property = new Device.Property();
            property.setName(Constants.Device.MEMORY_INFO_INTERNAL_TOTAL);
            property.setValue(String.valueOf(phoneState.getTotalInternalMemorySize()));
            deviceInfoProperties.add(property);
            keyValPair.put(Constants.Device.MEMORY_INFO_INTERNAL_TOTAL,
                    String.valueOf(phoneState.getTotalInternalMemorySize()));
        }

        if ((keyValPair.get(Constants.Device.MEMORY_INFO_INTERNAL_AVAILABLE) == null) || !keyValPair
                .get(Constants.Device.MEMORY_INFO_INTERNAL_AVAILABLE).toString()
                .equals(String.valueOf(phoneState.getAvailableInternalMemorySize()))) {
            property = new Device.Property();
            property.setName(Constants.Device.MEMORY_INFO_INTERNAL_AVAILABLE);
            property.setValue(String.valueOf(phoneState.getAvailableInternalMemorySize()));
            deviceInfoProperties.add(property);
            keyValPair.put(Constants.Device.MEMORY_INFO_INTERNAL_AVAILABLE,
                    String.valueOf(phoneState.getAvailableInternalMemorySize()));
        }

        if ((keyValPair.get(Constants.Device.MEMORY_INFO_EXTERNAL_TOTAL) == null) || !keyValPair
                .get(Constants.Device.MEMORY_INFO_EXTERNAL_TOTAL).toString()
                .equals(String.valueOf(phoneState.getTotalExternalMemorySize()))) {
            property = new Device.Property();
            property.setName(Constants.Device.MEMORY_INFO_EXTERNAL_TOTAL);
            property.setValue(String.valueOf(phoneState.getTotalExternalMemorySize()));
            deviceInfoProperties.add(property);
            keyValPair.put(Constants.Device.MEMORY_INFO_EXTERNAL_TOTAL,
                    String.valueOf(phoneState.getTotalExternalMemorySize()));
        }

        if ((keyValPair.get(Constants.Device.MEMORY_INFO_EXTERNAL_AVAILABLE) == null) || !keyValPair
                .get(Constants.Device.MEMORY_INFO_EXTERNAL_AVAILABLE).toString()
                .equals(String.valueOf(phoneState.getAvailableExternalMemorySize()))) {
            property = new Device.Property();
            property.setName(Constants.Device.MEMORY_INFO_EXTERNAL_AVAILABLE);
            property.setValue(String.valueOf(phoneState.getAvailableExternalMemorySize()));
            deviceInfoProperties.add(property);
            keyValPair.put(Constants.Device.MEMORY_INFO_EXTERNAL_AVAILABLE,
                    String.valueOf(phoneState.getAvailableExternalMemorySize()));
        }

        if ((keyValPair.get(Constants.Device.NETWORK_OPERATOR) == null) || !keyValPair
                .get(Constants.Device.NETWORK_OPERATOR).toString()
                .equals(String.valueOf(deviceInfo.getNetworkOperatorName()))) {
            property = new Device.Property();
            property.setName(Constants.Device.NETWORK_OPERATOR);
            property.setValue(String.valueOf(deviceInfo.getNetworkOperatorName()));
            deviceInfoProperties.add(property);
            keyValPair.put(Constants.Device.NETWORK_OPERATOR,
                    String.valueOf(deviceInfo.getNetworkOperatorName()));
        }
        if (telephonyManager != null && ActivityCompat
                .checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            String mPhoneNumber = telephonyManager.getLine1Number();

            if ((mPhoneNumber != null) && (keyValPair.get(Constants.Device.PHONE_NUMBER) == null) || !keyValPair
                    .get(Constants.Device.PHONE_NUMBER).toString().equals(mPhoneNumber)) {
                property = new Device.Property();
                property.setName(Constants.Device.PHONE_NUMBER);
                property.setValue(mPhoneNumber);
                deviceInfoProperties.add(property);
                keyValPair.put(Constants.Device.PHONE_NUMBER, mPhoneNumber);
            }
        }

        try {
            String network = NetworkInfoService.getNetworkStatus();
            if ((network != null) && (keyValPair.get(Constants.Device.NETWORK_INFO) == null) || !keyValPair
                    .get(Constants.Device.NETWORK_INFO).toString().equals(network)) {
                property = new Device.Property();
                property.setName(Constants.Device.NETWORK_INFO);
                property.setValue(network);
                properties.add(property);
                keyValPair.put(Constants.Device.NETWORK_INFO, network);
            }
            if (Constants.WIFI_SCANNING_ENABLED) {
                String wifiScanResult = NetworkInfoService.getWifiScanResult(context);
                if ((network != null) && (keyValPair.get(Constants.Device.WIFI_SCAN_RESULT) == null) || !keyValPair
                        .get(Constants.Device.WIFI_SCAN_RESULT).toString().equals(wifiScanResult)) {
                    property = new Device.Property();
                    property.setName(Constants.Device.WIFI_SCAN_RESULT);
                    property.setValue(wifiScanResult);
                    properties.add(property);
                    keyValPair.put(Constants.Device.WIFI_SCAN_RESULT, wifiScanResult);
                }
            }
        } catch (AndroidAgentException e) {
            Log.e(TAG, "Error retrieving network status. " + e.getMessage());
        }

        RuntimeInfo runtimeInfo = new RuntimeInfo(context);
        String cpuInfoPayload;
        try {
            cpuInfoPayload = mapper.writeValueAsString(runtimeInfo.getCPUInfo());
        } catch (JsonProcessingException e) {
            String errorMsg = "Error occurred while parsing property CPU info object to json.";
            Log.e(TAG, errorMsg, e);
            throw new AndroidAgentException(errorMsg, e);
        }

        if ((keyValPair.get(Constants.Device.CPU_INFO) == null) ||
                !keyValPair.get(Constants.Device.CPU_INFO).toString().equals(cpuInfoPayload)) {
            property = new Device.Property();
            property.setName(Constants.Device.CPU_INFO);
            property.setValue(cpuInfoPayload);
            properties.add(property);
            keyValPair.put(Constants.Device.CPU_INFO, cpuInfoPayload);
        }

        String ramInfoPayload;
        try {
            ramInfoPayload = mapper.writeValueAsString(runtimeInfo.getRAMInfo());
        } catch (JsonProcessingException e) {
            String errorMsg = "Error occurred while parsing property RAM info object to json.";
            Log.e(TAG, errorMsg, e);
            throw new AndroidAgentException(errorMsg, e);
        }

        if ((keyValPair.get(Constants.Device.RAM_INFO) == null) || (keyValPair.get(Constants.Device.RAM_INFO) != null
                && !keyValPair.get(Constants.Device.RAM_INFO).toString().equals(ramInfoPayload))) {
            property = new Device.Property();
            property.setName(Constants.Device.RAM_INFO);
            property.setValue(ramInfoPayload);
            properties.add(property);
            keyValPair.put(Constants.Device.RAM_INFO, ramInfoPayload);
        }

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

        if ((keyValPair.get(Constants.Device.BATTERY_INFO) == null) ||
                !keyValPair.get(Constants.Device.BATTERY_INFO).toString().equals(batteryInfoPayload)) {
            property = new Device.Property();
            property.setName(Constants.Device.BATTERY_INFO);
            property.setValue(batteryInfoPayload);
            properties.add(property);
            keyValPair.put(Constants.Device.BATTERY_INFO, batteryInfoPayload);
        }

        // building device info json payload
        String deviceInfoPayload;
        try {
            deviceInfoPayload = mapper.writeValueAsString(deviceInfoProperties);
        } catch (JsonProcessingException e) {
            String errorMsg = "Error occurred while parsing property object to json.";
            Log.e(TAG, errorMsg, e);
            throw new AndroidAgentException(errorMsg, e);
        }

        if ((keyValPair.get(Constants.Device.INFO) == null) || !keyValPair
                .get(Constants.Device.INFO).toString().equals(deviceInfoPayload)) {
            property = new Device.Property();
            property.setName(Constants.Device.INFO);
            property.setValue(deviceInfoPayload);
            properties.add(property);
            keyValPair.put(Constants.Device.INFO, deviceInfoPayload);
        }

        device.setProperties(properties);
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        try {
            ObjectOutputStream oos = new ObjectOutputStream(bao);
            oos.writeObject(keyValPair);
            String stringObject = null;
            stringObject = Base64.encodeToString(bao.toByteArray(), Base64.DEFAULT);
            Preference.putString(context, "lastDeviceObject", stringObject);
        } catch (IOException e) {
            throw new AndroidAgentException("Error occurred while serializing policy operation object", e);
        }
    }

    /**
     * Returns the final payload.
     *
     * @return - Device info payload as a string.
     */
    public String getDeviceInfoPayload() {
        try {
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "device info " + device.toJSON());
            }
            return device.toJSON();
        } catch (AndroidAgentException e) {
            Log.e(TAG, "Error occurred while building device info payload", e);
        }
        return null;
    }

}
