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

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.AuthenticationErrorActivity;
import org.wso2.iot.agent.BuildConfig;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.api.DeviceInfo;
import org.wso2.iot.agent.beans.ServerConfig;
import org.wso2.iot.agent.events.EventRegistry;
import org.wso2.iot.agent.proxy.interfaces.APIResultCallBack;
import org.wso2.iot.agent.proxy.utils.Constants.HTTP_METHODS;
import org.wso2.iot.agent.services.AgentDeviceAdminReceiver;
import org.wso2.iot.agent.services.LocalNotification;
import org.wso2.iot.agent.services.MessageProcessor;
import org.wso2.iot.agent.utils.CommonDialogUtils;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Activity which handles user un-registration from the MDM server.
 */
public class AlreadyRegisteredActivity extends AppCompatActivity implements APIResultCallBack {

	private static final String TAG = AlreadyRegisteredActivity.class.getSimpleName();
	private static final int ACTIVATION_REQUEST = 47;
	private static final int DELAY_TIME = 0;
	private static final int PERIOD_TIME = 60 * 1000;
	private String regId;
	private Context context;
	private ProgressDialog progressDialog;
	private boolean isFreshRegistration = false;
	private DevicePolicyManager devicePolicyManager;
	private ComponentName cdmDeviceAdmin;
	private long lastSyncMillis = -1;
	private TextView textViewLastSync;
	private ImageView imageViewRefresh;
	private Handler mHandler;
	private Timer mTimer;

