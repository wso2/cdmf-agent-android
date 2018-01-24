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

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.activities.AuthenticationActivity;
import org.wso2.iot.agent.activities.ServerConfigsActivity;
import org.wso2.iot.agent.api.ApplicationManager;
import org.wso2.iot.agent.api.DeviceInfo;
import org.wso2.iot.agent.beans.AppInstallRequest;
import org.wso2.iot.agent.beans.Operation;
import org.wso2.iot.agent.beans.ServerConfig;
import org.wso2.iot.agent.proxy.interfaces.APIResultCallBack;
import org.wso2.iot.agent.proxy.utils.Constants.HTTP_METHODS;
import org.wso2.iot.agent.services.operation.OperationManagerDeviceOwner;
import org.wso2.iot.agent.services.operation.OperationProcessor;
import org.wso2.iot.agent.utils.AppInstallRequestUtil;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * This class handles all the functionality related to coordinating the retrieval
 * and processing of messages from the server.
 */
public class MessageProcessor implements APIResultCallBack {

    private static final String TAG = MessageProcessor.class.getSimpleName();

    private static final String ERROR_STATE = "ERROR";
    private static volatile long invokedTimestamp = 0;
    private static List<Operation> replyPayload;
    private Context context;
    private String deviceId;
    private OperationProcessor operationProcessor;
    private ObjectMapper mapper;
    private boolean isWipeTriggered = false;
    private boolean isRebootTriggered = false;
    private boolean isUpgradeTriggered = false;
    private boolean isShellCommandTriggered = false;
    private boolean isEnterpriseWipeTriggered = false;
    private String shellCommand = null;

    /**
     * Local notification message handler.
     *
     * @param context Context of the application.
     */
    public MessageProcessor(Context context) {
        this.context = context;

        deviceId = Preference.getString(context, Constants.PreferenceFlag.DEVICE_ID_PREFERENCE_KEY);
        operationProcessor = new OperationProcessor(context.getApplicationContext());
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        if (deviceId == null) {
            DeviceInfo deviceInfo = new DeviceInfo(context.getApplicationContext());
            deviceId = deviceInfo.getDeviceId();
            Preference.putString(context, Constants.PreferenceFlag.DEVICE_ID_PREFERENCE_KEY, deviceId);
        }
        invokedTimestamp = Calendar.getInstance().getTimeInMillis();
    }

    static long getInvokedTimeStamp() {
        return invokedTimestamp;
    }

    /**
     * This method executes the set of pending operations which is received from the
     * backend server.
     *
     * @param response Response received from the server that needs to be processed
     *                 and applied to the device.
     */
    private void performOperation(String response) {
        List<Operation> operations = new ArrayList<>();
        try {
            if (response != null) {
                operations = mapper.readValue(
                        response,
                        mapper.getTypeFactory().constructCollectionType(List.class,
                                Operation.class));
            }
            // check whether if there are any dismissed notifications to be sent
            operationProcessor.checkPreviousNotifications();
        } catch (JsonProcessingException e) {
            Log.e(TAG, "Issue in json parsing", e);
        } catch (IOException e) {
            Log.e(TAG, "Issue in stream parsing", e);
        } catch (AndroidAgentException e) {
            Log.e(TAG, "Error occurred while checking previous notification", e);
        }

        if (!(operations.isEmpty() || (operations.size() == 1 && Constants.Operation.POLICY_MONITOR.equals(operations.get(0).getCode())))) {
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Restarting to send quick update of received pending operations.");
            }
            LocalNotification.startPolling(context);
        }

