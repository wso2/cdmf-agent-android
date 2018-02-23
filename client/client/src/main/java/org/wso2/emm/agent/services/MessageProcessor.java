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
package org.wso2.emm.agent.services;

import android.content.Context;
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
import org.wso2.emm.agent.AndroidAgentException;
import org.wso2.emm.agent.R;
import org.wso2.emm.agent.api.ApplicationManager;
import org.wso2.emm.agent.api.DeviceInfo;
import org.wso2.emm.agent.beans.AppInstallRequest;
import org.wso2.emm.agent.beans.AppUninstallRequest;
import org.wso2.emm.agent.beans.Operation;
import org.wso2.emm.agent.beans.ServerConfig;
import org.wso2.emm.agent.proxy.interfaces.APIResultCallBack;
import org.wso2.emm.agent.proxy.utils.Constants.HTTP_METHODS;
import org.wso2.emm.agent.services.operation.OperationProcessor;
import org.wso2.emm.agent.utils.AppManagementRequestUtil;
import org.wso2.emm.agent.utils.CommonUtils;
import org.wso2.emm.agent.utils.Constants;
import org.wso2.emm.agent.utils.Preference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * This class handles all the functionalities related to coordinating the retrieval
 * and processing of messages from the server.
 */
public class MessageProcessor implements APIResultCallBack {

	private String TAG = MessageProcessor.class.getSimpleName();
	private Context context;
	private String deviceId;
	private static final String DEVICE_ID_PREFERENCE_KEY = "deviceId";
	private static List<org.wso2.emm.agent.beans.Operation> replyPayload;
	private OperationProcessor operationProcessor;
	private ObjectMapper mapper;
	private boolean isWipeTriggered = false;
	private boolean isRebootTriggered = false;
	private boolean isUpgradeTriggered = false;
	private boolean isShellCommandTriggered = false;
	private static final String ERROR_STATE = "ERROR";
	private static final String COMPLETE_STATE = "COMPLETED";
	private String shellCommand = null;
	private List<Integer> queuedFirmwareOperationlist = new ArrayList<>();

	/**
	 * Local notification message handler.
	 *
	 * @param context Context of the application.
	 */
	public MessageProcessor(Context context) {
		this.context = context;

		deviceId = Preference.getString(context, DEVICE_ID_PREFERENCE_KEY);
		operationProcessor = new OperationProcessor(context.getApplicationContext());
		mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

		if (deviceId == null) {
			DeviceInfo deviceInfo = new DeviceInfo(context.getApplicationContext());
			deviceId = deviceInfo.getDeviceId();
			Preference.putString(context, DEVICE_ID_PREFERENCE_KEY, deviceId);
		}
	}

