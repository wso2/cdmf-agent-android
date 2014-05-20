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
import org.wso2.emm.agent.services.WSO2DeviceAdminReceiver;
import org.wso2.emm.agent.utils.CommonDialogUtils;
import org.wso2.emm.agent.utils.CommonUtilities;
import org.wso2.emm.agent.utils.ServerUtils;
import org.wso2.mobile.idp.proxy.APIResultCallBack;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class MainActivity extends Activity implements APIResultCallBack {
	 String regId = "";
	 String username = "";
	 TextView mDisplay;
	 Context context;
	 boolean regState = false;
	 boolean successFlag = false;
	 private final int TAG_BTN_UNREGISTER = 0;
	 private final int TAG_BTN_OPTIONS = 1;
	 Button btnEnroll = null;
	 RelativeLayout btnLayout = null;
	 ProgressDialog progressDialog;
	 AsyncTask<Void, Void, String> mRegisterTask;
	 
	static final int ACTIVATION_REQUEST = 47; // identifies our request id
	DevicePolicyManager devicePolicyManager;
	ComponentName demoDeviceAdmin;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_main);
        setContentView(R.layout.activity_main);
        
        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
		demoDeviceAdmin = new ComponentName(this, WSO2DeviceAdminReceiver.class);
		context = this;
		
		
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			/*if(extras.containsKey(getResources().getString(R.string.intent_extra_regid))){
				regId = extras.getString(getResources().getString(R.string.intent_extra_regid));
			}*/
			
			if(extras.containsKey(getResources().getString(R.string.intent_extra_username))){
				username = extras.getString(getResources().getString(R.string.intent_extra_username));
			}
		}

		SharedPreferences mainPref = this.getSharedPreferences( getResources().getString(R.string.shared_pref_package), Context.MODE_PRIVATE);
		Editor editor = mainPref.edit();
		regId = mainPref.getString(getResources().getString(R.string.shared_pref_regId), "");
		editor.putString(getResources().getString(R.string.shared_pref_username), username);
		editor.commit();
		
		//Enroll automatically
		final Context context = MainActivity.this;
		
		registrateDevice();
		        
		
		btnEnroll = (Button)findViewById(R.id.btnEnroll);
		btnLayout = (RelativeLayout)findViewById(R.id.enrollPanel);
		//ImageView optionBtn = (ImageView) findViewById(R.id.option_button);	
		
		btnEnroll.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				//registrateDevice();
 
			}
		});
    }
		
		private void registrateDevice() {
			progressDialog= ProgressDialog.show(MainActivity.this, getResources().getString(R.string.dialog_enrolling),getResources().getString(R.string.dialog_please_wait), true);
			DeviceInfo deviceInfo = new DeviceInfo(MainActivity.this);
			JSONObject jsObject = new JSONObject();
			String osVersion = "";
			SharedPreferences mainPref = MainActivity.this.getSharedPreferences(
					MainActivity.this.getResources().getString(
							R.string.shared_pref_package),
					Context.MODE_PRIVATE);
			String type = mainPref.getString(MainActivity.this.getResources()
					.getString(R.string.shared_pref_reg_type), "");
			
			osVersion = deviceInfo.getOsVersion();
		try {
			jsObject.put("device", deviceInfo.getDevice());
			jsObject.put("imei", deviceInfo.getDeviceId());
			jsObject.put("imsi", deviceInfo.getIMSINumber());
			jsObject.put("model", deviceInfo.getDeviceModel());

			Map<String, String> requestParams = new HashMap<String, String>();
			requestParams.put("regid", regId);
			requestParams.put("properties", jsObject.toString());
			requestParams.put("osversion", osVersion);
			requestParams.put("username", username);
			requestParams.put("platform", "Android");
			requestParams.put("vendor", deviceInfo.getDeviceManufacturer());
			requestParams.put("type", type);
			requestParams.put("mac", deviceInfo.getMACAddress());

			// Check network connection availability before calling the API.
			if (PhoneState.isNetworkAvailable(context)) {
				// Call device registration API.
				ServerUtils.callSecuredAPI(CommonUtilities.REGISTER_ENDPOINT,
						CommonUtilities.POST_METHOD, requestParams,
						MainActivity.this, CommonUtilities.REGISTER_REQUEST_CODE);
			} else {
				CommonDialogUtils
						.showNetworkUnavailableMessage(MainActivity.this);
			}
			

		} catch (JSONException e) {
			e.printStackTrace();
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
   	    }
   	    else if (keyCode == KeyEvent.KEYCODE_HOME) {
   	    	/*Intent i = new Intent();
   	    	i.setAction(Intent.ACTION_MAIN);
   	    	i.addCategory(Intent.CATEGORY_HOME);
   	    	this.startActivity(i);*/
   	    	finish();
   	        return true;
   	    }
   	    return super.onKeyDown(keyCode, event);
   	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	//MenuInflater inflater = getMenuInflater();
    	//inflater.inflate(R.menu.options_menu, menu);
        return true;
    }
    public boolean onOptionsItemSelected(MenuItem item) {
    	/*switch (item.getItemId()) {
    	case R.id.info:
    		Intent intent = new Intent(MainActivity.this,DisplayDeviceInfo.class);
    		startActivity(intent);
    		return true;
    	default:*/
    		return super.onOptionsItemSelected(item);
    	//}
    }

	@Override
	public void onReceiveAPIResult(Map<String, String> result, int requestCode) {
		if (progressDialog!=null && progressDialog.isShowing()){
    		progressDialog.dismiss();
        }
		String responseStatus = "";
		if (result != null) {
			responseStatus = result.get(CommonUtilities.STATUS_KEY);
			
			if (requestCode == CommonUtilities.REGISTER_REQUEST_CODE) {
				if (responseStatus.equals(CommonUtilities.REGISTERATION_SUCCESSFUL)) {
					Intent intent = new Intent(MainActivity.this,AlreadyRegisteredActivity.class);
		        	intent.putExtra(getResources().getString(R.string.intent_extra_regid), regId);
		        	intent.putExtra(getResources().getString(R.string.intent_extra_fresh_reg_flag), true);
		        	intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		        	startActivity(intent);
		        	//finish();
		    	}else{
		    		Intent intent = new Intent(MainActivity.this,AuthenticationErrorActivity.class);
		        	intent.putExtra(getResources().getString(R.string.intent_extra_regid), regId);
		        	intent.putExtra(getResources().getString(R.string.intent_extra_from_activity), MainActivity.class.getSimpleName());
		        	intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		        	startActivity(intent);
		        	//finish();
		    	}
			} else {
				// TODO Implementation
			}
		} else {
			// TODO Implementation
		}
		
		
	} 

   
}
