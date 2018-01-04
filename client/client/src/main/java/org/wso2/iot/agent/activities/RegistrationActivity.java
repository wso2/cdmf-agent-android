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
package org.wso2.iot.agent.activities;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;

import com.google.firebase.iid.FirebaseInstanceId;

import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.KioskActivity;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.api.DeviceInfo;
import org.wso2.iot.agent.beans.ServerConfig;
import org.wso2.iot.agent.proxy.interfaces.APIResultCallBack;
import org.wso2.iot.agent.proxy.utils.Constants.HTTP_METHODS;
import org.wso2.iot.agent.services.DeviceInfoPayload;
import org.wso2.iot.agent.utils.CommonDialogUtils;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

import java.util.Map;

/**
 * Activity which handles user enrollment.
 */
public class RegistrationActivity extends Activity implements APIResultCallBack {

	private static final String TAG = RegistrationActivity.class.getSimpleName();
	private static final int FCM_TOKEN_WAIT_MILLIS = 60000;

	private Context context;
	private ProgressDialog progressDialog;
	private boolean isFCMTokenReceiverRegistered = false;

	private BroadcastReceiver fcmTokenReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			CommonDialogUtils.stopProgressDialog(progressDialog);
			registerDevice();
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_registration);
		context = this;
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
	}

	@Override
	protected void onResume(){
		super.onResume();
		if (Constants.NOTIFIER_FCM.equals(Preference.getString(context, Constants.PreferenceFlag.NOTIFIER_TYPE))) {
			registerFCM();
		} else {
			registerDevice();
		}
	}

	@Override
	protected void onPause(){
		super.onPause();
		CommonDialogUtils.stopProgressDialog(progressDialog);
		if (isFCMTokenReceiverRegistered) {
			unregisterReceiver(fcmTokenReceiver);
		}
	}

	private void registerDevice() {
		progressDialog = CommonDialogUtils.showProgressDialog(RegistrationActivity.this,
				getResources().getString(R.string.dialog_enrolling),
				getResources().getString(R.string.dialog_please_wait),
				null);

		DeviceInfoPayload deviceInfoBuilder = new DeviceInfoPayload(context);
		DeviceInfo deviceInfo = new DeviceInfo(context);
		String deviceIdentifier = deviceInfo.getDeviceId();
		Preference.putString(context, Constants.PreferenceFlag.REG_ID, deviceIdentifier);

		String type = Preference.getString(context,
		                                   context.getResources().getString(R.string.shared_pref_reg_type));
		String username = Preference.getString(context,
		                                       context.getResources().getString(R.string.username));
		try {
			deviceInfoBuilder.build(type, username);
		} catch (AndroidAgentException e) {
			Log.e(TAG, "Error occurred while building the device info payload.", e);
		}

		// Check network connection availability before calling the API.
		if (CommonUtils.isNetworkAvailable(context)) {
			// Call device registration API.
			String ipSaved = Constants.DEFAULT_HOST;
			String prefIP = Preference.getString(context.getApplicationContext(), Constants.PreferenceFlag.IP);
			if (prefIP != null) {
				ipSaved = prefIP;
			}
			if (ipSaved != null && !ipSaved.isEmpty()) {
				ServerConfig utils = new ServerConfig();
				utils.setServerIP(ipSaved);

				CommonUtils.callSecuredAPI(RegistrationActivity.this,
				                           utils.getAPIServerURL(context) + Constants.REGISTER_ENDPOINT,
				                           HTTP_METHODS.POST,
				                           deviceInfoBuilder.getDeviceInfoPayload(),
				                           RegistrationActivity.this,
				                           Constants.REGISTER_REQUEST_CODE);
			} else {
				Log.e(TAG, "There is no valid IP to contact the server");
				CommonDialogUtils.stopProgressDialog(progressDialog);
				CommonDialogUtils.showNetworkUnavailableMessage(RegistrationActivity.this);
			}
		} else {
			CommonDialogUtils.stopProgressDialog(progressDialog);
			CommonDialogUtils.showNetworkUnavailableMessage(RegistrationActivity.this);
		}

	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			Intent i = new Intent();
			i.setAction(Intent.ACTION_MAIN);
			i.addCategory(Intent.CATEGORY_HOME);
			this.startActivity(i);
			finish();
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_HOME) {
			finish();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	private DialogInterface.OnClickListener registrationFailedOKBanClickListener =
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0,
				                    int arg1) {
					loadAuthenticationActivity();
				}
	};
	
	/**
	 * Loads Already registered activity.
	 */
	private void loadAlreadyRegisteredActivity(){
		Intent intent =
				new Intent(RegistrationActivity.this,
				           AlreadyRegisteredActivity.class);
		intent.putExtra(getResources().getString(R.string.intent_extra_fresh_reg_flag), true);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
		finish();
	}

	private void loadKioskActivity(){
		Intent intent =
				new Intent(RegistrationActivity.this, KioskActivity.class);
		intent.putExtra(getResources().getString(R.string.intent_extra_fresh_reg_flag), true);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
		finish();
	}
	
	/**
	 * Display connectivity error.
	 */
	private void displayConnectionError(){
		RegistrationActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				CommonDialogUtils.getAlertDialogWithOneButtonAndTitle(context,
						getResources().getString(R.string.title_head_connection_error),
						getResources().getString(R.string.error_internal_server),
						getResources().getString(R.string.button_ok),
						registrationFailedOKBanClickListener);
			}
		});
	}
	
	/**
	 * Display internal server error.
	 */
	private void displayInternalServerError(){
		RegistrationActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				CommonDialogUtils.getAlertDialogWithOneButtonAndTitle(context,
						getResources().getString(R.string.title_head_registration_error),
						getResources().getString(R.string.error_for_all_unknown_registration_failures),
						getResources().getString(R.string.button_ok),
						registrationFailedOKBanClickListener);
			}
		});
	}

	/**
	 * Display FCM services error
	 */
	private void displayFCMServicesError() {
		RegistrationActivity.this.runOnUiThread(new Runnable() {
			@Override public void run() {
				CommonDialogUtils.getAlertDialogWithOneButtonAndTitle(context,
						getResources().getString(R.string.title_head_registration_error),
						getResources().getString(R.string.error_for_fcm_unavailability),
						getResources().getString(R.string.button_ok), registrationFailedOKBanClickListener);
			}
		});
	}

	@Override
	public void onReceiveAPIResult(Map<String, String> result, int requestCode) {
		DeviceInfo info = new DeviceInfo(context);
		if (Constants.REGISTER_REQUEST_CODE == requestCode) {
			String responseStatus;
			if (result != null) {
				responseStatus = result.get(Constants.STATUS);
				Preference.putString(context, Constants.PreferenceFlag.REG_ID, info.getDeviceId());
				if (Constants.Status.SUCCESSFUL.equals(responseStatus) || Constants.Status.CREATED.equals(responseStatus)) {
					CommonDialogUtils.stopProgressDialog(progressDialog);
					if (Constants.OWNERSHIP_COSU.equals(Constants.DEFAULT_OWNERSHIP)) {
						loadKioskActivity();
					} else {
						loadAlreadyRegisteredActivity();
					}

				} else {
					displayInternalServerError();
				}
			} else {
				displayConnectionError();
			}
		} else if (Constants.POLICY_REQUEST_CODE == requestCode) {
			CommonDialogUtils.stopProgressDialog(progressDialog);
			if (Constants.OWNERSHIP_COSU.equals(Constants.DEFAULT_OWNERSHIP)) {
				loadKioskActivity();
			} else {
				loadAlreadyRegisteredActivity();
			}
		} else if (requestCode == Constants.FCM_REGISTRATION_ID_SEND_CODE && result != null) {
			String status = result.get(Constants.STATUS_KEY);
			if (!(Constants.Status.SUCCESSFUL.equals(status) || Constants.Status.ACCEPT.equals(status))) {
				displayConnectionError();
			} else {
				CommonDialogUtils.stopProgressDialog(progressDialog);
				if (Constants.OWNERSHIP_COSU.equals(Constants.DEFAULT_OWNERSHIP)) {
					loadKioskActivity();
				} else {
					loadAlreadyRegisteredActivity();
				}
			}
		} else {
			CommonDialogUtils.stopProgressDialog(progressDialog);
		}
	}

	/**
     * This will start the FCM flow by registering the device with Google and sending the
     * registration ID to IoTS. This is done in a Async task as a network call may be done, and
     * it should be done out side the UI thread. After retrieving the registration Id, it is send
     * to the MDM server so that it can send notifications to the device.
     */
	private void registerFCM() {
		String token =  FirebaseInstanceId.getInstance().getToken();
		if(token != null) {
			if (Constants.DEBUG_MODE_ENABLED){
				Log.d(TAG, "FCM Token: " + token);
			}
			Preference.putString(context, Constants.FCM_REG_ID, token);
			registerDevice();
		} else {
			Log.w(TAG, "FCM Token is null. Will depend on FCMInstanceIdService.");
			Preference.removePreference(context, Constants.FCM_REG_ID);
			progressDialog = CommonDialogUtils.showProgressDialog(RegistrationActivity.this,
					getResources().getString(R.string.dialog_enrolling),
					getResources().getString(R.string.dialog_configuring_fcm),
					null);
			isFCMTokenReceiverRegistered = true;
			registerReceiver(fcmTokenReceiver,
					new IntentFilter(Constants.FCM_TOKEN_REFRESHED_BROADCAST_ACTION));
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					unregisterReceiver(fcmTokenReceiver);
					isFCMTokenReceiverRegistered = false;
					displayFCMServicesError();
				}
			}, FCM_TOKEN_WAIT_MILLIS);
		}
	}

	/**
	 * Loads Authentication activity.
	 */
	private void loadAuthenticationActivity() {
		Preference.putString(context, Constants.PreferenceFlag.IP, null);
		Intent intent = new Intent( RegistrationActivity.this, AuthenticationActivity.class);
		intent.putExtra(getResources().getString(R.string.intent_extra_from_activity), RegistrationActivity.class.getSimpleName());
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
		finish();
	}

}