	/**
	 * This method executes the set of pending operations which is recieved from the
	 * backend server.
	 *
	 * @param response Response received from the server that needs to be processed
	 *                 and applied to the device.
	 */
	public void performOperation(String response) {

		List<org.wso2.emm.agent.beans.Operation> operations = new ArrayList<>();

		try {
			if (response != null) {
				operations = mapper.readValue(
						response,
						mapper.getTypeFactory().constructCollectionType(List.class,
								org.wso2.emm.agent.beans.Operation.class));
			}


		// check whether if there are any dismissed notifications to be sent
		operationProcessor.checkPreviousNotifications();

		} catch (JsonProcessingException e) {
			Log.e(TAG,  "Issue in json parsing", e);
		} catch (IOException e) {
			Log.e(TAG, "Issue in stream parsing", e);
		} catch (AndroidAgentException e) {
			Log.e(TAG, "Error occurred while checking previous notification", e);
		}

		for (org.wso2.emm.agent.beans.Operation op : operations) {
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
		int appInstallOperationId;
		int appUninstallOperationId;
		int firmwareUpgradeOperationId = 0;
		try {
			if (Preference.getBoolean(context, context.getResources().getString(R.string.shared_pref_reboot_done))) {
				if (replyPayload == null) {
					replyPayload = new ArrayList<>();
				}
				int lastRebootOperationId = Preference.getInt(context, context.getResources().getString(R.string.shared_pref_reboot_op_id));
				for (org.wso2.emm.agent.beans.Operation operation : replyPayload) {
					if (lastRebootOperationId == operation.getId()) {
						replyPayload.remove(operation);
						break;
					}
				}

				JSONObject result = new JSONObject();
				result.put(context.getResources().getString(R.string.operation_status), Constants.SYSTEM_APP_ENABLED);

				org.wso2.emm.agent.beans.Operation rebootOperation = new org.wso2.emm.agent.beans.Operation();
				rebootOperation.setId(lastRebootOperationId);
				rebootOperation.setCode(Constants.Operation.REBOOT);
				rebootOperation.setPayLoad(result.toString());
				rebootOperation.setStatus(context.getResources().getString(R.string.operation_value_completed));
				replyPayload.add(rebootOperation);

				Preference.removePreference(context, context.getResources().getString(R.string.shared_pref_reboot_done));
				Preference.removePreference(context, context.getResources().getString(R.string.shared_pref_reboot_op_id));
			}
			if (replyPayload != null) {
				for (org.wso2.emm.agent.beans.Operation operation : replyPayload) {
					if (operation == null) {
						continue;
					}
					if (Constants.Operation.WIPE_DATA.equals(operation.getCode())
							&& !ERROR_STATE.equals(operation.getStatus())) {
						isWipeTriggered = true;
					} else if (Constants.Operation.REBOOT.equals(operation.getCode()) && context.getResources()
							.getString(R.string.operation_value_pending).equals(operation.getStatus())) {
						operation.setStatus(context.getResources().getString(R.string.operation_value_progress));
						isRebootTriggered = true;
					} else if (Constants.Operation.UPGRADE_FIRMWARE.equals(operation.getCode())) {
                        if ((!COMPLETE_STATE.equals(operation.getStatus()) && !ERROR_STATE.equals(operation.getStatus
                                ()))) {
							//All the IN_PROGRESS state firmware operations should will come into this block
							int opId = Preference.getInt(context, "firmwareOperationId");
							int erroneousId = Preference.getInt(context, "LastErroneousFirmwareOperationId");
							if (Constants.DEBUG_MODE_ENABLED) {
								Log.d(TAG, "operation status at the moment is " + operation.getStatus());
                                Log.d(TAG, "Received Operation Id : " + operation.getId());
                                Log.d(TAG, "Current Operation Id : " + Preference.getInt(context,
                                        "firmwareOperationId"));
                                Log.d(TAG, "Last operation Id with ERROR state : " + Preference.getInt(context,
                                        "LastErroneousFirmwareOperationId"));
							}
                            //Initially when the operation status is In Progress, 'isUpgradeTriggered'
                            // is set to 'true' to call the system app. After initial call, to prevent
                            // calling system app again and again for the same operation Id following
                            // checks are added.
							if(erroneousId!= 0 && operation.getId() == erroneousId){
								isUpgradeTriggered = false;
							} else if (opId == operation.getId()){
								if (Constants.DEBUG_MODE_ENABLED) {
									Log.d(TAG, "isUpgradeTriggered is set to false");
								}
								isUpgradeTriggered = false;
							} else if (opId == 0) {
								Preference.putInt(context, "firmwareOperationId", operation.getId());
								isUpgradeTriggered = true;
							} else if (operation.getId() != 0 && operation.getId() != opId){
								Log.e(TAG, "This is a new Firmware upgrade operation but one operation is still on " +
                                        "going");
								queuedFirmwareOperationlist.add(operation.getId());
							}
                            //Operation Id of the received reply payload is stored
                            firmwareUpgradeOperationId = operation.getId();
						}
					} else if (operation.getCode().equals(Constants.Operation.EXECUTE_SHELL_COMMAND) && !operation
                            .getStatus().equals(ERROR_STATE)) {
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

			if(ERROR_STATE.equals(Preference.getString(context, context.getResources().getString(
					R.string.firmware_upgrade_response_status)))){
                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, "Firmware operation state is ERROR");
                }
				Preference.putInt(context, "LastErroneousFirmwareOperationId", Preference.getInt(context,
						"firmwareOperationId"));
				Preference.putInt(context, "firmwareOperationId", 0);
				isUpgradeTriggered = false;
			}

			if (COMPLETE_STATE.equals(Preference.getString(context, context.getResources().getString(R.string
					.firmware_upgrade_response_status)))) {
                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, "Firmware operation state is SUCCESSFUL");
                }
				Preference.putInt(context, "firmwareOperationId", 0);
				isUpgradeTriggered = false;
			}

			if (!queuedFirmwareOperationlist.isEmpty()){
				for (int queuedOperationId : queuedFirmwareOperationlist) {
					org.wso2.emm.agent.beans.Operation firmwareOperation = new org.wso2.emm.agent.beans.Operation();
					firmwareOperation.setId(queuedOperationId);
					firmwareOperation.setCode(Constants.Operation.UPGRADE_FIRMWARE);
					firmwareOperation.setStatus(ERROR_STATE);
					firmwareOperation.setOperationResponse("There is an already running firmware upgrade operation");
					if (replyPayload != null) {
						replyPayload.add(firmwareOperation);
					} else {
						replyPayload = new ArrayList<>();
						replyPayload.add(firmwareOperation);
					}
				}
				queuedFirmwareOperationlist = new ArrayList<>();
			}

			int firmwareOperationId = Preference.getInt(context, context.getResources().getString(
					R.string.firmware_upgrade_response_id));

			if (firmwareOperationId != 0) {
				//If there's a firmwareResponseId that means the operation is already triggered.
				// Therefore cleaning the operation Id set above
				firmwareUpgradeOperationId = 0;
				org.wso2.emm.agent.beans.Operation firmwareOperation = new org.wso2.emm.agent.beans.Operation();
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
						R.string.firmware_upgrade_response_status), null);
				Preference.putString(context, context.getResources().getString(
						R.string.firmware_upgrade_response_message), null);
			}

