/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.UserManager;
import android.support.annotation.RequiresApi;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.iot.agent.AlertActivity;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.activities.ServerConfigsActivity;
import org.wso2.iot.agent.beans.AppRestriction;
import org.wso2.iot.agent.beans.ComplianceFeature;
import org.wso2.iot.agent.beans.Notification;
import org.wso2.iot.agent.beans.Operation;
import org.wso2.iot.agent.services.NotificationService;
import org.wso2.iot.agent.services.kiosk.KioskAlarmReceiver;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OperationManagerDeviceOwner extends OperationManager {
    private static final String TAG = OperationManagerDeviceOwner.class.getSimpleName();
    private NotificationService notificationService;
    private static final String STATUS = "status";

    public OperationManagerDeviceOwner(Context context) {
        super(context);
        notificationService = super.getNotificationService();
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
        operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
        getResultBuilder().build(operation);

        getDevicePolicyManager().setPasswordQuality(getCdmDeviceAdmin(),
                DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);
        getDevicePolicyManager().setPasswordMinimumLength(getCdmDeviceAdmin(), getDefaultPasswordLength());
        getDevicePolicyManager().resetPassword(getContextResources().getString(R.string.shared_pref_default_string),
                DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
        getDevicePolicyManager().lockNow();
        getDevicePolicyManager().setPasswordQuality(getCdmDeviceAdmin(),
                DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Password cleared");
        }
    }

    @Override
    public void installAppBundle(Operation operation) throws AndroidAgentException {
        try {
            if (operation.getCode().equals(Constants.Operation.INSTALL_APPLICATION)||
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

    /**
     * Install an Application.
     *
     * @param operation - Operation object.
     */
    private void installApplication(JSONObject data, org.wso2.iot.agent.beans.Operation operation) throws AndroidAgentException {
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
                    if(data.has(getContextResources().getString(R.string.app_schedule))){
                        schedule = data.getString(getContextResources().getString(R.string.app_schedule));
                    }
                    operation.setStatus(getContextResources().getString(R.string.operation_value_progress));
                    getResultBuilder().build(operation);
                    getAppList().installApp(appUrl, schedule, operation);

                } else if (type.equalsIgnoreCase(getContextResources().getString(R.string.intent_extra_public))) {
                    appUrl = data.getString(getContextResources().getString(R.string.app_identifier));
                    operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
                    getResultBuilder().build(operation);
                    Preference.putInt(getContext(), getContext().getResources().getString(R.string.app_install_id),
                            operation.getId());
                    Preference.putString(getContext(), getContext().getResources().getString(R.string.app_install_code),
                            operation.getCode());
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
        boolean doEncrypt = operation.isEnabled();
        JSONObject result = new JSONObject();

        if (doEncrypt &&
                getDevicePolicyManager().getStorageEncryptionStatus() != DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED &&
                (getDevicePolicyManager().getStorageEncryptionStatus() == DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE)) {

            getDevicePolicyManager().setStorageEncryption(getCdmDeviceAdmin(), doEncrypt);
            Intent intent = new Intent(DevicePolicyManager.ACTION_START_ENCRYPTION);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);

        } else if (!doEncrypt &&
                getDevicePolicyManager().getStorageEncryptionStatus() != DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED &&
                (getDevicePolicyManager().getStorageEncryptionStatus() == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ||
                        getDevicePolicyManager().getStorageEncryptionStatus() == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVATING)) {

            getDevicePolicyManager().setStorageEncryption(getCdmDeviceAdmin(), doEncrypt);
        }

        try {
            String status;
            if (getDevicePolicyManager().getStorageEncryptionStatus() !=
                    DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED) {
                status = getContextResources().getString(R.string.shared_pref_default_status);
                result.put(getContextResources().getString(R.string.operation_status), status);

            } else {
                status = getContextResources().getString(R.string.shared_pref_false_status);
                result.put(getContextResources().getString(R.string.operation_status), status);
            }
        } catch (JSONException e) {
            operation.setStatus(getContextResources().getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing ENCRYPT payload.");
            getResultBuilder().build(operation);
            throw new AndroidAgentException("Issue in parsing json", e);
        }
        operation.setPayLoad(result.toString());
        operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
        getResultBuilder().build(operation);
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Encryption process started");
        }
    }

    @Override
    public void setPasswordPolicy(Operation operation) throws AndroidAgentException {
        int attempts, length, history, specialChars;
        String alphanumeric, complex;
        boolean isAlphanumeric, isComplex;
        long timout;

        operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
        getResultBuilder().build(operation);

        try {
            JSONObject policyData = new JSONObject(operation.getPayLoad().toString());
            if (!policyData.isNull(getContextResources().getString(R.string.policy_password_max_failed_attempts)) &&
                    policyData.get(getContextResources().getString(R.string.policy_password_max_failed_attempts)) != null) {
                if (!policyData.get(getContextResources().getString(R.string.policy_password_max_failed_attempts)).toString().isEmpty()) {
                    attempts = policyData.getInt(getContextResources().getString(R.string.policy_password_max_failed_attempts));
                    getDevicePolicyManager().setMaximumFailedPasswordsForWipe(getCdmDeviceAdmin(), attempts);
                }
            }

            if (!policyData.isNull(getContextResources().getString(R.string.policy_password_min_length)) &&
                    policyData.get(getContextResources().getString(R.string.policy_password_min_length)) != null) {
                if (!policyData.get(getContextResources().getString(R.string.policy_password_min_length)).toString().isEmpty()) {
                    length = policyData.getInt(getContextResources().getString(R.string.policy_password_min_length));
                    getDevicePolicyManager().setPasswordMinimumLength(getCdmDeviceAdmin(), length);
                } else {
                    getDevicePolicyManager().setPasswordMinimumLength(getCdmDeviceAdmin(), getDefaultPasswordMinLength());
                }
            }

            if (!policyData.isNull(getContextResources().getString(R.string.policy_password_pin_history)) &&
                    policyData.get(getContextResources().getString(R.string.policy_password_pin_history)) != null) {
                if (!policyData.get(getContextResources().getString(R.string.policy_password_pin_history)).toString().isEmpty()) {
                    history = policyData.getInt(getContextResources().getString(R.string.policy_password_pin_history));
                    getDevicePolicyManager().setPasswordHistoryLength(getCdmDeviceAdmin(), history);
                } else {
                    getDevicePolicyManager().setPasswordHistoryLength(getCdmDeviceAdmin(), getDefaultPasswordLength());
                }
            }

            if (!policyData.isNull(getContextResources().getString(R.string.policy_password_min_complex_chars)) &&
                    policyData.get(getContextResources().getString(R.string.policy_password_min_complex_chars)) != null) {
                if (!policyData.get(getContextResources().getString(R.string.policy_password_min_complex_chars)).toString().isEmpty()) {
                    specialChars = policyData.getInt(getContextResources().getString(R.string.policy_password_min_complex_chars));
                    getDevicePolicyManager().setPasswordMinimumSymbols(getCdmDeviceAdmin(), specialChars);
                } else {
                    getDevicePolicyManager().setPasswordMinimumSymbols(getCdmDeviceAdmin(), getDefaultPasswordLength());
                }
            }

            if (!policyData.isNull(getContextResources().getString(R.string.policy_password_require_alphanumeric)) &&
                    policyData.get(getContextResources().getString(R.string.policy_password_require_alphanumeric)) != null) {
                if (policyData.get(getContextResources().getString(
                        R.string.policy_password_require_alphanumeric)) instanceof String) {
                    alphanumeric = (String) policyData.get(getContextResources().getString(
                            R.string.policy_password_require_alphanumeric));
                    if (alphanumeric.equals(getContextResources().getString(R.string.shared_pref_default_status))) {
                        getDevicePolicyManager().setPasswordQuality(getCdmDeviceAdmin(),
                                DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC);
                    }
                } else if (policyData.get(getContextResources().getString(
                        R.string.policy_password_require_alphanumeric)) instanceof Boolean) {
                    isAlphanumeric = policyData.getBoolean(getContextResources().getString(
                            R.string.policy_password_require_alphanumeric));
                    if (isAlphanumeric) {
                        getDevicePolicyManager().setPasswordQuality(getCdmDeviceAdmin(),
                                DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC);
                    }
                }
            }

            if (!policyData.isNull(getContextResources().getString(R.string.policy_password_allow_simple)) &&
                    policyData.get(getContextResources().getString(R.string.policy_password_allow_simple)) != null) {
                if (policyData.get(getContextResources().getString(
                        R.string.policy_password_allow_simple)) instanceof String) {
                    complex = (String) policyData.get(getContextResources().getString(
                            R.string.policy_password_allow_simple));
                    if (!complex.equals(getContextResources().getString(R.string.shared_pref_default_status))) {
                        getDevicePolicyManager().setPasswordQuality(getCdmDeviceAdmin(),
                                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX);
                    }
                } else if (policyData.get(getContextResources().getString(
                        R.string.policy_password_allow_simple)) instanceof Boolean) {
                    isComplex = policyData.getBoolean(
                            getContextResources().getString(R.string.policy_password_allow_simple));
                    if (!isComplex) {
                        getDevicePolicyManager().setPasswordQuality(getCdmDeviceAdmin(),
                                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX);
                    }
                }
            }

            if (!policyData.isNull(getContextResources().getString(R.string.policy_password_pin_age_in_days)) &&
                    policyData.get(getContextResources().getString(R.string.policy_password_pin_age_in_days)) != null) {
                if (!policyData.get(getContextResources().getString(R.string.policy_password_pin_age_in_days)).toString().isEmpty()) {
                    int daysOfExp = policyData.getInt(getContextResources().getString(R.string.policy_password_pin_age_in_days));
                    timout = daysOfExp * getDayMillisecondsMultiplier();
                    getDevicePolicyManager().setPasswordExpirationTimeout(getCdmDeviceAdmin(), timout);
                }
            }

            if (!getDevicePolicyManager().isActivePasswordSufficient()) {
                Intent intent = new Intent(getContext(), AlertActivity.class);
                intent.putExtra(getContextResources().getString(R.string.intent_extra_type),
                        getContextResources().getString(R.string.intent_extra_password_setting));
                intent.putExtra(getContextResources().getString(R.string.intent_extra_message_text),
                        getContextResources().getString(R.string.policy_violation_password_tail));
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            }

            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Password policy set");
            }
        } catch (JSONException e) {
            operation.setStatus(getContextResources().getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing PASSWORD_POLICY payload.");
            getResultBuilder().build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
    }

    @Override
    public void changeLockCode(Operation operation) throws AndroidAgentException {
        getDevicePolicyManager().setPasswordMinimumLength(getCdmDeviceAdmin(), getDefaultPasswordMinLength());
        String password = null;

        try {
            JSONObject lockData = new JSONObject(operation.getPayLoad().toString());
            if (!lockData.isNull(getContextResources().getString(R.string.intent_extra_lock_code))) {
                password =
                        (String) lockData.get(getContextResources().getString(R.string.intent_extra_lock_code));
            }

            operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
            getResultBuilder().build(operation);

            if (password != null && !password.isEmpty()) {
                getDevicePolicyManager().resetPassword(password,
                                                       DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
                getDevicePolicyManager().lockNow();
            }

            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Lock code changed");
            }
        } catch (JSONException e) {
            operation.setStatus(getContextResources().getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing CHANGE_LOCK_CODE payload.");
            getResultBuilder().build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void enterpriseWipe(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
        getResultBuilder().build(operation);

        CommonUtils.disableAdmin(getContext());

        Intent intent = new Intent(getContext(), ServerConfigsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Started enterprise wipe");
        }
    }

    @Override
    public void disenrollDevice(Operation operation) {
        boolean status = operation.isEnabled();
        if (status) {
            CommonUtils.disableAdmin(getContext());
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
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
                getDevicePolicyManager().setApplicationHidden(getCdmDeviceAdmin(), packageName, true);
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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
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
                getDevicePolicyManager().setApplicationHidden(getCdmDeviceAdmin(), packageName, false);
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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void setProfileName(Operation operation) throws AndroidAgentException {
        //sets the name of the user since the agent is the device owner.
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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void handleOwnersRestriction(Operation operation) throws AndroidAgentException {
        boolean isEnable = operation.isEnabled();
        String key = "";
        switch (operation.getCode()) {
            case Constants.Operation.ALLOW_PARENT_PROFILE_APP_LINKING:
                key = UserManager.ALLOW_PARENT_PROFILE_APP_LINKING;
                break;
            case Constants.Operation.DISALLOW_CONFIG_VPN:
                key = UserManager.DISALLOW_CONFIG_VPN;
                break;
            case Constants.Operation.DISALLOW_INSTALL_APPS:
                key = UserManager.DISALLOW_INSTALL_APPS;
                break;
            case Constants.Operation.DISALLOW_ADJUST_VOLUME:
                key = UserManager.DISALLOW_ADJUST_VOLUME;
                break;
            case Constants.Operation.DISALLOW_SMS:
                key = UserManager.DISALLOW_SMS;
                break;
            case Constants.Operation.DISALLOW_CONFIG_CELL_BROADCASTS:
                key = UserManager.DISALLOW_CONFIG_CELL_BROADCASTS;
                break;
            case Constants.Operation.DISALLOW_CONFIG_BLUETOOTH:
                key = UserManager.DISALLOW_CONFIG_BLUETOOTH;
                break;
            case Constants.Operation.DISALLOW_CONFIG_MOBILE_NETWORKS:
                key = UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS;
                break;
            case Constants.Operation.DISALLOW_CONFIG_TETHERING:
                key = UserManager.DISALLOW_CONFIG_TETHERING;
                break;
            case Constants.Operation.DISALLOW_CONFIG_WIFI:
                key = UserManager.DISALLOW_CONFIG_WIFI;
                break;
            case Constants.Operation.DISALLOW_SAFE_BOOT:
                key = UserManager.DISALLOW_SAFE_BOOT;
                break;
            case Constants.Operation.DISALLOW_OUTGOING_CALLS:
                key = UserManager.DISALLOW_OUTGOING_CALLS;
                break;
            case Constants.Operation.DISALLOW_MOUNT_PHYSICAL_MEDIA:
                key = UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA;
                break;
            case Constants.Operation.DISALLOW_CREATE_WINDOWS:
                key = UserManager.DISALLOW_CREATE_WINDOWS;
                break;
            case Constants.Operation.DISALLOW_FACTORY_RESET:
                key = UserManager.DISALLOW_FACTORY_RESET;
                break;
            case Constants.Operation.DISALLOW_REMOVE_USER:
                key = UserManager.DISALLOW_REMOVE_USER;
                break;
            case Constants.Operation.DISALLOW_ADD_USER:
                key = UserManager.DISALLOW_ADD_USER;
                break;
            case Constants.Operation.DISALLOW_NETWORK_RESET:
                key = UserManager.DISALLOW_NETWORK_RESET;
                break;
            case Constants.Operation.DISALLOW_UNMUTE_MICROPHONE:
                key = UserManager.DISALLOW_UNMUTE_MICROPHONE;
                break;
            case Constants.Operation.DISALLOW_USB_FILE_TRANSFER:
                key = UserManager.DISALLOW_USB_FILE_TRANSFER;
                break;
            case Constants.Operation.DISALLOW_CONFIG_CREDENTIALS:
                key = UserManager.DISALLOW_CONFIG_CREDENTIALS;
                break;
            case Constants.Operation.DISALLOW_APPS_CONTROL:
                key = UserManager.DISALLOW_APPS_CONTROL;
                break;
            case Constants.Operation.DISALLOW_CROSS_PROFILE_COPY_PASTE:
                key = UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE;
                break;
            case Constants.Operation.DISALLOW_DEBUGGING_FEATURES:
                key = UserManager.DISALLOW_DEBUGGING_FEATURES;
                break;
            case Constants.Operation.DISALLOW_INSTALL_UNKNOWN_SOURCES:
                key = UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES;
                break;
            case Constants.Operation.DISALLOW_MODIFY_ACCOUNTS:
                key = UserManager.DISALLOW_MODIFY_ACCOUNTS;
                break;
            case Constants.Operation.DISALLOW_OUTGOING_BEAM:
                key = UserManager.DISALLOW_OUTGOING_BEAM;
                break;
            case Constants.Operation.DISALLOW_SHARE_LOCATION:
                key = UserManager.DISALLOW_SHARE_LOCATION;
                break;
            case Constants.Operation.DISALLOW_UNINSTALL_APPS:
                key = UserManager.DISALLOW_UNINSTALL_APPS;
                break;
            case Constants.Operation.ENSURE_VERIFY_APPS:
                key = UserManager.ENSURE_VERIFY_APPS;
                break;
            default:
                key = operation.getCode();
                break;
        }
        operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
        getResultBuilder().build(operation);
        if (isEnable) {
            getDevicePolicyManager().addUserRestriction(getCdmDeviceAdmin(), key);
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Restriction added: " + key);
            }
        } else {
            getDevicePolicyManager().clearUserRestriction(getCdmDeviceAdmin(), key);
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Restriction cleared: " + key);
            }
        }
    }

    @Override
    public void handleDeviceOwnerRestriction(Operation operation) throws AndroidAgentException {
        handleOwnersRestriction(operation);
    }

    @Override
    public void configureWorkProfile(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
        operation.setOperationResponse("Operation not supported.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Operation not supported.");
    }

    @Override
    public void passOperationToSystemApp(Operation operation) throws AndroidAgentException {
        if(getApplicationManager().isPackageInstalled(Constants.SYSTEM_SERVICE_PACKAGE)) {
            CommonUtils.callSystemApp(getContext(),operation.getCode(),
                    Boolean.toString(operation.isEnabled()), null);
        } else {
            if (operation.isEnabled()) {
                Log.e(TAG, "Invalid operation code received");
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void restrictAccessToApplications(Operation operation) throws AndroidAgentException {
        AppRestriction appRestriction = CommonUtils.
                getAppRestrictionTypeAndList(operation, getResultBuilder(), getContextResources());

        ArrayList appList = (ArrayList)appRestriction.getRestrictedList();
        JSONArray appRestrictions = new JSONArray();
        for (Object appObj: appList) {
            JSONObject app = new JSONObject();
            try {
                app.put(Constants.AppRestriction.PACKAGE_NAME,appObj.toString());
                app.put(Constants.AppRestriction.
                        RESTRICTION_TYPE, appRestriction.getRestrictionType());
                appRestrictions.put(app);
            } catch (JSONException e) {
                operation.setStatus(getContextResources().
                        getString(R.string.operation_value_error));
                operation.setOperationResponse("Error in parsing app restriction payload.");
                getResultBuilder().build(operation);
                throw new AndroidAgentException("Invalid JSON format for app restriction bundle.", e);
            }
        }

        if (Constants.AppRestriction.WHITE_LIST.equals(appRestriction.getRestrictionType())) {
            Preference.putString(getContext(),
                    Constants.AppRestriction.WHITE_LIST_APPS, appRestrictions.toString());
            validateInstalledApps();
        } else if (Constants.AppRestriction.BLACK_LIST.equals(appRestriction.getRestrictionType())) {
            String disallowedApps = "";
            for (String packageName : appRestriction.getRestrictedList()) {
                getDevicePolicyManager().setApplicationHidden(getCdmDeviceAdmin(), packageName, true);
                disallowedApps = disallowedApps + getContext().getString(R.string.whitelist_package_split_regex) + packageName;
            }
            Preference.putString(getContext(),
                    Constants.AppRestriction.DISALLOWED_APPS, disallowedApps);
            Preference.putString(getContext(),
                    Constants.AppRestriction.BLACK_LIST_APPS, appRestrictions.toString());
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
            whiteListApps = new JSONArray(Preference.getString(getContext(),
                    Constants.AppRestriction.WHITE_LIST_APPS));
            String disallowedApps = "";
            for (String packageName : alreadyInstalledApps) {
                if (!packageName.equals(getCdmDeviceAdmin().getPackageName())) {     //Skip agent app.
                    for (int i = 0; i < whiteListApps.length(); i++) {
                        permittedApp = new JSONObject(whiteListApps.getString(i));
                        permittedPackageName = permittedApp.
                                getString(Constants.AppRestriction.PACKAGE_NAME);
                        if (Objects.equals(permittedPackageName, packageName)) {
                            permissionName = permittedApp.
                                    getString(Constants.AppRestriction.RESTRICTION_TYPE);
                            if (permissionName.equals(Constants.AppRestriction.WHITE_LIST)) {
                                isAllowed = true;
                                break;
                            }
                        }
                    }
                    if (!isAllowed) {
                        disallowedApps = disallowedApps + getContext().getString(R.string.whitelist_package_split_regex) + packageName;
                        getDevicePolicyManager().
                                setApplicationHidden(getCdmDeviceAdmin(), packageName, true);
                    }
                    isAllowed = false;
                }
            }
            Preference.putString(getContext(),
                    Constants.AppRestriction.DISALLOWED_APPS, disallowedApps);
        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON format..");
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
        String permissionName;
        int permissionType;
        String packageName;

        int defaultPermissionType;

        try {
            restrictionPolicyData = new JSONObject(operation.getPayLoad().toString());
            if (!restrictionPolicyData.isNull(
                    Constants.RuntimePermissionPolicy.DEFAULT_PERMISSION_TYPE)) {
                defaultPermissionType = Integer.parseInt(restrictionPolicyData.
                        getString(Constants.RuntimePermissionPolicy.DEFAULT_PERMISSION_TYPE));
                getDevicePolicyManager().
                        setPermissionPolicy(getCdmDeviceAdmin(), defaultPermissionType);
                Log.d(TAG, "Default runtime-permission type changed.");
            }
            if (!restrictionPolicyData.isNull(Constants.RuntimePermissionPolicy.PERMITTED_APPS)) {
                permittedApplicationsPayload = restrictionPolicyData.getJSONArray(
                        Constants.RuntimePermissionPolicy.PERMITTED_APPS);
                for(int i = 0; i <permittedApplicationsPayload.length(); i++) {
                    restrictionAppData = new JSONObject(permittedApplicationsPayload.getString(i));
                    permissionName = restrictionAppData.
                            getString(Constants.RuntimePermissionPolicy.PERMISSION_NAME);
                    permissionType = Integer.parseInt(restrictionAppData.
                            getString(Constants.RuntimePermissionPolicy.PERMISSION_TYPE));
                    packageName = restrictionAppData.
                            getString(Constants.RuntimePermissionPolicy.PACKAGE_NAME);

                    if(!permissionName.equals(Constants.RuntimePermissionPolicy.ALL_PERMISSIONS)){
                        setAppRuntimePermission(packageName, permissionName, permissionType);
                    }
                    else {
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
        getDevicePolicyManager().
                setPermissionGrantState(getCdmDeviceAdmin(),packageName,permission,permissionType);
        Log.d(TAG,"App Permission Changed"+ packageName + " : " + permission );
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void setAppAllRuntimePermission(String packageName, int permissionType) {
        String[] permissionList = getContextResources().
                getStringArray(R.array.runtime_permission_list_array);
        for(String permission: permissionList){
            setAppRuntimePermission(packageName, permission, permissionType);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void setStatusBarDisabled(Operation operation) throws AndroidAgentException {
        boolean isEnable = operation.isEnabled();
        operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
        getResultBuilder().build(operation);
        if (isEnable) {
            getDevicePolicyManager().setStatusBarDisabled(getCdmDeviceAdmin(), true);
        }
        else {
            getDevicePolicyManager().setStatusBarDisabled(getCdmDeviceAdmin(), false);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void setScreenCaptureDisabled(Operation operation) throws AndroidAgentException {
        boolean isEnable = operation.isEnabled();
        operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
        getResultBuilder().build(operation);
        if (isEnable) {
            getDevicePolicyManager().setScreenCaptureDisabled(getCdmDeviceAdmin(), true);
        }
        else {
            getDevicePolicyManager().setScreenCaptureDisabled(getCdmDeviceAdmin(), false);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void setAutoTimeRequired(Operation operation) throws AndroidAgentException {
        boolean isEnable = operation.isEnabled();
        operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
        getResultBuilder().build(operation);
        if (isEnable) {
            getDevicePolicyManager().setAutoTimeRequired(getCdmDeviceAdmin(), true);
        }
        else {
            getDevicePolicyManager().setAutoTimeRequired(getCdmDeviceAdmin(), false);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void configureCOSUProfile(Operation operation) throws AndroidAgentException {
        int releaseTime;
        int freezeTime;
        try {
            JSONObject payload = new JSONObject(operation.getPayLoad().toString());
            releaseTime = Integer.
                    parseInt(payload.getString(Constants.COSUProfilePolicy.deviceReleaseTime));
            freezeTime = Integer.
                    parseInt(payload.getString(Constants.COSUProfilePolicy.deviceFreezeTime));

            Preference.
                    putInt(getContext(), Constants.PreferenceCOSUProfile.FREEZE_TIME, freezeTime);
            Preference.
                    putInt(getContext(), Constants.PreferenceCOSUProfile.RELEASE_TIME, releaseTime);

            if(!Preference.getBoolean(getContext(),Constants.PreferenceCOSUProfile.ENABLE_LOCKDOWN)) {
                Preference.putBoolean(getContext(), Constants.PreferenceCOSUProfile.ENABLE_LOCKDOWN, true);
                KioskAlarmReceiver kioskAlarmReceiver = new KioskAlarmReceiver();
                kioskAlarmReceiver.startAlarm(getContext());
            }

            operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
            getResultBuilder().build(operation);

        } catch (JSONException e) {
            operation.setStatus(getContextResources().getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing PROFILE payload.");
            getResultBuilder().build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
    }

    @Override
    public ComplianceFeature checkWorkProfilePolicy(Operation operation, ComplianceFeature policy) throws AndroidAgentException {
        policy.setCompliance(true);
        return policy;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public ComplianceFeature checkRuntimePermissionPolicy(Operation operation, ComplianceFeature policy) throws AndroidAgentException {
        int currentPermissionType;
        int policyPermissionType;
        try {
            JSONObject runtimePermissionData = new JSONObject(operation.getPayLoad().toString());
            if (!runtimePermissionData.isNull("defaultType")) {
                policyPermissionType = Integer.parseInt(runtimePermissionData.get("defaultType").toString());
                currentPermissionType = getDevicePolicyManager().getPermissionPolicy(getCdmDeviceAdmin());
                if(currentPermissionType != policyPermissionType){
                    policy.setCompliance(false);
                    policy.setMessage(getContextResources().getString(R.string.error_runtime_permission_policy));
                    return policy;
                }
            }
        } catch (JSONException e) {
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
        policy.setCompliance(true);
        return policy;
    }

}