        for (Operation op : operations) {
            try {
                operationProcessor.doTask(op);
            } catch (AndroidAgentException e) {
                Log.e(TAG, "Failed to perform operation", e);
            }
        }
        replyPayload = operationProcessor.getResultPayload();
    }


    /**
     * Call the message retrieval end point of the server to get messages pending.
     */
    public void getMessages() throws AndroidAgentException {
        String ipSaved = Constants.DEFAULT_HOST;
        String prefIP = Preference.getString(context.getApplicationContext(), Constants.PreferenceFlag.IP);
        if (prefIP != null) {
            ipSaved = prefIP;
        }
        ServerConfig utils = new ServerConfig();
        utils.setServerIP(ipSaved);
        String url = utils.getAPIServerURL(context) + Constants.DEVICES_ENDPOINT + deviceId + Constants.NOTIFICATION_ENDPOINT;

        Log.i(TAG, "Get pending operations from: " + url);

        String requestParams;
        ObjectMapper mapper = new ObjectMapper();
        try {
            String rebootStatus = Preference.getString(context, context.getResources()
                    .getString(R.string.shared_pref_reboot_status));
            if (rebootStatus != null) {
                if (replyPayload == null) {
                    replyPayload = new ArrayList<>();
                }
                int lastRebootOperationId = Preference.getInt(context, context.getResources()
                        .getString(R.string.shared_pref_reboot_op_id));
                for (Operation operation : replyPayload) {
                    if (lastRebootOperationId == operation.getId()) {
                        replyPayload.remove(operation);
                        break;
                    }
                }

                Operation rebootOperation = new Operation();
                rebootOperation.setId(lastRebootOperationId);
                rebootOperation.setCode(Constants.Operation.REBOOT);
                rebootOperation.setOperationResponse(Preference.getString(context,
                        context.getResources().getString(R.string.shared_pref_reboot_result)));
                rebootOperation.setStatus(rebootStatus);
                replyPayload.add(rebootOperation);

                Preference.removePreference(context, context.getResources()
                        .getString(R.string.shared_pref_reboot_status));
                Preference.removePreference(context, context.getResources()
                        .getString(R.string.shared_pref_reboot_result));
                Preference.removePreference(context, context.getResources()
                        .getString(R.string.shared_pref_reboot_op_id));
            }
            if (replyPayload != null) {
                for (Operation operation : replyPayload) {
                    if (operation.getCode().equals(Constants.Operation.WIPE_DATA) && !operation.getStatus().
                            equals(ERROR_STATE)) {
                        isWipeTriggered = true;
                    } else if (operation.getCode().equals(Constants.Operation.ENTERPRISE_WIPE) && !operation.getStatus().
                            equals(ERROR_STATE)) {
                        isEnterpriseWipeTriggered = true;
                    } else if (operation.getCode().equals(Constants.Operation.REBOOT) && operation.getStatus().
                            equals(context.getResources().getString(R.string.operation_value_pending))) {
                        operation.setStatus(context.getResources().getString(R.string.operation_value_progress));
                        isRebootTriggered = true;
                    } else if (operation.getCode().equals(Constants.Operation.UPGRADE_FIRMWARE) && !operation.getStatus().
                            equals(ERROR_STATE)) {
                        isUpgradeTriggered = true;
                        Preference.putInt(context, "firmwareOperationId", operation.getId());
                    } else if (operation.getCode().equals(Constants.Operation.EXECUTE_SHELL_COMMAND) && !operation.getStatus().
                            equals(ERROR_STATE)) {
                        isShellCommandTriggered = true;
                        try {
                            JSONObject payload = new JSONObject(operation.getPayLoad().toString());
                            shellCommand = (String) payload.get(context.getResources().getString(R.string.shared_pref_command));
                        } catch (JSONException e) {
                            throw new AndroidAgentException("Invalid JSON format.", e);
                        }
                    }
                }
            }
            int firmwareOperationId = Preference.getInt(context, context.getResources().getString(
                    R.string.firmware_upgrade_response_id));
            if (firmwareOperationId != 0) {
                Operation firmwareOperation = new Operation();
                firmwareOperation.setId(firmwareOperationId);
                firmwareOperation.setCode(Constants.Operation.UPGRADE_FIRMWARE);
                firmwareOperation.setStatus(Preference.getString(context, context.getResources().getString(
                        R.string.firmware_upgrade_response_status)));
                boolean isRetryPending = Preference.getBoolean(context, context.getResources().
                        getString(R.string.firmware_upgrade_retry_pending));
                if (isRetryPending) {
                    isUpgradeTriggered = true;
                    int retryCount = Preference.getInt(context, context.getResources().
                            getString(R.string.firmware_upgrade_retries));
                    firmwareOperation.setOperationResponse("Attempt " + retryCount +
                            " has failed due to: " + Preference.getString(context, context.getResources().getString(
                            R.string.firmware_upgrade_response_message)));
                } else {
                    firmwareOperation.setOperationResponse(Preference.getString(context, context.getResources().getString(
                            R.string.firmware_upgrade_response_message)));
                }
                if (replyPayload != null) {
                    replyPayload.add(firmwareOperation);
                } else {
                    replyPayload = new ArrayList<>();
                    replyPayload.add(firmwareOperation);
                }
                Preference.putInt(context, context.getResources().getString(
                        R.string.firmware_upgrade_response_id), 0);
                Preference.putString(context, context.getResources().getString(
                        R.string.firmware_upgrade_response_status), context.getResources().getString(
                        R.string.operation_value_error));
                Preference.putString(context, context.getResources().getString(
                        R.string.firmware_upgrade_response_message), null);
            }


            int applicationUninstallOperationId = Preference.getInt(context, context.getResources().getString(
                    R.string.app_uninstall_id));
            String applicationUninstallOperationCode = Preference.getString(context, context.getResources().getString(
                    R.string.app_uninstall_code));
            String applicationUninstallOperationStatus = Preference.getString(context, context.getResources().getString(
                    R.string.app_uninstall_status));
            String applicationUninstallOperationMessage = Preference.getString(context, context.getResources().getString(
                    R.string.app_uninstall_failed_message));

            if (applicationUninstallOperationStatus != null && applicationUninstallOperationId != 0 && applicationUninstallOperationCode != null) {
                Operation applicationOperation = new Operation();
                ApplicationManager appMgt = new ApplicationManager(context);
                applicationOperation.setId(applicationUninstallOperationId);
                applicationOperation.setCode(applicationUninstallOperationCode);
                applicationOperation = appMgt.getApplicationInstallationStatus(
                        applicationOperation, applicationUninstallOperationStatus, applicationUninstallOperationMessage);
                if (replyPayload == null) {
                    replyPayload = new ArrayList<>();
                }
                replyPayload.add(applicationOperation);

                Preference.putString(context, context.getResources().getString(
                        R.string.app_install_status), null);
                Preference.putString(context, context.getResources().getString(
                        R.string.app_install_failed_message), null);
            }



            int applicationOperationId = Preference.getInt(context, context.getResources().getString(
                    R.string.app_install_id));
            String applicationOperationCode = Preference.getString(context, context.getResources().getString(
                    R.string.app_install_code));
            String applicationOperationStatus = Preference.getString(context, context.getResources().getString(
                    R.string.app_install_status));
            String applicationOperationMessage = Preference.getString(context, context.getResources().getString(
                    R.string.app_install_failed_message));
            if (applicationOperationStatus != null && applicationOperationId != 0 && applicationOperationCode != null) {
                Operation applicationOperation = new Operation();
                ApplicationManager appMgt = new ApplicationManager(context);
                applicationOperation.setId(applicationOperationId);
                applicationOperation.setCode(applicationOperationCode);
                applicationOperation = appMgt.getApplicationInstallationStatus(
                        applicationOperation, applicationOperationStatus, applicationOperationMessage);
                if (replyPayload == null) {
                    replyPayload = new ArrayList<>();
                }
                replyPayload.add(applicationOperation);
                Preference.putString(context, context.getResources().getString(
                        R.string.app_install_status), null);
                Preference.putString(context, context.getResources().getString(
                        R.string.app_install_failed_message), null);
                if (context.getResources().getString(R.string.operation_value_error).equals(applicationOperation.getStatus()) ||
                        context.getResources().getString(R.string.operation_value_completed).equals(applicationOperation.getStatus())) {
                    Preference.putInt(context, context.getResources().getString(
                            R.string.app_install_id), 0);
                    Preference.putString(context, context.getResources().getString(
                            R.string.app_install_code), null);
                    startPendingInstallation();
                }
            } else {
                startPendingInstallation();
            }

            if (Preference.hasPreferenceKey(context, Constants.Operation.LOGCAT)) {
                if (Preference.hasPreferenceKey(context, Constants.Operation.LOGCAT)) {
                    Gson operationGson = new Gson();
                    Operation logcatOperation = operationGson.fromJson(Preference
                            .getString(context, Constants.Operation.LOGCAT), Operation.class);
                    if (replyPayload == null) {
                        replyPayload = new ArrayList<>();
                    }
                    replyPayload.add(logcatOperation);
                    Preference.removePreference(context, Constants.Operation.LOGCAT);
                }
            }

            Resources resources = context.getResources();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            int uploadId = prefs.getInt(resources.getString(R.string.FILE_UPLOAD_ID), -1);
            if (uploadId > 0) {
                Operation fileUpload = new Operation();
                fileUpload.setCode(Constants.Operation.FILE_UPLOAD);
                fileUpload.setId(uploadId);
                fileUpload.setStatus(prefs.getString(resources.getString(R.string.FILE_UPLOAD_STATUS),
                        resources.getString(R.string.operation_value_error)));
                fileUpload.setOperationResponse(prefs.getString(resources.getString(R.string.
                        FILE_UPLOAD_RESPONSE), resources.getString(R.string.operation_value_error)));
                fileUpload.setEnabled(true);
                replyPayload.add(fileUpload);
                SharedPreferences.Editor editor = prefs.edit();
                editor.remove(resources.getString(R.string.FILE_UPLOAD_ID));
                editor.apply();
            }

            int downloadId = prefs.getInt(resources.getString(R.string.FILE_DOWNLOAD_ID), -1);
            if (downloadId > 0) {
                Operation fileDownload = new Operation();
                fileDownload.setCode(Constants.Operation.FILE_DOWNLOAD);
                fileDownload.setId(downloadId);
                fileDownload.setStatus(prefs.getString(resources.getString(R.string.FILE_DOWNLOAD_STATUS),
                        resources.getString(R.string.operation_value_error)));
                fileDownload.setOperationResponse(prefs.getString(resources.getString(R.string.
                        FILE_DOWNLOAD_RESPONSE), resources.getString(R.string.operation_value_error)));
                fileDownload.setEnabled(true);
                replyPayload.add(fileDownload);
                SharedPreferences
                        .Editor editor = prefs.edit();
                editor.remove(resources.getString(R.string.FILE_DOWNLOAD_ID));
                editor.apply();
            }

            requestParams = mapper.writeValueAsString(replyPayload);
        } catch (JsonMappingException e) {
            throw new AndroidAgentException("Issue in json mapping", e);
        } catch (JsonGenerationException e) {
            throw new AndroidAgentException("Issue in json generation", e);
        } catch (IOException e) {
            throw new AndroidAgentException("Issue in parsing stream", e);
        }
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Reply Payload: " + requestParams);
        }

        if (requestParams != null && requestParams.trim().equals(context.getResources().getString(
                R.string.operation_value_null))) {
            requestParams = null;
        }

        if (ipSaved != null && !ipSaved.isEmpty()) {
            CommonUtils.callSecuredAPI(context, url,
                    HTTP_METHODS.PUT, requestParams, MessageProcessor.this,
                    Constants.NOTIFICATION_REQUEST_CODE
            );
        } else {
            Log.e(TAG, "There is no valid IP to contact the server");
        }
    }

    private void startPendingInstallation() {
        AppInstallRequest appInstallRequest = AppInstallRequestUtil.getPending(context);
        if (appInstallRequest != null) {
            ApplicationManager applicationManager = new ApplicationManager(context.getApplicationContext());
            Operation applicationOperation = new Operation();
            applicationOperation.setId(appInstallRequest.getApplicationOperationId());
            applicationOperation.setCode(appInstallRequest.getApplicationOperationCode());
            Log.d(TAG, "Try to start app installation from queue.");
            applicationManager.installApp(appInstallRequest.getAppUrl(), null, applicationOperation);
        }
    }

    @SuppressWarnings("unused")
    @Override
    public void onReceiveAPIResult(Map<String, String> result, int requestCode) {
        String responseStatus;
        String response;
        if (requestCode == Constants.NOTIFICATION_REQUEST_CODE) {
            Preference.putLong(context, Constants.PreferenceFlag.LAST_SERVER_CALL, CommonUtils.currentDate().getTime());
            Intent intent = new Intent();
            intent.setAction(Constants.SYNC_BROADCAST_ACTION);
            context.sendBroadcast(intent);

            if (isWipeTriggered) {
                if (Constants.SYSTEM_APP_ENABLED) {
                    CommonUtils.callSystemApp(context, Constants.Operation.WIPE_DATA, null, null);
                } else {
                    DevicePolicyManager devicePolicyManager = operationProcessor.getOperationManager().getDevicePolicyManager();
                    if (devicePolicyManager != null) {
                        devicePolicyManager.wipeData(0);
                    } else {
                        Log.e(TAG, "Unable to perform operation as device policy manager is null");
                    }
                }
            }

            if (isEnterpriseWipeTriggered) {
                CommonUtils.disableAdmin(context);

                Intent intentEnterpriseWipe = new Intent(context, ServerConfigsActivity.class);
                intentEnterpriseWipe.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intentEnterpriseWipe.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intentEnterpriseWipe);
                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, "Started enterprise wipe");
                }
            }

            if (isRebootTriggered) {
                if (Constants.SYSTEM_APP_ENABLED) {
                    CommonUtils.callSystemApp(context, Constants.Operation.REBOOT, null, null);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                        && operationProcessor.getOperationManager() instanceof OperationManagerDeviceOwner) {
                    DevicePolicyManager devicePolicyManager = (DevicePolicyManager)
                            context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                    if (devicePolicyManager != null) {
                        devicePolicyManager.reboot(AgentDeviceAdminReceiver.getComponentName(context));
                    }
                } else {
                    try {
                        Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot"});
                        proc.waitFor();
                    } catch (Exception ex) {
                        String msg = "Could not reboot.";
                        Log.e(TAG, msg, ex);
                        Preference.putString(context, context.getResources().getString(R.string.shared_pref_reboot_status),
                                context.getResources().getString(R.string.operation_value_error));
                        Preference.putString(context, context.getResources().getString(R.string.shared_pref_reboot_result),
                                msg + " " + ex.getMessage());
                    }
                }
            }

            if (isUpgradeTriggered) {
                String schedule = Preference.getString(context, context.getResources().getString(R.string.pref_key_schedule));
                CommonUtils.callSystemApp(context, Constants.Operation.UPGRADE_FIRMWARE, schedule, null);
            }

            if (isShellCommandTriggered && shellCommand != null) {
                CommonUtils.callSystemApp(context, Constants.Operation.EXECUTE_SHELL_COMMAND, shellCommand, null);
            }

            if (result != null) {
                responseStatus = result.get(Constants.STATUS_KEY);
                response = result.get(Constants.RESPONSE);
                if (Constants.Status.SUCCESSFUL.equals(responseStatus) ||
                        Constants.Status.CREATED.equals(responseStatus)) {
                    if (response != null && !response.isEmpty()) {
                        if (Constants.DEBUG_MODE_ENABLED) {
                            Log.d(TAG, "Pending Operations List: " + response);
                        }
                        if (Constants.DEFAULT_OWNERSHIP.equals(Constants.OWNERSHIP_COSU)) {
                            if (!Preference.
                                    getBoolean(context, Constants.PreferenceFlag.DEVICE_INITIALIZED)) {
                                Preference.
                                        putBoolean(context,
                                                Constants.PreferenceFlag.DEVICE_INITIALIZED, true);
                            }
                        }
                        performOperation(response);
                    }
                } else if (Constants.Status.AUTHENTICATION_FAILED.equals(responseStatus) &&
                        org.wso2.iot.agent.proxy.utils.Constants.REFRESH_TOKEN_EXPIRED.equals(response)) {
                    Log.d(TAG, "Requesting credentials to obtain new token pair.");
                    LocalNotification.stopPolling(context);
                    Preference.putBoolean(context, Constants.TOKEN_EXPIRED, true);
                    CommonUtils.displayNotification(context,
                            R.drawable.ic_error_outline_white_24dp,
                            context.getResources().getString(R.string.title_need_to_sign_in),
                            context.getResources().getString(R.string.msg_need_to_sign_in),
                            AuthenticationActivity.class,
                            Constants.TOKEN_EXPIRED,
                            Constants.SIGN_IN_NOTIFICATION_ID);
                }
            }
        }
    }

}