			//if there's no new firmware upgrade operation this will prevent calling system app
			if (firmwareUpgradeOperationId == 0){
				isUpgradeTriggered = false;
			}

			appUninstallOperationId = Preference.getInt(context, context.getResources()
					.getString(R.string.app_uninstall_id));
			String applicationUninstallOperationCode = Preference.getString(context,
					context.getResources().getString(R.string.app_uninstall_code));
			String applicationUninstallOperationStatus = Preference.getString(context,
					context.getResources().getString(R.string.app_uninstall_status));
			String applicationUninstallOperationMessage = Preference.getString(context,
					context.getResources().getString(
					R.string.app_uninstall_failed_message));
			long uninstallInitiatedAt = Preference.getLong(context,
					Constants.PreferenceFlag.UNINSTALLATION_INITIATED_AT);

			// If uninstallation is started, we might need to ensure that uninstallation
			// is completing within the time defined in APP_UNINSTALL_TIMEOUT constants.
			if (uninstallInitiatedAt != 0 && Calendar.getInstance().getTimeInMillis() -
					uninstallInitiatedAt > Constants.APP_UNINSTALL_TIMEOUT) {
				applicationUninstallOperationStatus = context.getResources()
						.getString(R.string.operation_value_error);
				applicationUninstallOperationMessage = "App uninstallation unresponsive. Hence aborted.";
				Preference.putLong(context, Constants.PreferenceFlag.UNINSTALLATION_INITIATED_AT, 0);
				Log.e(TAG, "Clearing app uninstall request " + appUninstallOperationId +
						" as it is not responsive.");
			}

			if (applicationUninstallOperationStatus != null && appUninstallOperationId != 0
					&& applicationUninstallOperationCode != null) {
				ApplicationManager appMgt = new ApplicationManager(context);
				Operation applicationOperation = new Operation();
				applicationOperation.setId(appUninstallOperationId);
				applicationOperation.setCode(applicationUninstallOperationCode);
				applicationOperation = appMgt.getApplicationStatus(applicationOperation,
						applicationUninstallOperationStatus, applicationUninstallOperationMessage);

				if (replyPayload == null) {
					replyPayload = new ArrayList<>();
				}
				replyPayload.add(applicationOperation);

				Preference.putString(context, context.getResources().getString(
						R.string.app_uninstall_status), null);
				Preference.putString(context, context.getResources().getString(
						R.string.app_uninstall_failed_message), null);

				if (context.getResources().getString(R.string.operation_value_error)
						.equals(applicationOperation.getStatus()) ||
						context.getResources().getString(R.string.operation_value_completed)
								.equals(applicationOperation.getStatus())){
					appUninstallOperationId = 0;
					Preference.putInt(context, context.getResources().getString(
							R.string.app_uninstall_id), 0);
					Preference.putString(context, context.getResources().getString(
							R.string.app_uninstall_code), null);
					Preference.putLong(context,
							Constants.PreferenceFlag.UNINSTALLATION_INITIATED_AT, 0);
				}
			}

			appInstallOperationId = Preference.getInt(context, context.getResources().getString(
					R.string.app_install_id));
			String applicationOperationCode = Preference.getString(context,
					context.getResources().getString(R.string.app_install_code));
			String applicationOperationStatus = Preference.getString(context,
					context.getResources().getString(R.string.app_install_status));
			String applicationOperationMessage = Preference.getString(context,
					context.getResources().getString(R.string.app_install_failed_message));
			String appInstallLastStatus = Preference.getString(context,
					Constants.PreferenceFlag.APP_INSTALLATION_LAST_STATUS);

