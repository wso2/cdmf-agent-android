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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.util.Linkify;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.api.DeviceInfo;
import org.wso2.iot.agent.api.TenantResolverCallback;
import org.wso2.iot.agent.api.TenantResolverHandler;
import org.wso2.iot.agent.beans.ApiRegistrationProfile;
import org.wso2.iot.agent.beans.ServerConfig;
import org.wso2.iot.agent.beans.Tenant;
import org.wso2.iot.agent.proxy.IdentityProxy;
import org.wso2.iot.agent.proxy.authenticators.AuthenticatorFactory;
import org.wso2.iot.agent.proxy.authenticators.ClientAuthenticator;
import org.wso2.iot.agent.proxy.beans.CredentialInfo;
import org.wso2.iot.agent.proxy.interfaces.APIAccessCallBack;
import org.wso2.iot.agent.proxy.interfaces.APIResultCallBack;
import org.wso2.iot.agent.proxy.interfaces.AuthenticationCallback;
import org.wso2.iot.agent.proxy.utils.Constants.HTTP_METHODS;
import org.wso2.iot.agent.services.AgentDeviceAdminReceiver;
import org.wso2.iot.agent.services.DynamicClientManager;
import org.wso2.iot.agent.services.LocalNotification;
import org.wso2.iot.agent.services.location.LocationService;
import org.wso2.iot.agent.utils.CommonDialogUtils;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Activity that captures username, password and device ownership details
 * and handles authentication.
 */