	private BroadcastReceiver syncUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, Intent intent) {
			lastSyncMillis = CommonUtils.currentDate().getTime();
			updateSyncText();
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_already_registered);

		textViewLastSync = (TextView) findViewById(R.id.textViewLastSync);
		imageViewRefresh = (ImageView) findViewById(R.id.imageViewRefresh);
		devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
		cdmDeviceAdmin = new ComponentName(this, AgentDeviceAdminReceiver.class);
		context = this;
		DeviceInfo info = new DeviceInfo(context);
		Bundle extras = getIntent().getExtras();

		if (extras != null) {
			if (extras.containsKey(getResources().getString(R.string.intent_extra_fresh_reg_flag))) {
				isFreshRegistration = extras.getBoolean(getResources().getString(R.string.intent_extra_fresh_reg_flag));
			}
		}
		String registrationId = Preference.getString(context, Constants.PreferenceFlag.REG_ID);

		if (registrationId != null && !registrationId.isEmpty()) {
			regId = registrationId;
		} else{
			regId = info.getDeviceId();
		}

		if (isFreshRegistration) {
			Preference.putBoolean(context, Constants.PreferenceFlag.REGISTERED, true);
			if (!isDeviceAdminActive()) {
				startDeviceAdminPrompt(cdmDeviceAdmin);
			}
			isFreshRegistration = false;
		}

		RelativeLayout relativeLayoutSync = (RelativeLayout) findViewById(R.id.layoutSync);
		relativeLayoutSync.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (Preference.getBoolean(context, Constants.PreferenceFlag.REGISTERED)
						&& isDeviceAdminActive()) {
					syncWithServer();
				}
			}
		});

		RelativeLayout relativeLayoutDeviceInfo = (RelativeLayout) findViewById(R.id.layoutDeviceInfo);
		relativeLayoutDeviceInfo.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				loadDeviceInfoActivity();
			}
		});

		RelativeLayout relativeLayoutChangePIN = (RelativeLayout) findViewById(R.id.layoutChangePIN);
		relativeLayoutChangePIN.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				loadPinCodeActivity();
			}
		});

		RelativeLayout relativeLayoutRegistration = (RelativeLayout) findViewById(R.id.layoutRegistration);
		if (Constants.HIDE_UNREGISTER_BUTTON) {
			relativeLayoutRegistration.setVisibility(View.GONE);
		} else {
			relativeLayoutRegistration.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showUnregisterDialog();
				}
			});
		}

		TextView textViewAgentVersion = (TextView) findViewById(R.id.textViewVersion);
		String versionText = BuildConfig.BUILD_TYPE + " v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ") ";
		textViewAgentVersion.setText(versionText);

		if (Build.VERSION.SDK_INT >= 23) {
			List<String> missingPermissions = new ArrayList<>();

			if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
				missingPermissions.add(android.Manifest.permission.READ_PHONE_STATE);
			}
			if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				missingPermissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
			}
			if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				missingPermissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
			}
			if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				missingPermissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
			}

			if (!missingPermissions.isEmpty()) {
				ActivityCompat.requestPermissions(AlreadyRegisteredActivity.this,
						missingPermissions.toArray(new String[missingPermissions.size()]),
						110);
			}
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			try {
				int locationSetting = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
				if (locationSetting == 0) {
					Intent enableLocationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
					startActivity(enableLocationIntent);
					Toast.makeText(context, R.string.msg_need_location, Toast.LENGTH_LONG).show();
				}
			} catch (Settings.SettingNotFoundException e) {
				Log.w(TAG, "Location setting is not available on this device");
			}
		}

		boolean isRegistered = Preference.getBoolean(context, Constants.PreferenceFlag.REGISTERED);
		if (isRegistered) {
			if (CommonUtils.isNetworkAvailable(context)) {
				String serverIP = Constants.DEFAULT_HOST;
				String prefIP = Preference.getString(context, Constants.PreferenceFlag.IP);
				if (prefIP != null) {
					serverIP = prefIP;
				}
				regId = Preference.getString(context, Constants.PreferenceFlag.REG_ID);

				if (regId != null) {
					if (serverIP != null && !serverIP.isEmpty()) {
						ServerConfig utils = new ServerConfig();
						utils.setServerIP(serverIP);
						if (utils.getHostFromPreferences(context) != null && !utils.getHostFromPreferences(context).isEmpty()) {
							CommonUtils.callSecuredAPI(AlreadyRegisteredActivity.this,
									utils.getAPIServerURL(context) + Constants.DEVICES_ENDPOINT + regId + Constants.IS_REGISTERED_ENDPOINT,
									HTTP_METHODS.GET,
									null, AlreadyRegisteredActivity.this,
									Constants.IS_REGISTERED_REQUEST_CODE);
						} else {
							try {
								CommonUtils.clearAppData(context);
							} catch (AndroidAgentException e) {
								String msg = "Device already dis-enrolled.";
								Log.e(TAG, msg, e);
							}
							loadInitialActivity();
						}
					} else {
						Log.e(TAG, "There is no valid IP to contact server");
					}
				}
			} else {
				if(!Constants.HIDE_ERROR_DIALOG) {
					CommonDialogUtils.showNetworkUnavailableMessage(AlreadyRegisteredActivity.this);
				}
			}
		} else {
			loadInitialActivity();
		}
	}

	@Override
	protected void onDestroy(){
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		super.onDestroy();
		if (progressDialog != null && progressDialog.isShowing()) {
			progressDialog.dismiss();
			progressDialog = null;
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (requestCode == 110) {
			List<String> missingPermissions = new ArrayList<>();
			for (int i =0;  i < permissions.length; i++) {
				if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
					missingPermissions.add(permissions[i]);
				}
			}
			if (!missingPermissions.isEmpty()) {
				Log.w(TAG, "Permissions not granted: " + missingPermissions.toString());
			}
			NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			mNotificationManager.cancel(Constants.PERMISSION_MISSING, Constants.PERMISSION_MISSING_NOTIFICATION_ID);
		}
	}

	private void startEvents() {
		if(!EventRegistry.eventListeningStarted) {
			EventRegistry registerEvent = new EventRegistry(this);
			registerEvent.register();
		}
	}

	private DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch (which) {
				case DialogInterface.BUTTON_POSITIVE:
					startUnRegistration();
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					dialog.dismiss();
					break;
				default:
					break;
			}
		}
	};

	/**
	 * Send unregistration request.
	 */
	private void startUnRegistration() {
		progressDialog = ProgressDialog.show(context,
						getResources().getString(R.string.dialog_message_unregistering),
						getResources().getString(R.string.dialog_message_please_wait),
						true);

		if (regId != null && !regId.isEmpty()) {
			if (CommonUtils.isNetworkAvailable(context)) {
				String serverIP = Constants.DEFAULT_HOST;
				String prefIP = Preference.getString(context, Constants.PreferenceFlag.IP);
				if (prefIP != null) {
					serverIP = prefIP;
				}
				if (serverIP != null && !serverIP.isEmpty()) {
					stopPolling();
					ServerConfig utils = new ServerConfig();
					utils.setServerIP(serverIP);

					CommonUtils.callSecuredAPI(context,
					                           utils.getAPIServerURL(context) + Constants.UNREGISTER_ENDPOINT + regId,
					                           HTTP_METHODS.DELETE,
					                           null, AlreadyRegisteredActivity.this,
					                           Constants.UNREGISTER_REQUEST_CODE);
				} else {
					Log.e(TAG, "There is no valid IP to contact the server");
					CommonDialogUtils.stopProgressDialog(progressDialog);
					CommonDialogUtils.showNetworkUnavailableMessage(context);
				}
			} else {
				Log.e(TAG, "Registration ID is not available");
				CommonDialogUtils.stopProgressDialog(progressDialog);
				CommonDialogUtils.showNetworkUnavailableMessage(context);
			}
		}
	}

	@Override
	public void onBackPressed() {
		loadHomeScreen();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			loadHomeScreen();
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_HOME) {
			loadHomeScreen();
			return true;
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (Constants.DEBUG_MODE_ENABLED) {
			Log.d(TAG, "Calling onResume");
		}

		if (Build.VERSION.SDK_INT >= 19) {
			try {
				int locationSetting = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
				if (locationSetting != 0) {
					NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
					mNotificationManager.cancel(Constants.LOCATION_DISABLED, Constants.LOCATION_DISABLED_NOTIFICATION_ID);
				}
			} catch (Settings.SettingNotFoundException e) {
				Log.w(TAG, "Location setting is not available on this device");
			}
		}
		updateSyncText();
		IntentFilter filter = new IntentFilter(Constants.SYNC_BROADCAST_ACTION);
		registerReceiver(syncUpdateReceiver, filter);

		mHandler = new Handler();
		mTimer = new Timer();
		mTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						updateSyncText();
					}
				});
			}
		}, DELAY_TIME, PERIOD_TIME);
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(syncUpdateReceiver);
		mTimer.cancel();
	}

	private void updateSyncText() {
		if (lastSyncMillis <= 0
				&& Preference.hasPreferenceKey(context, Constants.PreferenceFlag.LAST_SERVER_CALL)) {
			lastSyncMillis = Preference.getLong(context, Constants.PreferenceFlag.LAST_SERVER_CALL);
		}
		String syncText = CommonUtils.getTimeAgo(lastSyncMillis, context);
		if (syncText == null) {
			syncText = getResources().getString(R.string.txt_never);
		}
		imageViewRefresh.clearAnimation();
		textViewLastSync.setText(syncText);
		textViewLastSync.invalidate();
	}

	/**
	 * Displays an internal server error message to the user.
	 */
	private void displayInternalServerError() {
		AlertDialog.Builder alertDialog = CommonDialogUtils.getAlertDialogWithOneButtonAndTitle(context,
				getResources().getString(R.string.title_head_connection_error),
				getResources().getString(R.string.error_internal_server),
				getResources().getString(R.string.button_ok),
				null);
		alertDialog.show();
	}

	@Override
	public void onReceiveAPIResult(Map<String, String> result, int requestCode) {

		if (Constants.DEBUG_MODE_ENABLED) {
			Log.d(TAG, "onReceiveAPIResult> request code: " + requestCode);
		}

		CommonDialogUtils.stopProgressDialog(progressDialog);

		String responseStatus;
		if (requestCode == Constants.UNREGISTER_REQUEST_CODE) {
			if (result != null) {
				responseStatus = result.get(Constants.STATUS);
				if (responseStatus != null && Constants.Status.SUCCESSFUL.equals(responseStatus)) {
					stopPolling();
					initiateUnregistration();
				} else if (Constants.Status.INTERNAL_SERVER_ERROR.equals(responseStatus)) {
					startPolling();
					displayInternalServerError();
				} else {
					startPolling();
					loadAuthenticationErrorActivity();
				}
			} else {
				startPolling();
				loadAuthenticationErrorActivity();
			}
		} else if (requestCode == Constants.IS_REGISTERED_REQUEST_CODE) {
			if (result != null) {
				responseStatus = result.get(Constants.STATUS);
				if (Constants.Status.INTERNAL_SERVER_ERROR.equals(responseStatus)) {
					displayInternalServerError();
				} else if (Constants.Status.SUCCESSFUL.equals(responseStatus) || Constants.Status.ACCEPT.equals(responseStatus)) {
					if (Constants.DEBUG_MODE_ENABLED) {
						Log.d(TAG, "Device has already enrolled");
					}
					if (isDeviceAdminActive()) {
						startPolling();
					}
				} else {
					stopPolling();
					initiateUnregistration();
					loadInitialActivity();
				}
			}
		}
	}

	/**
	 * Load device home screen.
	 */

	private void loadHomeScreen() {
		if(!devicePolicyManager.isProfileOwnerApp(getPackageName())) {
			finish();
			Intent i = new Intent();
			i.setAction(Intent.ACTION_MAIN);
			i.addCategory(Intent.CATEGORY_HOME);
			this.startActivity(i);
			super.onBackPressed();
		}
		else {
			Toast.makeText(this,"Press Home Button to exit.", Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Initiate unregistration.
	 */
	private void initiateUnregistration() {
		CommonUtils.disableAdmin(context);
		loadInitialActivity();
	}

	/**
	 * Start device admin activation request.
	 *
	 * @param cdmDeviceAdmin - Device admin component.
	 */
	private void startDeviceAdminPrompt(final ComponentName cdmDeviceAdmin) {
		AlreadyRegisteredActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Intent deviceAdminIntent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
				deviceAdminIntent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cdmDeviceAdmin);
				deviceAdminIntent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
				                           getResources().getString(R.string.device_admin_enable_alert));
				startActivityForResult(deviceAdminIntent, ACTIVATION_REQUEST);
			}
		});
	}

	/**
	 * Display unregistration confirmation dialog.
	 */
	private void showUnregisterDialog() {
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		} else {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		}
		AlertDialog.Builder alertDialog =
				CommonDialogUtils.getAlertDialogWithTwoButtonAndTitle(context,
						null,
						getResources().getString(R.string.dialog_unregister),
						getResources().getString(R.string.yes),
						getResources().getString(R.string.no),
						dialogClickListener, dialogClickListener);
		alertDialog.show();
	}

	/**
	 * Load device info activity.
	 */
	private void loadDeviceInfoActivity() {
		Intent intent =
				new Intent(AlreadyRegisteredActivity.this,
						DisplayDeviceInfoActivity.class);
		intent.putExtra(getResources().getString(R.string.intent_extra_from_activity),
				AlreadyRegisteredActivity.class.getSimpleName());
		startActivity(intent);
	}

	/**
	 * Load initial activity.
	 */
	private void loadInitialActivity() {
		Preference.putString(context, Constants.PreferenceFlag.IP, null);
		Intent intent = new Intent( AlreadyRegisteredActivity.this, SplashActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
		finish();
	}

	/**
	 * Load PIN code activity.
	 */
	private void loadPinCodeActivity() {
		Intent intent =
				new Intent(AlreadyRegisteredActivity.this, PinCodeActivity.class);
		intent.putExtra(getResources().getString(R.string.intent_extra_from_activity),
		                AlreadyRegisteredActivity.class.getSimpleName());
		startActivity(intent);
	}

	/**
	 * Loads authentication error activity.
	 */
	private void loadAuthenticationErrorActivity() {
		Intent intent =
				new Intent(AlreadyRegisteredActivity.this,
						AuthenticationErrorActivity.class);
		intent.putExtra(getResources().getString(R.string.intent_extra_regid), regId);
		intent.putExtra(getResources().getString(R.string.intent_extra_from_activity),
				AlreadyRegisteredActivity.class.getSimpleName());
		startActivity(intent);
	}

	/**
	 * Stops server polling task.
	 */
	private void stopPolling() {
		String notifier = Preference.getString(context, Constants.PreferenceFlag.NOTIFIER_TYPE);
		if(Constants.NOTIFIER_LOCAL.equals(notifier)) {
			LocalNotification.stopPolling(context);
		}
	}

	/**
	 * Starts server polling task.
	 */
	private void startPolling() {
		String notifier = Preference.getString(context, Constants.PreferenceFlag.NOTIFIER_TYPE);
		if(Constants.NOTIFIER_LOCAL.equals(notifier) &&
				!Constants.AUTO_ENROLLMENT_BACKGROUND_SERVICE_ENABLED) {
			LocalNotification.startPolling(context);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == ACTIVATION_REQUEST) {
			if (resultCode == AppCompatActivity.RESULT_OK) {
				startEvents();
				syncWithServer();
				CommonUtils.callSystemApp(context, null, null, null);
				Log.i("onActivityResult", "Administration enabled!");
			} else {
				Log.i("onActivityResult", "Administration enable FAILED!");
			}
		}
	}

    private void syncWithServer() {
        Animation rotate = AnimationUtils.loadAnimation(context, R.anim.clockwise_refresh);
        imageViewRefresh.startAnimation(rotate);
        textViewLastSync.setText(R.string.txt_sync);
        textViewLastSync.invalidate();
        MessageProcessor messageProcessor = new MessageProcessor(context);
        try {
            messageProcessor.getMessages();
        } catch (AndroidAgentException e) {
            Log.e(TAG, "Failed to sync with server", e);
        }
    }

	private boolean isDeviceAdminActive() {
		return devicePolicyManager.isAdminActive(cdmDeviceAdmin);
	}

}
