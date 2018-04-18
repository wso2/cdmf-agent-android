/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.iot.agent.services.operation;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.beans.AppRestriction;
import org.wso2.iot.agent.beans.ComplianceFeature;
import org.wso2.iot.agent.beans.Notification;
import org.wso2.iot.agent.beans.Operation;
import org.wso2.iot.agent.services.NotificationService;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class OperationManagerWorkProfile extends OperationManager {

    private static final String TAG = OperationManagerWorkProfile.class.getSimpleName();
    private NotificationService notificationService;
    private static final String STATUS = "status";

    public OperationManagerWorkProfile(Context context) {
        super(context);
        notificationService = super.getNotificationService();
    }

    @Override
    public void wipeDevice(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
        operation.setOperationResponse("Operation not supported.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Operation not supported.");
    }

    @Override
    public void displayNotification(Operation operation) throws AndroidAgentException {
        try {
            operation.setStatus(getContextResources().getString(R.string.operation_value_progress));
            operation.setOperationResponse(notificationService.buildResponse(Notification.Status.RECEIVED));
            getResultBuilder().build(operation);
            JSONObject inputData = new JSONObject(operation.getPayLoad().toString());
            String messageTitle = inputData.getString(getContextResources().getString(R.string.intent_extra_message_title));
            String messageText = inputData.getString(getContextResources().getString(R.string.intent_extra_message_text));

            if (messageTitle != null && !messageTitle.isEmpty() &&
                    messageText != null && !messageText.isEmpty()) {
                //adding notification to the db
                notificationService.addNotification(operation.getId(), messageTitle, messageText, Notification.Status.RECEIVED);
                notificationService.showNotification(operation.getId(), messageTitle, messageText);
            } else {
                operation.setStatus(getContextResources().getString(R.string.operation_value_error));
                String errorMessage = "Message title/text is empty. Please retry with valid inputs";
                JSONObject errorResult = new JSONObject();
                errorResult.put(STATUS, errorMessage);
                operation.setOperationResponse(errorMessage);
                getResultBuilder().build(operation);
                Log.e(TAG, errorMessage);
            }
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Notification received");
            }
        } catch (JSONException e) {
            operation.setStatus(getContextResources().getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing NOTIFICATION payload.");
            getResultBuilder().build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
    }

    @Override
    public void clearPassword(Operation operation) {
        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
        operation.setOperationResponse("Operation not supported.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Operation not supported.");
    }

    @Override
    public void installAppBundle(Operation operation) throws AndroidAgentException {
        try {
            if (operation.getCode().equals(Constants.Operation.INSTALL_APPLICATION) ||
                    operation.getCode().equals(Constants.Operation.UPDATE_APPLICATION)) {
                JSONObject appData = new JSONObject(operation.getPayLoad().toString());
                installApplication(appData, operation);
            } else if (operation.getCode().equals(Constants.Operation.INSTALL_APPLICATION_BUNDLE)) {
                JSONArray jArray;
                jArray = new JSONArray(operation.getPayLoad().toString());
                for (int i = 0; i < jArray.length(); i++) {
                    JSONObject appObj = jArray.getJSONObject(i);
                    installApplication(appObj, operation);
                }
            }
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Application bundle installation triggered.");
            }

        } catch (JSONException e) {
            operation.setStatus(getContextResources().getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing APPLICATION payload.");
            getResultBuilder().build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
    }

    private void installApplication(JSONObject data, org.wso2.iot.agent.beans.Operation operation)
            throws AndroidAgentException {
        String appUrl;
        String type;
        String name;
        String operationType;
        String schedule = null;

        try {
            if (!data.isNull(getContextResources().getString(R.string.app_type))) {
                type = data.getString(getContextResources().getString(R.string.app_type));

                if (type.equalsIgnoreCase(getContextResources().getString(R.string.intent_extra_enterprise))) {
                    appUrl = data.getString(getContextResources().getString(R.string.app_url));
                    if (data.has(getContextResources().getString(R.string.app_schedule))) {
                        schedule = data.getString(getContextResources().getString(R.string.app_schedule));
                        operation.setOperationResponse("Scheduling to execute at " + schedule);
                    }
                    operation.setStatus(getContextResources().getString(R.string.operation_value_progress));
                    getResultBuilder().build(operation);
                    try {
                        getAppList().installApp(appUrl, schedule, operation);
                    } catch (AndroidAgentException e) {
                        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
                        operation.setOperationResponse(e.getMessage());
                        getResultBuilder().build(operation);
                    }
                } else if (type.equalsIgnoreCase(getContextResources().getString(R.string.intent_extra_public))) {
                    appUrl = data.getString(getContextResources().getString(R.string.app_identifier));
                    operation.setStatus(getContextResources().getString(R.string.operation_value_progress));
                    getResultBuilder().build(operation);
                    triggerGooglePlayApp(appUrl);

                } else if (type.equalsIgnoreCase(getContextResources().getString(R.string.intent_extra_web))) {
                    name = data.getString(getContextResources().getString(R.string.intent_extra_name));
                    appUrl = data.getString(getContextResources().getString(R.string.app_url));
                    operationType = getContextResources().getString(R.string.operation_install);
                    JSONObject payload = new JSONObject();
                    payload.put(getContextResources().getString(R.string.intent_extra_identity), appUrl);
                    payload.put(getContextResources().getString(R.string.intent_extra_title), name);
                    payload.put(getContextResources().getString(R.string.operation_type), operationType);
                    operation.setPayLoad(payload.toString());
                    manageWebClip(operation);

                } else {
                    operation.setStatus(getContextResources().getString(R.string.operation_value_error));
                    operation.setOperationResponse("Invalid application details provided.");
                    getResultBuilder().build(operation);
                    throw new AndroidAgentException("Invalid application details");
                }

                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, "Application installation triggered.");
                }
            }
        } catch (JSONException e) {
            operation.setStatus(getContextResources().getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing APPLICATION payload.");
            getResultBuilder().build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
    }

    @Override
    public void setSystemUpdatePolicy(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
        operation.setOperationResponse("Operation not supported.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Operation not supported.");
    }

    @Override
    public void encryptStorage(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
        operation.setOperationResponse("Device already encrypted.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Already encrypted.");
    }

    @Override
    public void changeLockCode(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
        operation.setOperationResponse("Operation not supported.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Operation not supported.");
    }

    @Override
    public void enterpriseWipe(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
        getResultBuilder().build(operation);
        getDevicePolicyManager().wipeData(0);
    }

    @Override
    public void disenrollDevice(Operation operation) {
        operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
        getDevicePolicyManager().wipeData(0);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void hideApp(Operation operation) throws AndroidAgentException {
        String packageName = null;
        try {
            JSONObject hideAppData = new JSONObject(operation.getPayLoad().toString());
            if (!hideAppData.isNull(getContextResources().getString(R.string.intent_extra_package))) {
                packageName = (String) hideAppData.get(getContextResources().getString(R.string.intent_extra_package));
            }

            operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
            getResultBuilder().build(operation);

            if (packageName != null && !packageName.isEmpty()) {
                setApplicationHidden(packageName, true);
            }

            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "App-Hide successful.");
            }
        } catch (JSONException e) {
            operation.setStatus(getContextResources().getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing APP_HIDE payload.");
            getResultBuilder().build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void unhideApp(Operation operation) throws AndroidAgentException {
        String packageName = null;
        try {
            JSONObject hideAppData = new JSONObject(operation.getPayLoad().toString());
            if (!hideAppData.isNull(getContextResources().getString(R.string.intent_extra_package))) {
                packageName = (String) hideAppData.get(getContextResources().getString(R.string.intent_extra_package));
            }

            operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
            getResultBuilder().build(operation);

            if (packageName != null && !packageName.isEmpty()) {
                setApplicationHidden(packageName, false);
            }

            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "App-unhide successful.");
            }
        } catch (JSONException e) {
            operation.setStatus(getContextResources().getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing APP_UN_HIDE payload.");
            getResultBuilder().build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void blockUninstallByPackageName(Operation operation) throws AndroidAgentException {
        String packageName = null;
        try {
            JSONObject hideAppData = new JSONObject(operation.getPayLoad().toString());
            if (!hideAppData.isNull(getContextResources().getString(R.string.intent_extra_package))) {
                packageName = (String) hideAppData.get(getContextResources().getString(R.string.intent_extra_package));
            }

            operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
            getResultBuilder().build(operation);

            if (packageName != null && !packageName.isEmpty()) {
                getDevicePolicyManager().setUninstallBlocked(getCdmDeviceAdmin(), packageName, true);
            }

            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "App-Uninstall-Block successful.");
            }
        } catch (JSONException e) {
            operation.setStatus(getContextResources().getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing APP_BLOCK payload.");
            getResultBuilder().build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void setProfileName(Operation operation) throws AndroidAgentException {
        String profileName = null;
        try {
            JSONObject setProfileNameData = new JSONObject(operation.getPayLoad().toString());
            if (!setProfileNameData.isNull(getContextResources().getString(R.string.intent_extra_profile_name))) {
                profileName = (String) setProfileNameData.get(getContextResources().getString(
                        R.string.intent_extra_profile_name));
            }

            operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
            getResultBuilder().build(operation);

            if (profileName != null && !profileName.isEmpty()) {
                getDevicePolicyManager().setProfileName(getCdmDeviceAdmin(), profileName);
            }

            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Profile Name is set");
            }
        } catch (JSONException e) {
            operation.setStatus(getContextResources().getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing PROFILE payload.");
            getResultBuilder().build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void handleOwnersRestriction(Operation operation) {
        boolean isEnable = operation.isEnabled();
        String key = operation.getCode();
        operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
        getResultBuilder().build(operation);
        if (Constants.Operation.DISALLOW_INSTALL_UNKNOWN_SOURCES.equals(key)) {
            Preference.putBoolean(getContext(),
                    Constants.PreferenceFlag.DISALLOW_UNKNOWN_SOURCES, isEnable);
            CommonUtils.allowUnknownSourcesForProfile(getContext(), !isEnable);
        }
        if (isEnable) {
            getDevicePolicyManager().addUserRestriction(getCdmDeviceAdmin(), getPermissionConstantValue(key));
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Restriction added: " + key);
            }
        } else {
            getDevicePolicyManager().clearUserRestriction(getCdmDeviceAdmin(), getPermissionConstantValue(key));
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Restriction cleared: " + key);
            }
        }
    }

    @Override
    public void handleDeviceOwnerRestriction(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
        operation.setOperationResponse("Operation not supported.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Operation not supported.");
    }

    @Override
    public void configureWorkProfile(Operation operation) throws AndroidAgentException {
        String profileName;
        String enableSystemAppsData;
        String hideSystemAppsData;
        String unhideSystemAppsData;
        String googlePlayAppsData;
        try {
            JSONObject profileData = new JSONObject(operation.getPayLoad().toString());
            if (!profileData.isNull(getContextResources().getString(R.string.intent_extra_profile_name))) {
                profileName = (String) profileData.get(getContextResources().getString(
                        R.string.intent_extra_profile_name));
                changeProfileName(profileName);
            }
            if (!profileData.isNull(getContextResources().getString(R.string.intent_extra_enable_system_apps))) {
                // generate the System app list which are configured by user and received to agent as a single String
                // with packages separated by Commas.
                enableSystemAppsData = (String) profileData.get(getContextResources().getString(
                        R.string.intent_extra_enable_system_apps));
                List<String> systemAppList = Arrays.asList(enableSystemAppsData.split(getContextResources().getString(
                        R.string.split_delimiter)));
                for (String packageName : systemAppList) {
                    enableSystemApp(packageName);
                    setApplicationHidden(packageName, false);
                }
            }
            if (!profileData.isNull(getContextResources().getString(R.string.intent_extra_hide_system_apps))) {
                // generate the System app list which are configured by user and received to agent as a single String
                // with packages separated by Commas.
                hideSystemAppsData = (String) profileData.get(getContextResources().getString(
                        R.string.intent_extra_hide_system_apps));
                List<String> systemAppList = Arrays.asList(hideSystemAppsData.split(getContextResources().getString(
                        R.string.split_delimiter)));
                for (String packageName : systemAppList) {
                    setApplicationHidden(packageName, true);
                }
            }
            if (!profileData.isNull(getContextResources().getString(R.string.intent_extra_unhide_system_apps))) {
                // generate the System app list which are configured by user and received to agent as a single String
                // with packages separated by Commas.
                unhideSystemAppsData = (String) profileData.get(getContextResources().getString(
                        R.string.intent_extra_unhide_system_apps));
                List<String> systemAppList = Arrays.asList(unhideSystemAppsData.split(getContextResources().getString(
                        R.string.split_delimiter)));
                for (String packageName : systemAppList) {
                    setApplicationHidden(packageName, false);
                }
            }
            if (!profileData.isNull(getContextResources().getString(R.string.intent_extra_enable_google_play_apps))) {
                googlePlayAppsData = (String) profileData.get(getContextResources().getString(
                        R.string.intent_extra_enable_google_play_apps));
                List<String> systemAppList = Arrays.asList(googlePlayAppsData.split(getContextResources().getString(
                        R.string.split_delimiter)));
                for (String packageName : systemAppList) {
                    enableGooglePlayApps(packageName);
                }
            }
        } catch (JSONException e) {
            operation.setStatus(getContextResources().getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing WORK_PROFILE payload.");
            getResultBuilder().build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
    }

    @Override
    public void passOperationToSystemApp(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
        operation.setOperationResponse("Operation not supported.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Operation not supported.");
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void restrictAccessToApplications(Operation operation) throws AndroidAgentException {
        AppRestriction appRestriction = CommonUtils.getAppRestrictionTypeAndList(operation, getResultBuilder(), getContextResources());
        if (Constants.AppRestriction.BLACK_LIST.equals(appRestriction.getRestrictionType())) {
            String disallowedApps = "";
            for (String packageName : appRestriction.getRestrictedList()) {
                setApplicationHidden(packageName, true);
                disallowedApps = disallowedApps + getContext().getString(R.string.whitelist_package_split_regex) + packageName;
            }
            Preference.putString(getContext(),
                    Constants.AppRestriction.DISALLOWED_APPS, disallowedApps);
            Preference.putString(getContext(),
                    Constants.AppRestriction.BLACK_LIST_APPS, appRestriction.toString());
        } else if (Constants.AppRestriction.WHITE_LIST.equals(appRestriction.getRestrictionType())) {
            ArrayList appList = (ArrayList) appRestriction.getRestrictedList();
            JSONArray whiteListApps = new JSONArray();
            for (Object appObj : appList) {
                JSONObject app = new JSONObject();
                try {
                    app.put(Constants.AppRestriction.PACKAGE_NAME, appObj.toString());
                    app.put(Constants.AppRestriction.RESTRICTION_TYPE, Constants.AppRestriction.WHITE_LIST);
                    whiteListApps.put(app);
                } catch (JSONException e) {
                    operation.setStatus(getContextResources().getString(R.string.operation_value_error));
                    operation.setOperationResponse("Error in parsing app white-list payload.");
                    getResultBuilder().build(operation);
                    throw new AndroidAgentException("Invalid JSON format for app white-list bundle.", e);
                }
            }
            Preference.putString(getContext(),
                    Constants.AppRestriction.WHITE_LIST_APPS, whiteListApps.toString());
            validateInstalledApps();
        }
        operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
        getResultBuilder().build(operation);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void validateInstalledApps() {
        List<String> alreadyInstalledApps = CommonUtils.getInstalledAppPackagesByUser(getContext());
        JSONObject permittedApp;
        String permissionName;
        Boolean isAllowed = false;
        String permittedPackageName;
        JSONArray whiteListApps;
        try {
            whiteListApps = new JSONArray(Preference.getString(getContext(), Constants.AppRestriction.WHITE_LIST_APPS));
            String disallowedApps = "";
            for (String packageName : alreadyInstalledApps) {
                if (!packageName.equals(getCdmDeviceAdmin().getPackageName())) {     //Skip agent app.
                    for (int i = 0; i < whiteListApps.length(); i++) {
                        permittedApp = new JSONObject(whiteListApps.getString(i));
                        permittedPackageName = permittedApp.getString(Constants.AppRestriction.PACKAGE_NAME);
                        if (Objects.equals(permittedPackageName, packageName)) {
                            permissionName = permittedApp.getString(Constants.AppRestriction.RESTRICTION_TYPE);
                            if (permissionName.equals(Constants.AppRestriction.WHITE_LIST)) {
                                isAllowed = true;
                                break;
                            }
                        }
                    }
                    if (!isAllowed) {
                        disallowedApps = disallowedApps + getContext().getString(R.string.whitelist_package_split_regex) + packageName;
                        setApplicationHidden(packageName, true);
                    }
                    isAllowed = false;
                }
            }
            Preference.putString(getContext(), Constants.AppRestriction.DISALLOWED_APPS, disallowedApps);
        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON format..");
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void changeProfileName(String name) {
        getDevicePolicyManager().setProfileName(getCdmDeviceAdmin(), name);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void enableSystemApp(String packageName) {
        try {
            getDevicePolicyManager().enableSystemApp(getCdmDeviceAdmin(), packageName);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "App is not available on the device to enable. " + e.toString());
        }
    }

    @Override
    public void setPolicyBundle(Operation operation) throws AndroidAgentException {
        getResultBuilder().build(operation);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void setRuntimePermissionPolicy(Operation operation) throws AndroidAgentException {
        JSONObject restrictionPolicyData;
        JSONObject restrictionAppData;
        JSONArray permittedApplicationsPayload;
        int defaultPermissionType;
        String permissionName;
        int permissionType;
        String packageName;

        try {
            restrictionPolicyData = new JSONObject(operation.getPayLoad().toString());
            if (!restrictionPolicyData.isNull("defaultType")) {
                defaultPermissionType = Integer.parseInt(restrictionPolicyData.
                        getString("defaultType"));
                getDevicePolicyManager().setPermissionPolicy(getCdmDeviceAdmin(), defaultPermissionType);
                Log.d(TAG, "Default runtime-permission type changed.");
            }
            if (!restrictionPolicyData.isNull("permittedApplications")) {
                permittedApplicationsPayload = restrictionPolicyData.getJSONArray("permittedApplications");
                for (int i = 0; i < permittedApplicationsPayload.length(); i++) {
                    restrictionAppData = new JSONObject(permittedApplicationsPayload.getString(i));
                    permissionName = restrictionAppData.getString(Constants.RuntimePermissionPolicy.PERMISSION_NAME);
                    permissionType = Integer.parseInt(restrictionAppData.getString(Constants.RuntimePermissionPolicy.PERMISSION_TYPE));
                    packageName = restrictionAppData.getString(Constants.RuntimePermissionPolicy.PACKAGE_NAME);
                    if (!permissionName.equals(Constants.RuntimePermissionPolicy.ALL_PERMISSIONS)) {
                        setAppRuntimePermission(packageName, permissionName, permissionType);
                    } else {
                        setAppAllRuntimePermission(packageName, permissionType);
                    }
                }
            }

        } catch (JSONException e) {
            operation.setStatus(getContextResources().getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing PROFILE payload.");
            getResultBuilder().build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void setAppRuntimePermission(String packageName, String permission, int permissionType) {
        getDevicePolicyManager().setPermissionGrantState(
                getCdmDeviceAdmin(), packageName, permission, permissionType);
        Log.d(TAG, "App Permission Changed" + packageName + " : " + permission);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void setAppAllRuntimePermission(String packageName, int permissionType) {
        String[] permissionList = getContextResources().getStringArray(R.array.runtime_permission_list_array);
        for (String permission : permissionList) {
            setAppRuntimePermission(packageName, permission, permissionType);
        }
    }

    public void setStatusBarDisabled(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
        operation.setOperationResponse("Operation not supported.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Operation not supported.");
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void setScreenCaptureDisabled(Operation operation) throws AndroidAgentException {
        boolean isEnable = operation.isEnabled();
        operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
        getResultBuilder().build(operation);
        if (isEnable) {
            getDevicePolicyManager().setScreenCaptureDisabled(getCdmDeviceAdmin(), true);
        } else {
            getDevicePolicyManager().setScreenCaptureDisabled(getCdmDeviceAdmin(), false);
        }
    }

    @Override
    public void setAutoTimeRequired(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
        operation.setOperationResponse("Operation not supported.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Operation not supported.");
    }

    @Override
    public void configureCOSUProfile(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
        operation.setOperationResponse("Operation not supported.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Operation not supported.");
    }

    @Override
    public ComplianceFeature checkWorkProfilePolicy(Operation operation, ComplianceFeature policy) throws AndroidAgentException {
        String profileName;
        String systemAppsData;
        String googlePlayAppsData;
        try {
            JSONObject profileData = new JSONObject(operation.getPayLoad().toString());
            if (!profileData.isNull(getContextResources().getString(R.string.intent_extra_profile_name))) {
                profileName = (String) profileData.get(getContextResources().getString(
                        R.string.intent_extra_profile_name));
                //yet there is no method is given to get the current profile name.
                //add a method to test whether profile name is set correctly once introduced.
            }
            if (!profileData.isNull(getContextResources().getString(R.string.intent_extra_enable_system_apps))) {
                // generate the System app list which are configured by user and received to agent as a single String
                // with packages separated by Commas.
                systemAppsData = (String) profileData.get(getContextResources().getString(
                        R.string.intent_extra_enable_system_apps));
                List<String> systemAppList = Arrays.asList(systemAppsData.split(getContextResources().getString(
                        R.string.split_delimiter)));
                for (String packageName : systemAppList) {
                    if (!getApplicationManager().isPackageInstalled(packageName)) {
                        policy.setCompliance(false);
                        policy.setMessage(getContextResources().getString(R.string.error_work_profile_policy));
                        return policy;
                    }
                }
            }
            if (!profileData.isNull(getContextResources().getString(R.string.intent_extra_enable_google_play_apps))) {
                googlePlayAppsData = (String) profileData.get(getContextResources().getString(
                        R.string.intent_extra_enable_google_play_apps));
                List<String> playStoreAppList = Arrays.asList(googlePlayAppsData.split(getContextResources().getString(
                        R.string.split_delimiter)));
                for (String packageName : playStoreAppList) {
                    if (!getApplicationManager().isPackageInstalled(packageName)) {
                        policy.setCompliance(false);
                        policy.setMessage(getContextResources().getString(R.string.error_work_profile_policy));
                        return policy;
                    }
                }
            }
        } catch (JSONException e) {
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
        policy.setCompliance(true);
        return policy;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setApplicationHidden(String packageName, boolean isHidden) {
        getDevicePolicyManager().setApplicationHidden(getCdmDeviceAdmin(), packageName, isHidden);
    }

    @Override
    public ComplianceFeature checkRuntimePermissionPolicy(Operation operation, ComplianceFeature policy) throws AndroidAgentException {
        policy.setCompliance(true);
        return policy;
    }

    private void enableGooglePlayApps(String packageName) {
        triggerGooglePlayApp(packageName);
    }

    private String getPermissionConstantValue(String key) {
        return getContext().getString(getContextResources().getIdentifier(
                key.toString(), "string", getContext().getPackageName()));
    }
}