/*
 ~ Copyright (c) 2014, WSO2 Inc. (http://wso2.com/) All Rights Reserved.
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~      http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
 */
package org.wso2.emm.agent;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.emm.agent.api.DeviceInfo;
import org.wso2.emm.agent.api.PhoneState;
import org.wso2.emm.agent.services.AlarmReceiver;
import org.wso2.emm.agent.utils.CommonDialogUtils;
import org.wso2.emm.agent.utils.CommonUtilities;
import org.wso2.emm.agent.utils.ServerUtils;
import org.wso2.mobile.idp.proxy.APIAccessCallBack;
import org.wso2.mobile.idp.proxy.APIResultCallBack;
import org.wso2.mobile.idp.proxy.IdentityProxy;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings.Secure;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.google.android.gcm.GCMRegistrar;

public class AuthenticationActivity extends SherlockActivity implements APIAccessCallBack, APIResultCallBack {
	String regId = "";
	Button authenticate;
	EditText username;
	EditText txtDomain;
	EditText password;
	// TextView txtLoadingEULA;
	RadioButton radioBYOD, radioCOPE;
	String deviceType;
	Activity activity;
	Context context;
	String isAgreed = "";
	String eula = "";
	ProgressDialog progressDialog;
	AlertDialog.Builder alertDialog;
	private final int TAG_BTN_AUTHENTICATE = 0;
	private final int TAG_BTN_OPTIONS = 1;

	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_authentication);
		getSupportActionBar().setDisplayShowCustomEnabled(true);
		getSupportActionBar().setCustomView(R.layout.custom_sherlock_bar);
		View homeIcon = findViewById(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ? android.R.id.home
				: R.id.abs__home);
		((View) homeIcon.getParent()).setVisibility(View.GONE);

		this.activity = AuthenticationActivity.this;
		this.context = AuthenticationActivity.this;
		deviceType = getResources().getString(R.string.device_enroll_type_byod);
		// txtLoadingEULA = (TextView)findViewById(R.id.txtLoadingEULA);
		txtDomain = (EditText) findViewById(R.id.txtDomain);
		username = (EditText) findViewById(R.id.editText1);
		password = (EditText) findViewById(R.id.editText2);
		radioBYOD = (RadioButton) findViewById(R.id.radioBYOD);
		radioCOPE = (RadioButton) findViewById(R.id.radioCOPE);
		txtDomain.setFocusable(true);
		txtDomain.requestFocus();
		if (CommonUtilities.DEBUG_MODE_ENABLED) {
			Log.v("check first username", username.getText().toString());
			Log.v("check first password", password.getText().toString());
		}
		authenticate = (Button) findViewById(R.id.btnRegister);
		authenticate.setEnabled(false);
		authenticate.setTag(TAG_BTN_AUTHENTICATE);
		authenticate.setOnClickListener(onClickListener_BUTTON_CLICKED);
		authenticate.setBackground(getResources().getDrawable(R.drawable.btn_grey));
		authenticate.setTextColor(getResources().getColor(R.color.black));

		DeviceInfo deviceInfo = new DeviceInfo(AuthenticationActivity.this);

		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			if (extras.containsKey(getResources().getString(R.string.intent_extra_regid))) {
				regId = extras.getString(getResources().getString(R.string.intent_extra_regid));
			}
		}
		if (regId == null || regId.equals("")) {
			regId = GCMRegistrar.getRegistrationId(this);
		}

		String regIden = CommonUtilities.getPref(context, context.getResources().getString(R.string.shared_pref_regId));
		if (!regIden.equals("")) {
			regId = regIden;
		}

		username.addTextChangedListener(new TextWatcher() {
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

		password.addTextChangedListener(new TextWatcher() {
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

	}

	@Override
	protected void onResume() {
		super.onResume();
		// Here we need to call isRegistered function
	}

	OnClickListener onClickListener_BUTTON_CLICKED = new OnClickListener() {

		@Override
		public void onClick(View view) {

			int iTag = (Integer) view.getTag();

			switch (iTag) {

			case TAG_BTN_AUTHENTICATE:
				if (username.getText() != null && !username.getText().toString().trim().equals("")
						&& password.getText() != null && !password.getText().toString().trim().equals("")) {
					if (radioBYOD.isChecked()) {
						deviceType = getResources().getString(R.string.device_enroll_type_byod);
					} else {
						deviceType = getResources().getString(R.string.device_enroll_type_cope);
					}
					AlertDialog.Builder builder = new AlertDialog.Builder(AuthenticationActivity.this);
					builder.setMessage(
							getResources().getString(R.string.dialog_init_middle) + " " + deviceType + " "
									+ getResources().getString(R.string.dialog_init_end))
							.setNegativeButton(getResources().getString(R.string.info_label_rooted_answer_yes),
									dialogClickListener)
							.setPositiveButton(getResources().getString(R.string.info_label_rooted_answer_no),
									dialogClickListener).show();
				} else {
					if (username.getText() != null && !username.getText().toString().trim().equals("")) {
						Toast.makeText(context, getResources().getString(R.string.toast_error_password),
								Toast.LENGTH_LONG).show();
					} else {
						Toast.makeText(context, getResources().getString(R.string.toast_error_username),
								Toast.LENGTH_LONG).show();
					}
				}
				break;

			case TAG_BTN_OPTIONS:
				// startOptionActivity();
				break;
			default:
				break;
			}

		}
	};

	public void showErrorMessage(String message, String title) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(message);
		builder.setTitle(title);
		builder.setCancelable(true);
		builder.setPositiveButton(getResources().getString(R.string.button_ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				cancelEntry();
				dialog.dismiss();
			}
		});
		AlertDialog alert = builder.create();
		alert.show();
	}

	public void showAlert(String message, String title) {
		final Dialog dialog = new Dialog(context);
		dialog.setContentView(R.layout.custom_terms_popup);
		dialog.setTitle(CommonUtilities.EULA_TITLE);
		dialog.setCancelable(false);

		WebView web = (WebView) dialog.findViewById(R.id.webview);
		String html = "<html><body>" + message + "</body></html>";
		String mime = "text/html";
		String encoding = "utf-8";
		web.getSettings().setJavaScriptEnabled(true);
		web.loadDataWithBaseURL(null, html, mime, encoding, null);

		Button dialogButton = (Button) dialog.findViewById(R.id.dialogButtonOK);
		Button cancelButton = (Button) dialog.findViewById(R.id.dialogButtonCancel);

		dialogButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				SharedPreferences mainPref = AuthenticationActivity.this.getSharedPreferences(
						getResources().getString(R.string.shared_pref_package), Context.MODE_PRIVATE);
				Editor editor = mainPref.edit();
				editor.putString(getResources().getString(R.string.shared_pref_isagreed), "1");
				editor.commit();
				dialog.dismiss();
				loadPincodeAcitvity();
			}
		});

		cancelButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				cancelEntry();
				dialog.dismiss();
			}
		});

		dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {

			@Override
			public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
				if (keyCode == KeyEvent.KEYCODE_SEARCH && event.getRepeatCount() == 0) {
					return true;
				} else if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
					return true;
				}
				return false;
			}
		});

		dialog.show();
	}

	public void cancelEntry() {
		SharedPreferences mainPref = context.getSharedPreferences(getResources()
				.getString(R.string.shared_pref_package), Context.MODE_PRIVATE);
		Editor editor = mainPref.edit();
		editor.putString(getResources().getString(R.string.shared_pref_policy), "");
		editor.putString(getResources().getString(R.string.shared_pref_isagreed), "0");
		editor.putString(getResources().getString(R.string.shared_pref_registered), "0");
		editor.putString(getResources().getString(R.string.shared_pref_ip), "");
		editor.commit();
		// finish();

		Intent intentIP = new Intent(AuthenticationActivity.this, SettingsActivity.class);
		intentIP.putExtra(getResources().getString(R.string.intent_extra_from_activity),
				AuthenticationActivity.class.getSimpleName());
		intentIP.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intentIP);

	}

	DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch (which) {
			case DialogInterface.BUTTON_POSITIVE:
				dialog.dismiss();
				break;

			case DialogInterface.BUTTON_NEGATIVE:
				dialog.dismiss();
				startAuthentication();
				break;
			}
		}
	};

	public void showAlertSingle(String message, String title) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(message);
		builder.setTitle(title);
		builder.setCancelable(true);
		builder.setPositiveButton(getResources().getString(R.string.button_ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				// cancelEntry();
				dialog.cancel();
			}
		});
		/*
		 * builder1.setNegativeButton("No", new
		 * DialogInterface.OnClickListener() { public void
		 * onClick(DialogInterface dialog, int id) { dialog.cancel(); } });
		 */

		AlertDialog alert = builder.create();
		alert.show();
	}

	public void showAuthErrorMessage(String message, String title) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(message);
		builder.setTitle(title);
		builder.setCancelable(true);
		builder.setPositiveButton(getResources().getString(R.string.button_ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
			}
		});
		/*
		 * builder1.setNegativeButton("No", new
		 * DialogInterface.OnClickListener() { public void
		 * onClick(DialogInterface dialog, int id) { dialog.cancel(); } });
		 */

		AlertDialog alert = builder.create();
		alert.show();
	}

	/**
	 * Start Authentication.
	 */
	public void startAuthentication() {
		final Context context = AuthenticationActivity.this;

		SharedPreferences mainPref = context.getSharedPreferences(getResources()
				.getString(R.string.shared_pref_package), Context.MODE_PRIVATE);
		Editor editor = mainPref.edit();
		editor.putString(getResources().getString(R.string.shared_pref_reg_type), deviceType);
		editor.commit();

		// Check network connection availability before calling the API.
		if (PhoneState.isNetworkAvailable(context)) {
			initializeIDPLib();
		} else {
			CommonDialogUtils.showNetworkUnavailableMessage(AuthenticationActivity.this);
		}

	}

	/**
	 * Initialize the Android IDP sdk by passing user credentials,client ID and
	 * client secret.
	 */
	private void initializeIDPLib() {

		if (txtDomain.getText() != null && !txtDomain.getText().toString().trim().equals("")) {
			IdentityProxy.getInstance()
					.init(CommonUtilities.CLIENT_ID, CommonUtilities.CLIENT_SECRET,
							username.getText().toString().trim() + "@" + txtDomain.getText().toString().trim(),
							password.getText().toString().trim(), CommonUtilities.SERVER_OAUTH_URL,
							AuthenticationActivity.this);

		} else {
			IdentityProxy.getInstance().init(CommonUtilities.CLIENT_ID, CommonUtilities.CLIENT_SECRET,
					username.getText().toString().trim(), password.getText().toString().trim(),
					CommonUtilities.SERVER_OAUTH_URL, AuthenticationActivity.this);
		}

		progressDialog = ProgressDialog.show(AuthenticationActivity.this,
				getResources().getString(R.string.dialog_authenticate),
				getResources().getString(R.string.dialog_please_wait), true);
	}

	/**
	 * Starts Option Activity.
	 * 
	 */
	public void startOptionActivity() {
		Intent intent = new Intent(AuthenticationActivity.this, DisplayDeviceInfoActivity.class);
		intent.putExtra(getResources().getString(R.string.intent_extra_from_activity),
				AuthenticationActivity.class.getSimpleName());
		intent.putExtra(getResources().getString(R.string.intent_extra_regid), regId);
		startActivity(intent);
	}

	@SuppressLint("NewApi")
	public void enableSubmitIfReady() {

		boolean isReady = false;

		if (username.getText().toString().length() >= 1 && password.getText().toString().length() >= 1) {
			isReady = true;
		}

		if (isReady) {
			authenticate.setBackground(getResources().getDrawable(R.drawable.btn_orange));
			authenticate.setTextColor(getResources().getColor(R.color.white));
			authenticate.setEnabled(true);
		} else {
			authenticate.setBackground(getResources().getDrawable(R.drawable.btn_grey));
			authenticate.setTextColor(getResources().getColor(R.color.black));
			authenticate.setEnabled(false);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// TODO Auto-generated method stub
		getSupportMenuInflater().inflate(R.menu.auth_sherlock_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.ip_setting:
			SharedPreferences mainPref = AuthenticationActivity.this.getSharedPreferences(
					getResources().getString(R.string.shared_pref_package), Context.MODE_PRIVATE);
			Editor editor = mainPref.edit();
			editor.putString(getResources().getString(R.string.shared_pref_ip), "");
			editor.commit();

			Intent intentIP = new Intent(AuthenticationActivity.this, SettingsActivity.class);
			intentIP.putExtra(getResources().getString(R.string.intent_extra_from_activity),
					AuthenticationActivity.class.getSimpleName());
			intentIP.putExtra(getResources().getString(R.string.intent_extra_regid), regId);
			startActivity(intentIP);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			Intent i = new Intent();
			i.setAction(Intent.ACTION_MAIN);
			i.addCategory(Intent.CATEGORY_HOME);
			this.startActivity(i);
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_HOME) {
			this.finish();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	DialogInterface.OnClickListener senderIdFailedClickListener = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			username.setText(CommonUtilities.EMPTY_STRING);
			password.setText(CommonUtilities.EMPTY_STRING);
			txtDomain.setText(CommonUtilities.EMPTY_STRING);
			authenticate.setEnabled(false);
			authenticate.setBackground(getResources().getDrawable(R.drawable.btn_grey));
			authenticate.setTextColor(getResources().getColor(R.color.black));
		}
	};

	@Override
	public void onReceiveAPIResult(Map<String, String> result, int requestCode) {
		if (requestCode == CommonUtilities.SENDER_ID_REQUEST_CODE) {
			manipulateSenderIdResponse(result);
		} else if (requestCode == CommonUtilities.LICENSE_REQUEST_CODE) {
			manipulateLicenseResponse(result);
		}
	}

	/**
	 * Manipulates the License agreement response.
	 * 
	 * @param result
	 *            the result of the license agreement request
	 */
	private void manipulateLicenseResponse(Map<String, String> result) {
		String responseStatus;
			CommonDialogUtils.stopProgressDialog(progressDialog);
			String licenseAgreement = "";
			
			if (result != null) {
				responseStatus = result.get(CommonUtilities.STATUS_KEY);
				if (responseStatus.equals(CommonUtilities.REQUEST_SUCCESSFUL)) {
					licenseAgreement = result.get("response");

					SharedPreferences mainPref = AuthenticationActivity.this.getSharedPreferences(getResources()
							.getString(R.string.shared_pref_package), Context.MODE_PRIVATE);
					Editor editor = mainPref.edit();
					editor.putString(getResources().getString(R.string.shared_pref_eula), licenseAgreement);
					editor.commit();

					if (licenseAgreement != null && !licenseAgreement.equals(CommonUtilities.EMPTY_STRING)) {
						showAlert(licenseAgreement, CommonUtilities.EULA_TITLE);
					} else {
						showErrorMessage(getResources().getString(R.string.error_enrollment_failed_detail),
								getResources().getString(R.string.error_enrollment_failed));
					}

				} else {
					// TODO NEED TO IMPLEMENT
				}

			} else {
				// TODO NEED TO IMPLEMENT
			}
	}

	/**
	 * Manipulates the sender ID response.
	 * 
	 * @param result
	 *            the result of the sender ID request
	 */
	private void manipulateSenderIdResponse(Map<String, String> result) {
		String responseStatus;
		JSONObject response;
		
			String senderId = "";
			String mode = "";
			long interval = 1;
			if (result != null) {
				responseStatus = result.get(CommonUtilities.STATUS_KEY);
				if (responseStatus.equals(CommonUtilities.REQUEST_SUCCESSFUL)) {
					try {
						response = new JSONObject(result.get("response"));
						senderId = response.getString("sender_id");
						mode = response.getString("notifier");
						Double intervl = Double.parseDouble(response.getString("notifierInterval"));
						interval = intervl.intValue();

					} catch (JSONException e) {
						e.printStackTrace();
					}
					if (!senderId.equals("")) {
						CommonUtilities.setSENDER_ID(senderId);
						GCMRegistrar.register(context, senderId);
					}
					SharedPreferences mainPref = context.getSharedPreferences(
							getResources().getString(R.string.shared_pref_package), Context.MODE_PRIVATE);
					Editor editor = mainPref.edit();
					editor.putString(getResources().getString(R.string.shared_pref_sender_id), senderId);
					editor.putString(getResources().getString(R.string.shared_pref_message_mode), mode);
					editor.putLong(getResources().getString(R.string.shared_pref_interval), interval);
					editor.commit();

					manageLocalPushNotification(mode, interval, editor);
					stopProgressDialog();
					getLicense();

				} else {
					stopProgressDialog();

					alertDialog = CommonDialogUtils.getAlertDialogWithOneButton(AuthenticationActivity.this,
							getResources().getString(R.string.title_init_msg_error),
							getResources().getString(R.string.button_ok), senderIdFailedClickListener);
					alertDialog.show();

				}
			}

	}

	private void manageLocalPushNotification(String mode, long interval, Editor editor) {
		if (mode.trim().toUpperCase().contains("LOCAL")) {
			CommonUtilities.LOCAL_NOTIFICATIONS_ENABLED = true;
			CommonUtilities.GCM_ENABLED = false;
			String androidID = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
			editor.putString(getResources().getString(R.string.shared_pref_regId), androidID);
			editor.commit();
			startLocalNotification(interval);
		} else if (mode.trim().toUpperCase().contains("GCM")) {
			CommonUtilities.LOCAL_NOTIFICATIONS_ENABLED = false;
			CommonUtilities.GCM_ENABLED = true;
			editor.commit();
			GCMRegistrar.register(context, CommonUtilities.SENDER_ID);

		}
	}

	/**
	 * Stops progressDialog.
	 * 
	 */
	private void stopProgressDialog() {
		if (progressDialog != null && progressDialog.isShowing()) {
			progressDialog.dismiss();
		}
	}

	/**
	 * Gets device License agreement.
	 * 
	 */
	private void getLicense() {
		SharedPreferences mainPref = context.getSharedPreferences(
				getResources().getString(R.string.shared_pref_package),
				Context.MODE_PRIVATE);
		isAgreed = mainPref.getString(
				getResources().getString(R.string.shared_pref_isagreed), "");
		String eula = mainPref.getString(
				getResources().getString(R.string.shared_pref_eula), "");
		String type = mainPref.getString(
				getResources().getString(R.string.shared_pref_reg_type), "");

		if (type.trim().equals(
				getResources().getString(R.string.device_enroll_type_byod))) {
			if (!isAgreed.equals("1")) {
				Map<String, String> requestParams = new HashMap<String, String>();
				requestParams.put("domain", txtDomain.getText().toString()
						.trim());

				// Get License
				OnCancelListener cancelListener = new OnCancelListener() {

					@Override
					public void onCancel(DialogInterface arg0) {
						showAlertSingle(
								getResources()
										.getString(
												R.string.error_enrollment_failed_detail),
								getResources().getString(
										R.string.error_enrollment_failed));
						// finish();
					}
				};

				progressDialog = CommonDialogUtils.showPrgressDialog(
						AuthenticationActivity.this,
						getResources().getString(
								R.string.dialog_license_agreement),
						getResources().getString(R.string.dialog_please_wait),
						cancelListener);
				progressDialog.show();

				// Check network connection availability before calling the API.
				if (PhoneState.isNetworkAvailable(context)) {
					// Call device license agreement API.
					ServerUtils.callSecuredAPI(
							CommonUtilities.LICENSE_ENDPOINT,
							CommonUtilities.GET_METHOD, null,
							AuthenticationActivity.this,
							CommonUtilities.LICENSE_REQUEST_CODE);
				} else {
					CommonDialogUtils
							.showNetworkUnavailableMessage(AuthenticationActivity.this);
				}

			} else {
				loadPincodeAcitvity();
			}
		} else {
			loadPincodeAcitvity();
		}

	}

	private void loadPincodeAcitvity() {
		Intent intent = new Intent(AuthenticationActivity.this, PinCodeActivity.class);
		intent.putExtra(getResources().getString(R.string.intent_extra_regid), regId);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtra(getResources().getString(R.string.intent_extra_username), username.getText().toString().trim());
		startActivity(intent);
	}

	@Override
	public void onAPIAccessRecive(String status) {

		if (status != null
				&& status.trim().equals(CommonUtilities.AUTHENTICATION_FAILED)) {
			CommonDialogUtils.stopProgressDialog(progressDialog);
			
			alertDialog = CommonDialogUtils
					.getAlertDialogWithOneButtonAndTitle(
							context,
							getResources().getString(
									R.string.title_head_authentication_error),
							getResources().getString(
									R.string.error_authentication_failed),
							getResources().getString(R.string.button_ok),
							dialogClickListener);
			alertDialog.show();
		} else {
			// Check network connection availability before calling the API.
			if (PhoneState.isNetworkAvailable(context)) {
				// Call get sender ID API.
				ServerUtils.callSecuredAPI(CommonUtilities.SENDER_ID_ENDPOINT,
						CommonUtilities.GET_METHOD, null,
						AuthenticationActivity.this,
						CommonUtilities.SENDER_ID_REQUEST_CODE);
			} else {
				CommonDialogUtils
						.showNetworkUnavailableMessage(AuthenticationActivity.this);
			}

		}
	}

	private void startLocalNotification(long duration) {
		long firstTime = SystemClock.elapsedRealtime();
		firstTime += 1 * 1000;

		Intent downloader = new Intent(context, AlarmReceiver.class);
		PendingIntent recurringDownload = PendingIntent.getBroadcast(context, 0, downloader,
				PendingIntent.FLAG_CANCEL_CURRENT);
		AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		/*
		 * alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP,
		 * updateTime.getTimeInMillis(), AlarmManager.INTERVAL_DAY,
		 * recurringDownload);
		 */

		alarms.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, firstTime, 20000 * duration, recurringDownload);

	}

}
