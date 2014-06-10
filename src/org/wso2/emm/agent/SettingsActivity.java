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

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.wso2.emm.agent.api.DeviceInfo;
import org.wso2.emm.agent.api.PhoneState;
import org.wso2.emm.agent.proxy.APIResultCallBack;
import org.wso2.emm.agent.proxy.IdentityProxy;
import org.wso2.emm.agent.proxy.ServerUtilitiesTemp;
import org.wso2.emm.agent.proxy.Token;
import org.wso2.emm.agent.proxy.TokenCallBack;
import org.wso2.emm.agent.utils.CommonDialogUtils;
import org.wso2.emm.agent.utils.CommonUtilities;
import org.wso2.emm.agent.utils.ServerUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity implements APIResultCallBack,TokenCallBack {
	
	private String TAG = SettingsActivity.class.getSimpleName();
	
	TextView ip;
	Button optionBtn;
	private String FROM_ACTIVITY = null;
	Context context;
	String senderID=null;
	DeviceInfo info = null;
	boolean accessFlag = true;
	TextView errorMessage;
	String error = "";
	AsyncTask<Void, Void, String> mSenderIDTask;
	ProgressDialog progressDialog;
	String regId;
	AlertDialog.Builder alertDialog;
	
	boolean alreadyRegisteredActivityFlag = false;
	boolean authenticationActivityFlag = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);
		context = SettingsActivity.this;
		info = new DeviceInfo(SettingsActivity.this);     
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			if(extras.containsKey(getResources().getString(R.string.intent_extra_from_activity))){
				FROM_ACTIVITY = extras.getString(getResources().getString(R.string.intent_extra_from_activity));
			}
		}
		
		// Need to move to a library
		if((info.getSdkVersion() >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) && !info.isRooted()){
        	accessFlag = true;
        }else{
        	accessFlag = false;
        }
		
        if(!(info.getSdkVersion() >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) && info.isRooted()){
        	error = getString(R.string.device_not_compatible_error);
        }else if(info.getSdkVersion() > android.os.Build.VERSION_CODES.FROYO){
        	error = getString(R.string.device_not_compatible_error_os);
        }else if(info.isRooted()){
        	error = getString(R.string.device_not_compatible_error_root);
        }
        
        errorMessage = (TextView) findViewById(R.id.textView1);
		errorMessage.setText(error);
		
		
			
		ip = (TextView)findViewById(R.id.editText1);
		SharedPreferences mainPref = context.getSharedPreferences(
				getResources().getString(R.string.shared_pref_package), Context.MODE_PRIVATE);
		String ipSaved = mainPref.getString(getResources().getString(R.string.shared_pref_ip), "");	
		regId = mainPref.getString(getResources().getString(R.string.shared_pref_regId), "");
		String clientKey = mainPref.getString(getResources().getString(R.string.shared_pref_client_id), "");
		String clientSecret = mainPref.getString(getResources().getString(R.string.shared_pref_client_secret), "");
		if(ipSaved != null && ipSaved != ""){
			CommonUtilities.setSERVER_URL(ipSaved);
		}
		if(!clientKey.equals("") && !clientSecret.equals("")){
			CommonUtilities.CLIENT_ID=clientKey;
			CommonUtilities.CLIENT_SECRET=clientSecret;
		}
		
		
		
		
		
		try {
			if (FROM_ACTIVITY == null) {
				IdentityProxy.getInstance().getToken(this.getApplicationContext(),SettingsActivity.this,CommonUtilities.CLIENT_ID,CommonUtilities.CLIENT_SECRET);
			}

		} catch (TimeoutException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		if(ipSaved != null && ipSaved != ""){
			ip.setText(ipSaved);
			CommonUtilities.setSERVER_URL(ipSaved);
			Intent intent = new Intent(SettingsActivity.this,AuthenticationActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);	
		}else{
			ip.setText(CommonUtilities.SERVER_IP);
		}
		optionBtn = (Button) findViewById(R.id.button1);	
		
		if(!accessFlag){
			optionBtn.setVisibility(View.GONE);
			ip.setVisibility(View.GONE);
			errorMessage.setVisibility(View.VISIBLE);	
			showAlert(error, getResources().getString(R.string.error_authorization_failed));
		}else{
			optionBtn.setVisibility(View.VISIBLE);
			ip.setVisibility(View.VISIBLE);
			errorMessage.setVisibility(View.GONE);	
		}
		
		optionBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
				AlertDialog.Builder builder = new AlertDialog.Builder(
						SettingsActivity.this);
				builder.setMessage(
						getResources().getString(R.string.dialog_init_confirmation) + " "
								+ ip.getText().toString() + " "
								+ getResources().getString(R.string.dialog_init_end_general))
						.setPositiveButton(getResources().getString(R.string.info_label_rooted_answer_yes), dialogClickListener)
						.setNegativeButton(getResources().getString(R.string.info_label_rooted_answer_no), dialogClickListener).show();
			}
		});
	}

	/**
	 * Checks whether device is registered or NOT.
	 * 
	 */
	private void isRegistered() {
		Log.e("isReg", "isReg");
		Map<String, String> requestParams = new HashMap<String, String>();
		requestParams.put("regid", regId);
		Log.e("regID", regId);

		// Check network connection availability before calling the API.
		if (PhoneState.isNetworkAvailable(context)) {
			// Call isRegistered API.
			ServerUtils.callSecuredAPI(SettingsActivity.this,
					CommonUtilities.IS_REGISTERED_ENDPOINT,
					CommonUtilities.POST_METHOD, requestParams,
					SettingsActivity.this,
					CommonUtilities.IS_REGISTERED_REQUEST_CODE);
		} else {
			CommonDialogUtils.stopProgressDialog(progressDialog);
			CommonDialogUtils
					.showNetworkUnavailableMessage(SettingsActivity.this);
		}

	}
	
	DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch (which) {
			case DialogInterface.BUTTON_POSITIVE:
				if (!ip.getText().toString().trim().equals("")) {
					CommonUtilities.setSERVER_URL(ip.getText().toString()
							.trim());

					SharedPreferences mainPref = context.getSharedPreferences(
							getResources().getString(
									R.string.shared_pref_package),
							Context.MODE_PRIVATE);
					Editor editor = mainPref.edit();
					editor.putString(
							getResources().getString(R.string.shared_pref_ip),
							ip.getText().toString().trim());
					editor.commit();

					Intent intent = new Intent(SettingsActivity.this,
							AuthenticationActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					startActivity(intent);

				} else {
					Toast.makeText(
							context,
							getResources()
									.getString(
											R.string.toast_message_enter_server_address),
							Toast.LENGTH_LONG).show();
				}
				break;

			case DialogInterface.BUTTON_NEGATIVE:
				dialog.dismiss();
				break;
			}
		}
	};
	
	@Override
	public void onBackPressed() {
		Intent i = new Intent();
		i.setAction(Intent.ACTION_MAIN);
		i.addCategory(Intent.CATEGORY_HOME);
		this.startActivity(i);
		// finish();
		super.onBackPressed();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && FROM_ACTIVITY != null && FROM_ACTIVITY.equals(AlreadyRegisteredActivity.class.getSimpleName())) {
			Intent i = new Intent();
	    	i.setAction(Intent.ACTION_MAIN);
	    	i.addCategory(Intent.CATEGORY_HOME);
	    	this.startActivity(i);
	    	this.finish();
    		return true;
	    }else if (keyCode == KeyEvent.KEYCODE_BACK && FROM_ACTIVITY != null && FROM_ACTIVITY.equals(AuthenticationActivity.class.getSimpleName())) {
	    	int pid = android.os.Process.myPid(); 
	    	android.os.Process.killProcess(pid); 
    		return true;
	    }else if (keyCode == KeyEvent.KEYCODE_BACK) {
	    	Intent i = new Intent();
	    	i.setAction(Intent.ACTION_MAIN);
	    	i.addCategory(Intent.CATEGORY_HOME);
	    	this.startActivity(i);
	    	this.finish();
	        return true;
	    }
	    return super.onKeyDown(keyCode, event);
	}
	
	public void showAlert(String message, String title){
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message);
        builder.setTitle(title);
        builder.setCancelable(true);
        builder.setPositiveButton(getResources().getString(R.string.button_ok),
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                if(mSenderIDTask!=null){
                	mSenderIDTask.cancel(true);
                }
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.settings, menu);
		return true;
	}

	public void onReceiveAPIResult(Map<String, String> result, int requestCode) {
		String responseStatus = CommonUtilities.EMPTY_STRING;
		if (result != null) {
			responseStatus = result.get(CommonUtilities.STATUS_KEY);

			if (responseStatus.equals(CommonUtilities.REQUEST_SUCCESSFUL)
					&& requestCode == CommonUtilities.IS_REGISTERED_REQUEST_CODE) {
				Intent intent = null;
				if (progressDialog != null) {
					progressDialog.dismiss();
				}
				intent = new Intent(SettingsActivity.this,
						AlreadyRegisteredActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);

			} else if (responseStatus
					.equals(CommonUtilities.INTERNAL_SERVER_ERROR)) {
				Log.e(TAG, "The value of status is null in onAPIAccessRecive()");

				String isRegistered =
				                      CommonUtilities.getPref(context,
				                                              context.getResources()
				                                                     .getString(R.string.shared_pref_registered));
				if (isRegistered.equals("1")) {
					Intent intent = null;
					intent = new Intent(SettingsActivity.this, AlreadyRegisteredActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					startActivity(intent);
				} else {
    				alertDialog = CommonDialogUtils
    						.getAlertDialogWithOneButtonAndTitle(
    								context,
    								getResources().getString(
    										R.string.title_head_connection_error),
    								getResources().getString(
    										R.string.error_internal_server),
    								getResources().getString(R.string.button_ok),
    								null);
    				Log.e("null",alertDialog.getClass().getPackage().toString());
    				alertDialog.show();
				}
				//ServerUtils.clearAppData(context);
			} else {
				Log.e(TAG, "The value of status is : " + responseStatus);
				ServerUtils.clearAppData(context);
				
				alertDialog = CommonDialogUtils
						.getAlertDialogWithOneButtonAndTitle(
								context,
								getResources().getString(
										R.string.title_head_registration_error),
								getResources().getString(
										R.string.error_internal_server),
								getResources().getString(R.string.button_ok),
								null);
				alertDialog.show();
			}
		} else {
			Log.e(TAG, "The result is null in onReceiveAPIResult()");
			ServerUtils.clearAppData(context);
			
			alertDialog = CommonDialogUtils
					.getAlertDialogWithOneButtonAndTitle(
							context,
							getResources().getString(
									R.string.title_head_registration_error),
							getResources().getString(
									R.string.error_for_all_unknown_registration_failures),
							getResources().getString(R.string.button_ok),
							null);
			alertDialog.show();
		}
	}

	@Override
	public void onReceiveTokenResult(Token token, String status) {
		if (token != null) {
			if (regId != null && !regId.equals("")) {
				// Check registration.
				isRegistered();

				progressDialog =
				                 ProgressDialog.show(SettingsActivity.this,
				                                     getResources().getString(R.string.dialog_sender_id),
				                                     getResources().getString(R.string.dialog_please_wait),
				                                     true);
			}
		}
	}

}
