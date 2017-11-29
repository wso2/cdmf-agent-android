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

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.iot.agent.AlertActivity;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.beans.AppRestriction;
import org.wso2.iot.agent.beans.ComplianceFeature;
import org.wso2.iot.agent.beans.DeviceAppInfo;
import org.wso2.iot.agent.beans.Notification;
import org.wso2.iot.agent.beans.Operation;
import org.wso2.iot.agent.services.AppLockService;
import org.wso2.iot.agent.services.NotificationService;
import org.wso2.iot.agent.services.ResultPayload;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.FileUploadCancelReceiver;
import org.wso2.iot.agent.utils.FileUploadReceiver;
import org.wso2.iot.agent.utils.Preference;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class OperationManagerBYOD extends OperationManager {

    private static final String TAG = OperationManagerBYOD.class.getSimpleName();
    private Context context=super.getContext();
    private Resources resources = context.getResources();
    private ResultPayload resultBuilder=super.getResultBuilder();
    private NotificationService notificationService;
    private static final String STATUS = "status";

    public OperationManagerBYOD(Context context){
        super(context);
        notificationService = super.getNotificationService();
    }

    @Override
    public void onReceiveAPIResult(Map<String, String> result, int requestCode) {
        super.onReceiveAPIResult(result, requestCode);
    }

    @Override
    public void uploadFile(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_progress));
        try {
            JSONObject inputData =
                    new JSONObject(operation.getPayLoad().toString());
            final String fileURL = inputData.getString(Constants.FileTransfer.FILE_LOCATION);
            File selectedFile = new File(fileURL);
            if (selectedFile.exists()) {
                Context context = getContext();
                Intent uploadIntent = new Intent(context, FileUploadReceiver.class);
                uploadIntent.putExtra(getContextResources().getString(R.string.intent_extra_operation_object), operation);

                PendingIntent requestPermission = PendingIntent.getBroadcast(context, 0, uploadIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT);

                Intent cancelIntent = new Intent(context, FileUploadCancelReceiver.class);
                cancelIntent.putExtra(getContextResources().getString(R.string.intent_extra_operation_object), operation);

                PendingIntent cancel = PendingIntent.getBroadcast(context, 0, cancelIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT);

                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context);
                mBuilder
                        .setSmallIcon(android.R.drawable.ic_menu_upload)
                        .setContentTitle(selectedFile.getName() + getContextResources().getString(R.
                                string.NOTIFICATION_TITLE))
                        .setTicker(getContextResources().getString(R.
                                string.NOTIFICATION_TICKER))
                        .setAutoCancel(true)
                        .addAction(android.R.drawable.ic_menu_upload, getContextResources().getString(R.
                                string.NOTIFICATION_ALLOW), requestPermission)
                        .addAction(android.R.drawable.ic_menu_close_clear_cancel, getContextResources().
                                getString(R.string.NOTIFICATION_CANCEL), cancel);

                NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                manager.notify(operation.getId(), mBuilder.build());
            } else {
                operation.setStatus(getContextResources().getString(R.string.
                        operation_value_error));
                operation.setOperationResponse("Requested file does not exists in device.");
                setResponse(operation);
            }
        } catch (JSONException e) {
            operation.setStatus(getContextResources().getString(R.string.
                    operation_value_error));
            operation.setOperationResponse("Error in operation payload");
            setResponse(operation);
        }
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
                    operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
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

    @Override
    public void enterpriseWipe(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
        getResultBuilder().build(operation);
    }

    @Override
    public void blacklistApps(Operation operation) throws AndroidAgentException {
        ArrayList<DeviceAppInfo> apps = new ArrayList<>(getAppList().getInstalledApps().values());
        JSONArray appList = new JSONArray();
        JSONArray blacklistApps = new JSONArray();
        String identity;
        try {
            JSONObject resultApp = new JSONObject(operation.getPayLoad().toString());
            if (!resultApp.isNull(getContextResources().getString(R.string.app_identifier))) {
                blacklistApps = resultApp.getJSONArray(getContextResources().getString(R.string.app_identifier));
            }

        } catch (JSONException e) {
            operation.setStatus(getContextResources().getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing APP_BLACKLIST payload.");
            getResultBuilder().build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
        for (int i = 0; i < blacklistApps.length(); i++) {
            try {
                identity = blacklistApps.getString(i);
                for (DeviceAppInfo app : apps) {
                    JSONObject result = new JSONObject();

                    result.put(getContextResources().getString(R.string.intent_extra_name), app.getAppname());
                    result.put(getContextResources().getString(R.string.intent_extra_package),
                            app.getPackagename());
                    if (identity.trim().equals(app.getPackagename())) {
                        result.put(getContextResources().getString(R.string.intent_extra_not_violated), false);
                        result.put(getContextResources().getString(R.string.intent_extra_package),
                                app.getPackagename());
                    } else {
                        result.put(getContextResources().getString(R.string.intent_extra_not_violated), true);
                    }
                    appList.put(result);
                }
            } catch (JSONException e) {
                operation.setStatus(getContextResources().getString(R.string.operation_value_error));
                operation.setOperationResponse("Error in parsing APP_BLACKLIST payload.");
                getResultBuilder().build(operation);
                throw new AndroidAgentException("Invalid JSON format.", e);
            }
        }
        operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
        operation.setPayLoad(appList.toString());
        getResultBuilder().build(operation);

        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Marked blacklist app");
        }
    }

    @Override
    public void disenrollDevice(Operation operation) {
        boolean status = operation.isEnabled();
        if (status) {
            CommonUtils.disableAdmin(getContext());
        }
    }

    @Override
    public void hideApp(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
        operation.setOperationResponse("Operation not supported.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Operation not supported.");
    }

    @Override
    public void unhideApp(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
        operation.setOperationResponse("Operation not supported.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Operation not supported.");
    }

    @Override
    public void blockUninstallByPackageName(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
        operation.setOperationResponse("Operation not supported.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Operation not supported.");
    }

    @Override
    public void setProfileName(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
        operation.setOperationResponse("Operation not supported.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Operation not supported.");
    }

    @Override
    public void handleOwnersRestriction(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
        operation.setOperationResponse("Operation not supported.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Operation not supported.");
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
            operation.setStatus(getContextResources().getString(R.string.operation_value_error));
            operation.setOperationResponse("System service not installed/activated.");
            getResultBuilder().build(operation);
        }
    }

    @Override
    public void restrictAccessToApplications(Operation operation) throws AndroidAgentException {
        AppRestriction appRestriction = CommonUtils.getAppRestrictionTypeAndList(operation, getResultBuilder(), getContextResources());
        String ownershipType = Preference.getString(getContext(), Constants.DEVICE_TYPE);

        if (Constants.AppRestriction.WHITE_LIST.equals(appRestriction.getRestrictionType())) {
            //Persisting white-listed app list.
            JSONArray whiteListApps = new JSONArray();
            ArrayList appList = (ArrayList) appRestriction.getRestrictedList();
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
            if (Constants.OWNERSHIP_COPE.equals(ownershipType)) {
                //Removing existing non-white-listed apps.
                List<String> installedAppPackages = CommonUtils.getInstalledAppPackages(getContext());
                List<String> toBeHideApps = new ArrayList<>(installedAppPackages);
                toBeHideApps.removeAll(appList);
                for (String packageName : toBeHideApps) {
                    CommonUtils.callSystemApp(getContext(), operation.getCode(), "false", packageName);
                }
            }
            Preference.putString(getContext(),
                    Constants.AppRestriction.WHITE_LIST_APPS, whiteListApps.toString());

        } else if (Constants.AppRestriction.BLACK_LIST.equals(appRestriction.getRestrictionType())) {
            if (Constants.OWNERSHIP_BYOD.equals(ownershipType)) {
                Intent restrictionIntent = new Intent(getContext(), AppLockService.class);
                restrictionIntent.setAction(Constants.APP_LOCK_SERVICE);

                restrictionIntent.putStringArrayListExtra(Constants.AppRestriction.APP_LIST, (ArrayList) appRestriction.getRestrictedList());

                PendingIntent pendingIntent = PendingIntent.getService(getContext(), 0, restrictionIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                AlarmManager alarmManager = (AlarmManager)getContext().getSystemService(Context.ALARM_SERVICE);
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(System.currentTimeMillis());
                calendar.add(Calendar.SECOND, 1); // First time
                long frequency= 1 * 1000; // In ms
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), frequency, pendingIntent);

                getContext().startService(restrictionIntent);
            } else if (Constants.OWNERSHIP_COPE.equals(ownershipType)) {
                for (String packageName : appRestriction.getRestrictedList()) {
                    CommonUtils.callSystemApp(getContext(), operation.getCode(), "false", packageName);
                }
            }
        }
        operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
        getResultBuilder().build(operation);
    }

    @Override
    public void setPolicyBundle(Operation operation) throws AndroidAgentException {
        getResultBuilder().build(operation);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void setRuntimePermissionPolicy(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
        operation.setOperationResponse("Operation not supported.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Operation not supported.");
    }

    @Override
    public void setStatusBarDisabled(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
        operation.setOperationResponse("Operation not supported.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Operation not supported.");
    }

    @Override
    public void setScreenCaptureDisabled(Operation operation) throws AndroidAgentException {
        operation.setStatus(getContextResources().getString(R.string.operation_value_error));
        operation.setOperationResponse("Operation not supported.");
        getResultBuilder().build(operation);
        Log.d(TAG, "Operation not supported.");
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
        policy.setCompliance(true);
        return policy;
    }

    @Override
    public ComplianceFeature checkRuntimePermissionPolicy(Operation operation, ComplianceFeature policy) throws AndroidAgentException {
        policy.setCompliance(true);
        return policy;
    }

    @Override
    public void ringDevice(org.wso2.iot.agent.beans.Operation operation) {
        operation.setStatus(resources.getString(R.string.operation_value_completed));
        resultBuilder.build(operation);
        Intent intent = new Intent(context, AlertActivity.class);
        intent.putExtra(resources.getString(R.string.intent_extra_type), resources.getString(R.string.intent_extra_ring));
        intent.putExtra(resources.getString(R.string.intent_extra_message_text), resources.getString(R.string.intent_extra_stop_ringing));
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Ringing is activated on the device");
        }
    }
}