public class AuthenticationActivity extends AppCompatActivity implements APIAccessCallBack,
		APIResultCallBack,
		AuthenticationCallback{
	private Button btnSignIn;
	private EditText etUsername;
	private EditText etDomain;
	private EditText etPassword;
	private String deviceType;
	private Context context;
	private String username;
	private String usernameVal;
	private String passwordVal;
	private String adminAccessToken;
	private ProgressDialog progressDialog;
	private boolean isReLogin = false;
	private boolean isCloudLogin = false;
	private int kioskExit;

	private DeviceInfo deviceInfo;
	private static final String TAG = AuthenticationActivity.class.getSimpleName();
	private static final int ACTIVATION_REQUEST = 47;
	private static final String[] SUBSCRIBED_API = new String[]{"android"};
	private Tenant currentTenant;
	private DevicePolicyManager devicePolicyManager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		context = this;
		devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
		if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		} else {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		}

		setContentView(R.layout.activity_authentication);

		deviceInfo = new DeviceInfo(context);
		etDomain = (EditText) findViewById(R.id.etDomain);
		etUsername = (EditText) findViewById(R.id.etUsername);
		etPassword = (EditText) findViewById(R.id.etPassword);
		etDomain.setFocusable(true);
		etDomain.requestFocus();
		btnSignIn = (Button) findViewById(R.id.btnSignIn);
		btnSignIn.setOnClickListener(onClickAuthenticate);
		btnSignIn.setEnabled(false);

		// change button color background till user enters a valid input
		btnSignIn.setBackgroundResource(R.drawable.btn_grey);
		btnSignIn.setTextColor(ContextCompat.getColor(this, R.color.black));
		TextView textViewSignIn = (TextView) findViewById(R.id.textViewSignIn);
		LinearLayout loginLayout = (LinearLayout) findViewById(R.id.loginLayout);

		if (Preference.hasPreferenceKey(context, Constants.TOKEN_EXPIRED)) {
			etDomain.setEnabled(false);
			etDomain.setTextColor(ContextCompat.getColor(this, R.color.black));
			etUsername.setEnabled(false);
			etUsername.setTextColor(ContextCompat.getColor(this, R.color.black));
			btnSignIn.setText(R.string.btn_sign_in);
			etPassword.setFocusable(true);
			etPassword.requestFocus();
			String tenantedUserName = Preference.getString(context, Constants.USERNAME);
			int tenantSeparator = tenantedUserName.lastIndexOf('@');
			etUsername.setText(tenantedUserName.substring(0, tenantSeparator));
			etDomain.setText(tenantedUserName.substring(tenantSeparator + 1, tenantedUserName.length()));
			isReLogin = true;
			textViewSignIn.setText(R.string.msg_need_to_sign_in);
		} else if (Constants.CLOUD_MANAGER != null) {
			isCloudLogin = true;
			etDomain.setVisibility(View.GONE);
			textViewSignIn.setText(R.string.txt_sign_in_cloud);
		}

		if (Preference.getBoolean(context, Constants.PreferenceFlag.DEVICE_ACTIVE) && !isReLogin) {
			Intent intent = new Intent(AuthenticationActivity.this, AlreadyRegisteredActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			finish();
			return;
		}

		TextView textViewSignUp = (TextView) findViewById(R.id.textViewSignUp);
		if (!isReLogin && Constants.SIGN_UP_URL != null) {
			Linkify.TransformFilter transformFilter = new Linkify.TransformFilter() {
				@Override
				public String transformUrl(Matcher match, String url) {
					return Constants.SIGN_UP_URL;
				}
			};
			Pattern pattern = Pattern.compile(getResources().getString(R.string.txt_sign_up_linkify));
			Linkify.addLinks(textViewSignUp, pattern, null, null, transformFilter);
		} else {
			textViewSignUp.setVisibility(View.GONE);
		}

		if(Constants.HIDE_LOGIN_UI) {
			loginLayout.setVisibility(View.GONE);
		}

		if (Constants.OWNERSHIP_COSU.equals(Constants.DEFAULT_OWNERSHIP)) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				startLockTask();
			}
		}

		TextView textViewWipeData = (TextView) this.findViewById(R.id.textViewWipeData);
		if(Constants.OWNERSHIP_COSU.equals(Constants.DEFAULT_OWNERSHIP) && Constants.DISPLAY_WIPE_DEVICE_BUTTON){
			textViewWipeData.setVisibility(View.VISIBLE);
			textViewWipeData.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					new AlertDialog.Builder(AuthenticationActivity.this)
							.setTitle(getString(R.string.app_name))
							.setMessage(R.string.wipe_confirmation)
							.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int whichButton) {
									DevicePolicyManager devicePolicyManager = (DevicePolicyManager)
											getApplicationContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
									if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
										devicePolicyManager.
												wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE |
														DevicePolicyManager.WIPE_RESET_PROTECTION_DATA);
									} else {
										devicePolicyManager.
												wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE);
									}
								}})
							.setNegativeButton(android.R.string.no, null)
							.show();
				}
			});
		}

		ImageView logo = (ImageView) findViewById(R.id.imageViewLogo);
		if (Constants.COSU_SECRET_EXIT) {
			logo.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					kioskExit++;
					if (kioskExit == 6) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
							stopLockTask();
						}
						finish();
					}
				}
			});
		}

		etUsername.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				enableSubmitIfReady();
			}

			@Override
			public void afterTextChanged(Editable s) {
				enableSubmitIfReady();
			}
		});

		etPassword.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				enableSubmitIfReady();
			}

			@Override
			public void afterTextChanged(Editable s) {
				enableSubmitIfReady();
			}
		});

		if (org.wso2.iot.agent.proxy.utils.Constants.Authenticator.AUTHENTICATOR_IN_USE.
				equals(org.wso2.iot.agent.proxy.utils.Constants.Authenticator.MUTUAL_SSL_AUTHENTICATOR)) {

			AuthenticatorFactory authenticatorFactory = new AuthenticatorFactory();
			ClientAuthenticator authenticator = authenticatorFactory.getClient(
					org.wso2.iot.agent.proxy.utils.Constants.Authenticator.AUTHENTICATOR_IN_USE,
					AuthenticationActivity.this, Constants.AUTHENTICATION_REQUEST_CODE);
			authenticator.doAuthenticate();
		}

		//This is an override to ownership type.
		if (Constants.DEFAULT_OWNERSHIP != null) {
			deviceType = Constants.DEFAULT_OWNERSHIP;
			Preference.putString(context, Constants.DEVICE_TYPE, deviceType);
		} else {
			deviceType = Constants.OWNERSHIP_BYOD;
		}

		if (Constants.OWNERSHIP_COSU.equals(Constants.DEFAULT_OWNERSHIP)) {
			Intent intent = getIntent();
			if(intent.hasExtra("android.app.extra.token")){
				adminAccessToken = intent.getStringExtra("android.app.extra.token");
				proceedToAuthentication();
			}
		}

		// This is added so that in case due to an agent customisation, if the authentication
		// activity is called the AUTO_ENROLLMENT_BACKGROUND_SERVICE_ENABLED is set, the activity
		// must be finished.
		if (Constants.AUTO_ENROLLMENT_BACKGROUND_SERVICE_ENABLED) {
			finish();
		}
	}

	@Override
	protected void onResume(){
		super.onResume();
		if (progressDialog != null) {
			progressDialog.show();
		}
	}

	@Override
	protected void onPause(){
		super.onPause();
		if (progressDialog != null && progressDialog.isShowing()) {
			progressDialog.dismiss();
		} else {
			progressDialog = null;
		}
	}

	@Override
	protected void onDestroy(){
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		super.onDestroy();
		CommonDialogUtils.stopProgressDialog(progressDialog);
		progressDialog = null;
	}

	private OnClickListener onClickAuthenticate = new OnClickListener() {

		@Override
		public void onClick(View view) {
			if (etUsername.getText() != null && !etUsername.getText().toString().trim().isEmpty() &&
					etPassword.getText() != null && !etPassword.getText().toString().trim().isEmpty()) {

				passwordVal = etPassword.getText().toString().trim();
				usernameVal = etUsername.getText().toString().trim();

				if (isCloudLogin) {
					obtainTenantDomain(usernameVal, passwordVal);
				} else {
					proceedToAuthentication();
				}
			} else {
				if (etUsername.getText() != null && !etUsername.getText().toString().trim().isEmpty()) {
					Toast.makeText(context,
							getResources().getString(R.string.toast_error_password),
							Toast.LENGTH_LONG).show();
				} else {
					Toast.makeText(context,
							getResources().getString(R.string.toast_error_username),
							Toast.LENGTH_LONG).show();
				}
			}
		}
	};

	private DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch (which) {
				case DialogInterface.BUTTON_POSITIVE:
					getClientCredentials();
					dialog.dismiss();
					break;
				case DialogInterface.BUTTON_NEGATIVE:
					dialog.dismiss();
					break;
				case DialogInterface.BUTTON_NEUTRAL:
					dialog.dismiss();
					break;
				default:
					break;
			}
		}
	};

	private void proceedToAuthentication() {
		if (etDomain.getText() != null && !etDomain.getText().toString().trim().isEmpty()) {
			usernameVal +=
					getResources().getString(R.string.intent_extra_at) +
							etDomain.getText().toString().trim();
		}
		if (Constants.OWNERSHIP_COPE.equals(deviceType) &&
				!CommonUtils.isSystemAppInstalled(context)) {
			showNoSystemAppDialog();
		} else {
			getClientCredentials();
		}
	}

	private void obtainTenantDomain(String username, String password) {
		// Check network connection availability before calling the API.
		currentTenant = null;
		if (CommonUtils.isNetworkAvailable(context)) {
			progressDialog = ProgressDialog.show(context,
					getResources().getString(R.string.dialog_authenticate),
					getResources().getString(R.string.dialog_message_please_wait), true);
			IdentityProxy.getInstance().setContext(this.getApplicationContext());
			TenantResolverHandler tenantResolverHandler = new TenantResolverHandler(new TenantResolverCallback() {
				@Override
				public void onTenantResolved(final List<Tenant> tenants) {
					if (tenants.isEmpty()) {
						showEnrollmentFailedErrorMessage(getResources().getString(R.string.dialog_tenants_not_available));
					} else if (tenants.size() == 1) {
						currentTenant = tenants.get(0);
						CommonDialogUtils.stopProgressDialog(progressDialog);
						etDomain.setText(currentTenant.getTenantDomain());
						proceedToAuthentication();
					} else {
						CommonDialogUtils.stopProgressDialog(progressDialog);
						List<String> tenantNames = new ArrayList<>();
						for (Tenant t : tenants) {
							tenantNames.add(t.getDisplayName());
						}
						CommonDialogUtils.getAlertDialogWithSingleChoices(context,
								getResources().getString(R.string.dialog_select_tenant),
								tenantNames.toArray(new String[0]),
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
										currentTenant = tenants.get(which);
										etDomain.setText(currentTenant.getTenantDomain());
										proceedToAuthentication();
										dialog.dismiss();
									}
								}).show();
					}
				}

				@Override
				public void onAuthenticationSuccess() {
					CommonDialogUtils.stopProgressDialog(progressDialog);
					progressDialog = ProgressDialog.show(context,
							getResources().getString(R.string.dialog_tenant_resolving),
							getResources().getString(R.string.dialog_message_please_wait), true);
				}

				@Override
				public void onAuthenticationFail() {
					showAuthenticationError();
				}

				@Override
				public void onFailure(AndroidAgentException exception) {
					showEnrollmentFailedErrorMessage(exception.getMessage());
				}
			});
			tenantResolverHandler.resolveTenantDomain(username, password);
		} else {
			CommonDialogUtils.showNetworkUnavailableMessage(context);
		}
	}

	/**
	 * Start device admin activation request.
	 *
	 */
	private void startDeviceAdminPrompt() {
		DevicePolicyManager devicePolicyManager = (DevicePolicyManager)
				getSystemService(Context.DEVICE_POLICY_SERVICE);
		ComponentName cdmDeviceAdmin =
				new ComponentName(AuthenticationActivity.this, AgentDeviceAdminReceiver.class);
		if(!devicePolicyManager.isAdminActive(cdmDeviceAdmin)){
			Intent deviceAdminIntent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
			deviceAdminIntent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cdmDeviceAdmin);
			deviceAdminIntent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
					getResources().getString(R.string.device_admin_enable_alert));
			startActivityForResult(deviceAdminIntent, ACTIVATION_REQUEST);
		}
	}

	/**
	 * Start authentication process.
	 */
	private void startAuthentication() {
		Preference.putString(context, Constants.DEVICE_TYPE, deviceType);

		// Check network connection availability before calling the API.
		if (CommonUtils.isNetworkAvailable(context)) {
			String clientId = Preference.getString(context, Constants.CLIENT_ID);
			String clientSecret = Preference.getString(context, Constants.CLIENT_SECRET);

			if (clientId == null || clientSecret == null) {
				String clientCredentials = Preference.getString(context, getResources().getString(R.string.shared_pref_client_credentials));
				if (clientCredentials != null) {
					try {
						JSONObject payload = new JSONObject(clientCredentials);
						clientId = payload.getString(Constants.CLIENT_ID);
						clientSecret = payload.getString(Constants.CLIENT_SECRET);

						if (clientId != null && !clientId.isEmpty() &&
								clientSecret != null && !clientSecret.isEmpty()) {
							initializeIDPLib(clientId, clientSecret);
						}
					} catch (JSONException e) {
						String msg = "error occurred while parsing client credential payload";
						Log.e(TAG, msg, e);
						CommonDialogUtils.stopProgressDialog(progressDialog);
						showInternalServerErrorMessage();
					}
				} else {
					String msg = "error occurred while retrieving client credentials";
					Log.e(TAG, msg);
					CommonDialogUtils.stopProgressDialog(progressDialog);
					showInternalServerErrorMessage();
				}
			} else {
				initializeIDPLib(clientId, clientSecret);
			}

		} else {
			CommonDialogUtils.stopProgressDialog(progressDialog);
			CommonDialogUtils.showNetworkUnavailableMessage(context);
		}

	}

	/**
	 * Initialize the Android IDP SDK by passing credentials,client ID and
	 * client secret.
	 *
	 * @param clientKey    client id value to access APIs..
	 * @param clientSecret client secret value to access APIs.
	 */
	private void initializeIDPLib(String clientKey, String clientSecret) {
		String serverIP = Constants.DEFAULT_HOST;
		String prefIP = Preference.getString(AuthenticationActivity.this, Constants.PreferenceFlag.IP);

		if (prefIP != null) {
			serverIP = prefIP;
		}
		if (serverIP != null && !serverIP.isEmpty()) {
			ServerConfig utils = new ServerConfig();
			utils.setServerIP(serverIP);
			String serverURL = utils.getServerURL(context) + Constants.OAUTH_ENDPOINT;
			Editable tenantDomain = etDomain.getText();

			if (tenantDomain != null && !tenantDomain.toString().trim().isEmpty()) {
				username =
						etUsername.getText().toString().trim() +
								context.getResources().getString(R.string.intent_extra_at) + tenantDomain.toString().trim();

			} else {
				username = etUsername.getText().toString().trim();
			}

			Preference.putString(context, Constants.CLIENT_ID, clientKey);
			Preference.putString(context, Constants.CLIENT_SECRET, clientSecret);

			CredentialInfo info = new CredentialInfo();
			info.setClientID(clientKey);
			info.setClientSecret(clientSecret);
			info.setUsername(username);

			info.setPassword(passwordVal);
			info.setTokenEndPoint(serverURL);

			if (adminAccessToken != null) {
				info.setAdminAccessToken(adminAccessToken);
			}

			//adding device-specific scope
			String deviceScope = "device_" + deviceInfo.getDeviceId();
			info.setScopes(deviceScope);

			if (tenantDomain != null && !tenantDomain.toString().trim().isEmpty()) {
				info.setTenantDomain(tenantDomain.toString().trim());
			}

			IdentityProxy.getInstance().init(info, AuthenticationActivity.this, this.getApplicationContext());
		}
	}



	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == ACTIVATION_REQUEST) {
			if (resultCode == Activity.RESULT_OK) {
				checkManifestPermissions();
				CommonUtils.callSystemApp(context, null, null, null);
				Log.i("onActivityResult", "Administration enabled!");
			} else {
				Log.i("onActivityResult", "Administration enable FAILED!");
				startDeviceAdminPrompt();
			}
		}
	}


	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onAPIAccessReceive(String status) {
		if (status != null) {
			if (status.trim().equals(Constants.Status.SUCCESSFUL)) {
				CommonDialogUtils.stopProgressDialog(progressDialog);
				if (isReLogin) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(context, R.string.authentication_successful, Toast.LENGTH_LONG).show();
						}
					});
					LocalNotification.startPolling(context);
					Preference.removePreference(context, Constants.TOKEN_EXPIRED);
					NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
					mNotificationManager.cancel(Constants.TOKEN_EXPIRED, Constants.SIGN_IN_NOTIFICATION_ID);
					finish();
				} else {
					Preference.putString(context, Constants.USERNAME, username);
					// Check network connection availability before calling the API.
					if (CommonUtils.isNetworkAvailable(context)) {
						getLicense();
					} else {
						CommonDialogUtils.showNetworkUnavailableMessage(AuthenticationActivity.this);
					}
				}
			} else if (status.trim().equals(Constants.Status.AUTHENTICATION_FAILED)) {
				showAuthenticationError();
				// clearing client credentials from shared memory
				CommonUtils.clearClientCredentials(context);
			} else if (status.trim().equals(Constants.Status.INTERNAL_SERVER_ERROR)) {
				showInternalServerErrorMessage();
			} else {
				showAuthCommonErrorMessage();
			}
		} else {
			showAuthCommonErrorMessage();
		}

	}

	/**
	 * Initialize get device license agreement. Check if the user has already
	 * agreed to license agreement
	 */
	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	private void getLicense() {
		boolean isAgreed = Preference.getBoolean(context, Constants.PreferenceFlag.IS_AGREED);
		deviceType = Preference.getString(context, Constants.DEVICE_TYPE);

		if(deviceType == null) {
			deviceType = Constants.DEFAULT_OWNERSHIP;
			Preference.putString(context, Constants.DEVICE_TYPE,
					deviceType);
		}

		if (deviceType != null && Constants.OWNERSHIP_BYOD.equals(deviceType.trim())) {

			if (!isAgreed) {
				final OnCancelListener cancelListener = new OnCancelListener() {
					@Override
					public void onCancel(DialogInterface arg0) {
						CommonDialogUtils.getAlertDialogWithOneButtonAndTitle(context,
								getResources().getString(R.string.error_enrollment_failed_detail),
								getResources().getString(R.string.error_enrollment_failed),
								getResources().getString(R.string.button_ok), null);
					}
				};

				AuthenticationActivity.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						progressDialog = CommonDialogUtils.showProgressDialog(context,
								getResources().getString(
										R.string.dialog_license_agreement),
								getResources().getString(
										R.string.dialog_please_wait),
								cancelListener);
					}
				});

				// Check network connection availability before calling the API.
				if (CommonUtils.isNetworkAvailable(context)) {
					getLicenseFromServer();
				} else {
					CommonDialogUtils.stopProgressDialog(progressDialog);
					CommonDialogUtils.showNetworkUnavailableMessage(context);
				}

			} else {
				startDeviceAdminPrompt();
			}
		} else if (deviceType != null){
			checkManifestPermissions();
		}

	}

	/**
	 * Retriever license agreement details from the server.
	 */
	private void getLicenseFromServer() {
		String ipSaved = Constants.DEFAULT_HOST;
		String prefIP = Preference.getString(context.getApplicationContext(), Constants.PreferenceFlag.IP);
		if (prefIP != null) {
			ipSaved = prefIP;
		}

		if (ipSaved != null && !ipSaved.isEmpty()) {
			ServerConfig utils = new ServerConfig();
			utils.setServerIP(ipSaved);
			CommonUtils.callSecuredAPI(AuthenticationActivity.this,
					utils.getAPIServerURL(context) + Constants.LICENSE_ENDPOINT,
					HTTP_METHODS.GET, null, AuthenticationActivity.this,
					Constants.LICENSE_REQUEST_CODE
			);
		} else {
			Log.e(TAG, "There is no valid IP to contact the server");
		}
	}



	/**
	 * Retriever configurations from the server.
	 */
	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	private void checkManifestPermissions(){
		if (ActivityCompat.checkSelfPermission(AuthenticationActivity.this, android.Manifest.permission.READ_PHONE_STATE)
				!= PackageManager.PERMISSION_GRANTED) {

			ActivityCompat.requestPermissions(AuthenticationActivity.this,
					new String[]{android.Manifest.permission.READ_PHONE_STATE,
							android.Manifest.permission.ACCESS_COARSE_LOCATION,
							android.Manifest.permission.ACCESS_FINE_LOCATION,
							android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
					110);
		}else{
			getConfigurationsFromServer();
		}
		//Since the agent in Work Profile already granted the Device Admin Permissions,
		// the relevant preference flag is changed to True.
		if (devicePolicyManager.isProfileOwnerApp(getApplicationContext().getPackageName())){
			Preference.putBoolean(context, Constants.PreferenceFlag.DEVICE_ACTIVE, true);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
										   @NonNull String[] permissions,
										   @NonNull int[] grantResults) {
		if(requestCode == 110){
			getConfigurationsFromServer();
		}
	}


	/**
	 * Retriever configurations from the server.
	 */
	private void getConfigurationsFromServer() {
		final OnCancelListener cancelListener = new OnCancelListener() {

			@Override
			public void onCancel(DialogInterface arg0) {
				CommonDialogUtils.getAlertDialogWithOneButtonAndTitle(context,
						getResources().getString(R.string.error_enrollment_failed_detail),
						getResources().getString(R.string.error_enrollment_failed),
						getResources().getString(R.string.button_ok), null);
			}
		};
		AuthenticationActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				progressDialog =
						CommonDialogUtils.showProgressDialog(context,
								getResources().getString(
										R.string.dialog_sender_id),
								getResources().getString(
										R.string.dialog_please_wait),
								cancelListener);
			}
		});

		String ipSaved = Constants.DEFAULT_HOST;
		String prefIP = Preference.getString(context.getApplicationContext(), Constants.PreferenceFlag.IP);
		if (prefIP != null) {
			ipSaved = prefIP;
		}

		if (ipSaved != null && !ipSaved.isEmpty()) {
			ServerConfig utils = new ServerConfig();
			utils.setServerIP(ipSaved);
			CommonUtils.callSecuredAPI(AuthenticationActivity.this,
					utils.getAPIServerURL(context) + Constants.CONFIGURATION_ENDPOINT,
					HTTP_METHODS.GET, null, AuthenticationActivity.this,
					Constants.CONFIGURATION_REQUEST_CODE
			);
		} else {
			Log.e(TAG, "There is no valid IP to contact the server");
		}
	}

	@Override
	public void onReceiveAPIResult(Map<String, String> result, int requestCode) {
		if (requestCode == Constants.LICENSE_REQUEST_CODE) {
			manipulateLicenseResponse(result);
		} else if (requestCode == Constants.CONFIGURATION_REQUEST_CODE){
			manipulateConfigurationResponse(result);
		} else if (requestCode == Constants.DYNAMIC_CLIENT_REGISTER_REQUEST_CODE) {
			manipulateDynamicClientResponse(result);
		}
	}

	/**
	 * Manipulates the dynamic client registration response received from server.
	 *
	 * @param result the result of the dynamic client request
	 */
	private void manipulateDynamicClientResponse(Map<String, String> result) {
		String responseStatus;
		if (result != null) {
			responseStatus = result.get(Constants.STATUS);
			if (Constants.Status.CREATED.equals(responseStatus)) {
				String dynamicClientResponse = result.get(Constants.RESPONSE);
				if (dynamicClientResponse != null) {
					Preference.putString(context, getResources().getString(R.string.shared_pref_client_credentials),
							dynamicClientResponse);
					startAuthentication();
				}
			} else if (Constants.Status.UNAUTHORIZED.equals(responseStatus)) {
				showAuthenticationError();
			} else if (!Constants.Status.SUCCESSFUL.equals(responseStatus)){
				if (result.containsKey(Constants.RESPONSE)) {
					showEnrollmentFailedErrorMessage("Code: " + responseStatus + "\nError: " + result.get(Constants.RESPONSE));
				} else {
					showEnrollmentFailedErrorMessage("Code: " + responseStatus);
				}
			}
		} else {
			showEnrollmentFailedErrorMessage(null);
		}
	}

	/**
	 * Manipulates the Configuration response received from server.
	 *
	 * @param result the result of the configuration request
	 */
	private void manipulateConfigurationResponse(Map<String, String> result) {
		boolean proceedNext = true;
		String responseStatus;
		CommonDialogUtils.stopProgressDialog(progressDialog);

		if (result != null) {
			responseStatus = result.get(Constants.STATUS);
			if (Constants.Status.SUCCESSFUL.equals(responseStatus)) {
				String configurationResponse = result.get(Constants.RESPONSE);

				if (configurationResponse != null) {
					try {
						JSONObject config = new JSONObject(configurationResponse);
						if (!config.isNull(context.getString(R.string.shared_pref_configuration))) {
							JSONArray configList = new JSONArray(config.getString(context.getString(R.string.
									shared_pref_configuration)));
							for (int i = 0; i < configList.length(); i++) {
								JSONObject param = new JSONObject(configList.get(i).toString());
								if(param.getString(context.getString(R.string.shared_pref_config_key)).trim().equals(
										Constants.PreferenceFlag.NOTIFIER_TYPE)){
									String type = param.getString(context.getString(R.string.shared_pref_config_value)).trim();
									if(type.equals(String.valueOf(Constants.NOTIFIER_CHECK))) {
										Preference.putString(context, Constants.PreferenceFlag.NOTIFIER_TYPE,
												Constants.NOTIFIER_FCM);
									}else{
										Preference.putString(context, Constants.PreferenceFlag.NOTIFIER_TYPE,
												Constants.NOTIFIER_LOCAL);
									}
								} else if(param.getString(context.getString(R.string.shared_pref_config_key)).trim().
										equals(context.getString(R.string.shared_pref_frequency)) && !param.getString(
										context.getString(R.string.shared_pref_config_value)).trim().isEmpty()){
									Preference.putInt(context, getResources().getString(R.string.shared_pref_frequency),
											Integer.valueOf(param.getString(context.getString(R.string.shared_pref_config_value)).trim()));
								} else if(param.getString(context.getString(R.string.shared_pref_config_key)).trim().
										equals(context.getString(R.string.shared_pref_gcm))){
									Preference.putString(context, getResources().getString(R.string.shared_pref_sender_id),
											param.getString(context.getString(R.string.shared_pref_config_value)).trim());
								}
							}
							String notifierType = Preference.getString(context, Constants.PreferenceFlag.NOTIFIER_TYPE);
							if (notifierType == null || notifierType.isEmpty()) {
								setDefaultNotifier();
							}
						}

					} catch (JSONException e) {
						Log.e(TAG, "Error parsing configuration response JSON", e);
						setDefaultNotifier();
					}
				} else {
					Log.e(TAG, "Empty configuration response");
					setDefaultNotifier();
				}
			} else if (Constants.Status.UNAUTHORIZED.equals(responseStatus)) {
				String response = result.get(Constants.RESPONSE);
				Log.e(TAG, "Unauthorized :" + response);
				showAuthenticationError();
				proceedNext = false;
			} else if (Constants.Status.INTERNAL_SERVER_ERROR.equals(responseStatus)) {
				Log.e(TAG, "Empty configuration response.");
				setDefaultNotifier();
			} else {
				Log.e(TAG, "Empty configuration response.");
				setDefaultNotifier();
			}

		} else {
			Log.e(TAG, "Empty configuration response.");
			setDefaultNotifier();
		}
		if(proceedNext) {
			if (!CommonUtils.isServiceRunning(context, LocationService.class)){
				Intent serviceIntent = new Intent(context, LocationService.class);
				context.startService(serviceIntent);
			}
			loadNextActivity();
		}
	}

	private void setDefaultNotifier(){
		Preference.putString(context, Constants.PreferenceFlag.NOTIFIER_TYPE, Constants.NOTIFIER_LOCAL);
		Preference.putInt(context, getResources().getString(R.string.shared_pref_frequency),
				Constants.DEFAULT_INTERVAL);
	}


	/**
	 * Manipulates the License agreement response received from server.
	 *
	 * @param result the result of the license agreement request
	 */
	private void manipulateLicenseResponse(Map<String, String> result) {
		String responseStatus;
		CommonDialogUtils.stopProgressDialog(progressDialog);

		if (result != null) {
			responseStatus = result.get(Constants.STATUS);
			if (Constants.Status.SUCCESSFUL.equals(responseStatus)) {
				String licenseAgreement = result.get(Constants.RESPONSE);

				if (licenseAgreement != null) {
					Preference.putString(context, getResources().getString(R.string.shared_pref_eula), licenseAgreement);
					showAgreement(licenseAgreement, Constants.EULA_TITLE);
				} else {
					CommonUtils.clearClientCredentials(context);
					showErrorMessage(
							getResources().getString(R.string.error_enrollment_failed_detail),
							getResources().getString(R.string.error_enrollment_failed));
				}

			} else if (Constants.Status.INTERNAL_SERVER_ERROR.equals(responseStatus)) {
				CommonUtils.clearClientCredentials(context);
				showInternalServerErrorMessage();
			} else if (Constants.Status.UNAUTHORIZED.equals(responseStatus)) {
				String response = result.get(Constants.RESPONSE);
				Log.e(TAG, "Unauthorized :" + response);
				showAuthenticationError();
			} else {
				CommonUtils.clearClientCredentials(context);
				showEnrollmentFailedErrorMessage(responseStatus);
			}

		} else {
			CommonUtils.clearClientCredentials(context);
			showEnrollmentFailedErrorMessage(null);
		}
	}

	private void showNoSystemAppDialog(){
		AlertDialog.Builder alertDialog =
				CommonDialogUtils.getAlertDialogWithNeutralButtonAndTitle(context,
						getResources().getString(R.string.dialog_title_system_app_required),
						getResources().getString(R.string.dialog_system_app_required),
						getResources().getString(R.string.ok),
						dialogClickListener);
		alertDialog.show();
	}

	private void showErrorMessage(String message, String title) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(message);
		builder.setTitle(title);
		builder.setCancelable(true);
		builder.setPositiveButton(getResources().getString(R.string.button_ok),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						cancelEntry();
						dialog.dismiss();
					}
				}
		);
		AlertDialog alert = builder.create();
		alert.show();
	}

	/**
	 * Show the license text retrieved from the server.
	 *
	 * @param licenseText Message text to be shown as the license.
	 * @param title   Title of the license.
	 */
	private void showAgreement(final String licenseText, final String title) {
		AuthenticationActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				final Dialog dialog = new Dialog(context, R.style.Dialog);
				dialog.setContentView(R.layout.dialog_license);
				dialog.setCancelable(false);

				WebView webView = (WebView) dialog.findViewById(R.id.webViewLicense);
				webView.loadDataWithBaseURL(null, licenseText, Constants.MIME_TYPE,
						Constants.ENCODING_METHOD, null);

				TextView textViewTitle = (TextView) dialog.findViewById(R.id.textViewDeviceNameTitle);
				textViewTitle.setText(title);

				Button btnAgree = (Button) dialog.findViewById(R.id.dialogButtonOK);
				Button btnCancel = (Button) dialog.findViewById(R.id.dialogButtonCancel);

				btnAgree.setOnClickListener(new OnClickListener() {
					@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
					@Override
					public void onClick(View v) {
						Preference.putBoolean(context, Constants.PreferenceFlag.IS_AGREED, true);
						dialog.dismiss();
						//load the next intent based on ownership type
						startDeviceAdminPrompt();
					}
				});

				btnCancel.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						dialog.dismiss();
						CommonUtils.clearClientCredentials(context);
						cancelEntry();
					}
				});

				dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
					@Override
					public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
						if (keyCode == KeyEvent.KEYCODE_SEARCH &&
								event.getRepeatCount() == Constants.DEFAULT_REPEAT_COUNT) {
							return true;
						} else if (keyCode == KeyEvent.KEYCODE_BACK &&
								event.getRepeatCount() == Constants.DEFAULT_REPEAT_COUNT) {
							return true;
						}
						return false;
					}
				});

				dialog.show();
			}
		});
	}

	private void loadPinCodeActivity() {
		Intent intent = new Intent(AuthenticationActivity.this, PinCodeActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtra(Constants.USERNAME, usernameVal);
		startActivity(intent);
		finish();
	}

	private void loadRegistrationActivity() {
		Intent intent = new Intent(AuthenticationActivity.this, RegistrationActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtra(Constants.USERNAME, usernameVal);
		startActivity(intent);
		finish();
	}

	private void cancelEntry() {
		Preference.putBoolean(context, Constants.PreferenceFlag.IS_AGREED, false);
		Preference.putBoolean(context, Constants.PreferenceFlag.REGISTERED, false);
		Preference.putString(context, Constants.PreferenceFlag.IP, null);

		Intent intentIP = new Intent(AuthenticationActivity.this, SplashActivity.class);
		intentIP.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intentIP);
		finish();
	}

	/**
	 * Validation done to see if the username and password fields are properly
	 * entered.
	 */
	private void enableSubmitIfReady() {

		boolean isReady = false;

		if (etUsername.getText().toString().length() >= 1 &&
				etPassword.getText().toString().length() >= 1) {
			isReady = true;
		}

		if (isReady) {
			btnSignIn.setBackgroundResource(R.drawable.btn_orange);
			btnSignIn.setTextColor(ContextCompat.getColor(this, R.color.white));
			btnSignIn.setEnabled(true);
		} else {
			btnSignIn.setBackgroundResource(R.drawable.btn_grey);
			btnSignIn.setTextColor(ContextCompat.getColor(this, R.color.black));
			btnSignIn.setEnabled(false);
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && !isReLogin) {
			finish();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	private DialogInterface.OnClickListener senderIdFailedClickListener =
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog,
									int which) {
					btnSignIn.setBackgroundResource(R.drawable.btn_orange);
					btnSignIn.setTextColor(ContextCompat.getColor(AuthenticationActivity.this, R.color.white));
					btnSignIn.setEnabled(true);
				}
			};

	/**
	 * Shows enrollment failed error.
	 */
	private void showEnrollmentFailedErrorMessage(String message) {
		CommonDialogUtils.stopProgressDialog(progressDialog);
		final String messageDescription;
		String descriptionText = getResources().getString(
				R.string.error_enrollment_failed_detail);
		if (message != null) {
			messageDescription = descriptionText + " " + message;
		} else {
			messageDescription = descriptionText;
		}
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				CommonDialogUtils.getAlertDialogWithOneButtonAndTitle(context,
						getResources().getString(
								R.string.error_enrollment_failed),
						messageDescription,
						getResources().getString(
								R.string.button_ok),
						senderIdFailedClickListener);
			}
		});

	}

	/**
	 * Shows internal server error message for authentication.
	 */
	private void showInternalServerErrorMessage() {
		CommonDialogUtils.stopProgressDialog(progressDialog);
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				CommonDialogUtils.getAlertDialogWithOneButtonAndTitle(context,
						getResources().getString(
								R.string.title_head_connection_error),
						getResources().getString(
								R.string.error_internal_server),
						getResources().getString(
								R.string.button_ok),
						null);
			}
		});

	}

	/**
	 * Shows credentials error message for authentication.
	 */
	private void showAuthenticationError(){
		CommonDialogUtils.stopProgressDialog(progressDialog);
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				CommonDialogUtils.getAlertDialogWithOneButtonAndTitle(context,
						getResources().getString(R.string.title_head_authentication_error),
						getResources().getString(R.string.error_authentication_failed),
						getResources().getString(R.string.button_ok),
						null);
			}
		});
	}

	/**
	 * Shows common error message for authentication.
	 */
	private void showAuthCommonErrorMessage() {
		CommonDialogUtils.stopProgressDialog(progressDialog);
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				CommonDialogUtils.getAlertDialogWithOneButtonAndTitle(context,
						getResources().getString(
								R.string.title_head_authentication_error),
						getResources().getString(
								R.string.error_for_all_unknown_authentication_failures),
						getResources().getString(
								R.string.button_ok),
						null);
			}
		});

	}

	/**
	 * This method is used to retrieve consumer-key and consumer-secret.
	 */
	private void getClientCredentials() {
		String ipSaved = Constants.DEFAULT_HOST;
		String prefIP = Preference.getString(context.getApplicationContext(),
				Constants.PreferenceFlag.IP);
		if (prefIP != null) {
			ipSaved = prefIP;
		}
		String authenticationTitle;
		if (currentTenant != null) {
			authenticationTitle = String.format(
					getResources().getString(R.string.dialog_registering),
					currentTenant.getDisplayName()
			);
		} else {
			authenticationTitle = getResources().getString(R.string.dialog_authenticate);
		}
		progressDialog = ProgressDialog.show(context, authenticationTitle, getResources().
				getString(R.string.dialog_message_please_wait), true);
		if (ipSaved != null && !ipSaved.isEmpty()) {
			ServerConfig utils = new ServerConfig();
			utils.setServerIP(ipSaved);
			String applicationName = Constants.API_APPLICATION_NAME_PREFIX +
					deviceInfo.getDeviceId();
			ApiRegistrationProfile apiRegistrationProfile = new ApiRegistrationProfile();
			apiRegistrationProfile.setApplicationName(applicationName);
			apiRegistrationProfile.setIsAllowedToAllDomains(false);
			apiRegistrationProfile.setIsMappingAnExistingOAuthApp(false);
			apiRegistrationProfile.setTags(SUBSCRIBED_API);
			DynamicClientManager dynamicClientManager = new DynamicClientManager();
			try {
				if (adminAccessToken != null) {
					dynamicClientManager.getClientCredentials(adminAccessToken, utils, context,
							AuthenticationActivity.this, apiRegistrationProfile);
				} else {
					dynamicClientManager.getClientCredentials(usernameVal, passwordVal, utils, context,
							AuthenticationActivity.this, apiRegistrationProfile);
				}
				Preference.putString(context, Constants.CLIENT_NAME, applicationName);
			} catch (AndroidAgentException e) {
				String message = "Client credentials generation failed";
				Log.e(TAG, message, e);
				showEnrollmentFailedErrorMessage(message);
			}
		} else {
			String message = "There is no valid IP to contact the server";
			Log.e(TAG, message);
			showEnrollmentFailedErrorMessage(message);
		}
	}

	/**
	 * This method is used to bypass the intents based on the
	 * ownership type.
	 */
	private void loadNextActivity() {
		if (Constants.OWNERSHIP_BYOD.equalsIgnoreCase(deviceType)) {
			loadPinCodeActivity();
		} else {
			loadRegistrationActivity();
		}
	}

	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onAuthenticated(boolean status, int requestCode) {
		if (requestCode == Constants.AUTHENTICATION_REQUEST_CODE) {
			if (status &&
					org.wso2.iot.agent.proxy.utils.Constants.Authenticator.AUTHENTICATOR_IN_USE.
							equals(org.wso2.iot.agent.proxy.utils.Constants.Authenticator.
									MUTUAL_SSL_AUTHENTICATOR)) {
				if(Constants.SKIP_LICENSE){
					startDeviceAdminPrompt();
				} else {
					getLicense();
				}
			}
		}
	}


}