			if (Constants.AppState.DOWNLOAD_STARTED.equals(appInstallLastStatus)
					|| Constants.AppState.DOWNLOAD_RETRY.equals(appInstallLastStatus)) {
				// If download is started, we might need to ensure that download is completing
				// within the time defined in DOWNLOAD_INITIATED_AT constants.
				long downloadInitiatedAt = Preference.getLong(context,
						Constants.PreferenceFlag.DOWNLOAD_INITIATED_AT);
				if (downloadInitiatedAt != 0 && Calendar.getInstance().getTimeInMillis() -
						downloadInitiatedAt > Constants.APP_DOWNLOAD_TIMEOUT) {
					new ApplicationManager(context).cancelOngoingDownload(); // Cancelling existing downloads if any.
					applicationOperationStatus = Constants.AppState.INSTALL_FAILED;
					applicationOperationMessage = "App download unresponsive. Hence aborted.";
					Preference.putLong(context, Constants.PreferenceFlag.DOWNLOAD_INITIATED_AT, 0);
					Preference.putString(context,
							Constants.PreferenceFlag.APP_INSTALLATION_LAST_STATUS, null);
					Log.e(TAG, "Clearing app download request " + appInstallOperationId +
							" as it is not responsive.");
				} else if (downloadInitiatedAt == 0) {
					// Setting download initiated timestamp as it is not set already.
					Preference.putLong(context, Constants.PreferenceFlag.DOWNLOAD_INITIATED_AT,
							Calendar.getInstance().getTimeInMillis());
				}
			} else if (Constants.AppState.DOWNLOAD_COMPLETED.equals(appInstallLastStatus)) {
				// If download is completed and installation is started, we might need to
				// ensure that download is completing within the time defined in
				// DOWNLOAD_INITIATED_AT constants.
				long installInitiatedAt = Preference.getLong(context,
						Constants.PreferenceFlag.INSTALLATION_INITIATED_AT);
				if (installInitiatedAt != 0 && Calendar.getInstance().getTimeInMillis() -
						installInitiatedAt > Constants.APP_INSTALL_TIMEOUT) {
					new ApplicationManager(context).cancelOngoingDownload(); // Cancelling existing downloads if any.
					applicationOperationStatus = Constants.AppState.INSTALL_FAILED;
					applicationOperationMessage = "App installation unresponsive. Hence aborted.";
					Preference.putLong(context, Constants.PreferenceFlag.INSTALLATION_INITIATED_AT, 0);
					Preference.putString(context,
							Constants.PreferenceFlag.APP_INSTALLATION_LAST_STATUS, null);
					Log.e(TAG, "Clearing previous app installation request " + appInstallOperationId +
							" as it is not responsive.");
				} else if (installInitiatedAt == 0) {
					// Setting installation initiated timestamp as it is not set already.
					Preference.putLong(context, Constants.PreferenceFlag.INSTALLATION_INITIATED_AT,
							Calendar.getInstance().getTimeInMillis());
				}
			}

			if (applicationOperationStatus != null && appInstallOperationId != 0 && applicationOperationCode != null) {
				Operation applicationOperation = new Operation();
				ApplicationManager appMgt = new ApplicationManager(context);
				applicationOperation.setId(appInstallOperationId);
				applicationOperation.setCode(applicationOperationCode);
				applicationOperation = appMgt.getApplicationStatus(
						applicationOperation, applicationOperationStatus, applicationOperationMessage);

				Preference.putString(context, context.getResources().getString(
						R.string.app_install_status), null);
				Preference.putString(context, context.getResources().getString(
						R.string.app_install_failed_message), null);
				if (context.getResources().getString(R.string.operation_value_error).equals(applicationOperation.getStatus()) ||
						context.getResources().getString(R.string.operation_value_completed).equals(applicationOperation.getStatus())){
				    appInstallOperationId = 0;
					Preference.putInt(context, context.getResources().getString(
							R.string.app_install_id), 0);
					Preference.putString(context, context.getResources().getString(
							R.string.app_install_code), null);
					Preference.putString(context,
							Constants.PreferenceFlag.APP_INSTALLATION_LAST_STATUS, null);
				} else {
					// Keep last installation status since app installation is not at finite state.
					Preference.putString(context,
							Constants.PreferenceFlag.APP_INSTALLATION_LAST_STATUS, applicationOperationStatus);
				}
				if (replyPayload == null) {
					replyPayload = new ArrayList<>();
				}
				replyPayload.add(applicationOperation);
			}

			if (Preference.hasPreferenceKey(context, Constants.Operation.LOGCAT)){
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
			requestParams =  mapper.writeValueAsString(replyPayload);
		} catch (JsonMappingException e) {
			throw new AndroidAgentException("Issue in json mapping", e);
		} catch (JsonGenerationException e) {
			throw new AndroidAgentException("Issue in json generation", e);
		} catch (IOException e) {
			throw new AndroidAgentException("Issue in parsing stream", e);
		} catch (JSONException e) {
			throw new AndroidAgentException("Issue in adding value to JSON", e);
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

		// Try to install apps from queue if there is no any ongoing installation operation
        if (appInstallOperationId == 0) {
            startPendingInstallation();
        }

		// Try to uninstall apps from queue if there is no any ongoing uninstallation operation
		if (appUninstallOperationId == 0) {
			startPendingUninstallation();
		}
	}

	private void startPendingInstallation(){
		AppInstallRequest appInstallRequest = AppManagementRequestUtil.getPendingInstall(context);
		// Start app installation from queue if app installation request available in the queue
		if (appInstallRequest != null) {
			ApplicationManager applicationManager = new ApplicationManager(context.getApplicationContext());
			Operation applicationOperation = new Operation();
			applicationOperation.setId(appInstallRequest.getApplicationOperationId());
			applicationOperation.setCode(appInstallRequest.getApplicationOperationCode());
			Log.d(TAG, "Try to start app installation from queue. Operation Id " +
					appInstallRequest.getApplicationOperationId());
			try {
				applicationManager.installApp(appInstallRequest.getAppUrl(), null, applicationOperation);
			} catch (AndroidAgentException e) {
				Log.e(TAG, "This is very unlikely to happen since schedule is null");
			}
		}
	}

	private void startPendingUninstallation(){
		AppUninstallRequest appUninstallRequest = AppManagementRequestUtil.getPendingUninstall(context);
		// Start app uninstall from queue if app uninstall request available in the queue
		if (appUninstallRequest != null) {
			ApplicationManager applicationManager = new ApplicationManager(context.getApplicationContext());
			Operation applicationOperation = new Operation();
			applicationOperation.setId(appUninstallRequest.getApplicationOperationId());
			applicationOperation.setCode(appUninstallRequest.getApplicationOperationCode());
			Log.d(TAG, "Try to start app uninstallation from queue. Operation Id " +
					appUninstallRequest.getApplicationOperationId());
			try {
				applicationManager.uninstallApplication(appUninstallRequest.getPackageName(),
						applicationOperation, null);
			} catch (AndroidAgentException e) {
				Preference.putInt(context, context.getResources().getString(
						R.string.app_uninstall_id), applicationOperation.getId());
				Preference.putString(context, context.getResources().getString(
						R.string.app_uninstall_code), applicationOperation.getCode());
				Preference.putString(context, context.getResources().getString(R.string.app_uninstall_status),
						Constants.AppState.UNINSTALL_FAILED);
				Preference.putString(context,
						context.getResources().getString(R.string.app_uninstall_failed_message), e.getMessage());
			}
		}
	}

	@SuppressWarnings("unused")
	@Override
	public void onReceiveAPIResult(Map<String, String> result, int requestCode) {
		String responseStatus;
		String response;
		if (requestCode == Constants.NOTIFICATION_REQUEST_CODE) {
			if (isWipeTriggered) {
				if(Constants.SYSTEM_APP_ENABLED) {
					CommonUtils.callSystemApp(context, Constants.Operation.WIPE_DATA, null, null);
				} else {
					Log.i(TAG, "Not the device owner.");
				}
			}

			if (isRebootTriggered) {
				CommonUtils.callSystemApp(context, Constants.Operation.REBOOT, null, null);
			}

			if (isUpgradeTriggered) {
				String schedule = Preference.getString(context, context.getResources().getString(R.string.pref_key_schedule));
                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, "Firmware upgrade operation passed to system app");
                }
				CommonUtils.callSystemApp(context, Constants.Operation.UPGRADE_FIRMWARE, schedule, null);
			}

			if (isShellCommandTriggered && shellCommand != null) {
				CommonUtils.callSystemApp(context, Constants.Operation.EXECUTE_SHELL_COMMAND, shellCommand, null);
			}

			if (result != null) {
				responseStatus = result.get(Constants.STATUS_KEY);
				if (Constants.Status.SUCCESSFUL.equals(responseStatus) || Constants.Status.CREATED.equals(responseStatus)) {
					response = result.get(Constants.RESPONSE);
					if (response != null && !response.isEmpty()) {
						if (Constants.DEBUG_MODE_ENABLED) {
							Log.d(TAG, "Pending Operations List: " + response);
						}
						performOperation(response);
					}
				}
			}
		}
	}

}
