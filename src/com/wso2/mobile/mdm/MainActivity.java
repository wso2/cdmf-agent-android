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
package com.wso2.mobile.mdm;
import com.google.android.gcm.GCMRegistrar;

import com.wso2.mobile.mdm.services.WSO2DeviceAdminReceiver;
import com.wso2.mobile.mdm.utils.CommonUtilities;
import com.wso2.mobile.mdm.utils.ServerUtilities;
import com.wso2.mobile.mdm.R;

import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.app.ProgressDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
	 String regId = "";
	 String email = "";
	 TextView mDisplay;
	 Context context;
	 boolean regState = false;
	 boolean successFlag = false;
	 private final int TAG_BTN_UNREGISTER = 0;
	 private final int TAG_BTN_OPTIONS = 1;
	 Button btnEnroll = null;
	 RelativeLayout btnLayout = null;
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
			if(extras.containsKey(getResources().getString(R.string.intent_extra_regid))){
				regId = extras.getString(getResources().getString(R.string.intent_extra_regid));
			}
			
			if(extras.containsKey(getResources().getString(R.string.intent_extra_email))){
				email = extras.getString(getResources().getString(R.string.intent_extra_email));
			}
		}
		
		if (regId.equals("") || regId == null) {
            GCMRegistrar.register(this, CommonUtilities.SENDER_ID);
            
        } 
        if (GCMRegistrar.isRegisteredOnServer(this)) {
        	regId = GCMRegistrar.getRegistrationId(this);
        }
		
		SharedPreferences mainPref = this.getSharedPreferences( getResources().getString(R.string.shared_pref_package), Context.MODE_PRIVATE);
		Editor editor = mainPref.edit();
		editor.putString(getResources().getString(R.string.shared_pref_username), email);
		editor.commit();
		
		//Enroll automatically
		final Context context = MainActivity.this;
		mRegisterTask = new AsyncTask<Void, Void, String>() {
	
	        @Override
	        protected String doInBackground(Void... params) {
	          //  boolean registered = ServerUtilities.register(context, regId);
	        //	ServerUtilities.register(context, regId);
	        	try{
	        		regState = ServerUtilities.register(regId, context);
	        	}catch(Exception e){
	        		e.printStackTrace();
	        	}
	        	return null;
	        }
	
	        ProgressDialog progressDialog;
	        //declare other objects as per your need
	        @Override
	        protected void onPreExecute()
	        {
	            progressDialog= ProgressDialog.show(MainActivity.this, getResources().getString(R.string.dialog_enrolling),getResources().getString(R.string.dialog_please_wait), true);
	
	            //do initialization of required objects objects here                
	        }; 
	        @Override
	        protected void onPostExecute(String result) {
	        	if (progressDialog!=null && progressDialog.isShowing()){
            		progressDialog.dismiss();
                }
	        	if(regState){
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
	        	//}else{
	        	//	btnLayout.setVisibility(View.VISIBLE);
	        		//progressDialog.dismiss();
	        	//}
	            mRegisterTask = null;
	           
	        }
	
	    };
	    mRegisterTask.execute(null, null, null);
		        
		
		btnEnroll = (Button)findViewById(R.id.btnEnroll);
		btnLayout = (RelativeLayout)findViewById(R.id.enrollPanel);
		//ImageView optionBtn = (ImageView) findViewById(R.id.option_button);	
		
		btnEnroll.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				mRegisterTask = new AsyncTask<Void, Void, String>() {
					
			        @Override
			        protected String doInBackground(Void... params) {
			          //  boolean registered = ServerUtilities.register(context, regId);
			        //	ServerUtilities.register(context, regId);
			        	try{
			        		regState = ServerUtilities.register(regId, context);
			        	}catch(Exception e){
			        		e.printStackTrace();
			        	}
			        	
			        	return null;
			        }
			
			        ProgressDialog progressDialog;
			        //declare other objects as per your need
			        @Override
			        protected void onPreExecute()
			        {
			            progressDialog= ProgressDialog.show(MainActivity.this, getResources().getString(R.string.dialog_enrolling),getResources().getString(R.string.dialog_please_wait), true);
			
			            //do initialization of required objects objects here                
			        }; 
			        @Override
			        protected void onPostExecute(String result) {
			        	if (progressDialog!=null && progressDialog.isShowing()){
		            		progressDialog.dismiss();
		                }
			        	if(regState){
			            	Intent intent = new Intent(MainActivity.this,AlreadyRegisteredActivity.class);
			            	intent.putExtra(getResources().getString(R.string.intent_extra_regid), regId);
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
			        	//}else{
			        		//btnLayout.setVisibility(View.VISIBLE);
			        		//progressDialog.dismiss();
			        	//}
			            mRegisterTask = null;
			           
			        }
			
			    };
			    mRegisterTask.execute(null, null, null);
			}
		});
		
		/*btnEnroll.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
				final Context context = MainActivity.this;
				mRegisterTask = new AsyncTask<Void, Void, Void>() {

                    @Override
                    protected Void doInBackground(Void... params) {
                      //  boolean registered = ServerUtilities.register(context, regId);
                    //	ServerUtilities.register(context, regId);
                    	ServerUtilities.register(regId, context);
                    	
                    	return null;
                    }

                    ProgressDialog progressDialog;
                    //declare other objects as per your need
                    @Override
                    protected void onPreExecute()
                    {
                        progressDialog= ProgressDialog.show(MainActivity.this, "Enrolling Device","Please wait", true);

                        //do initialization of required objects objects here                
                    }; 
                    @Override
                    protected void onPostExecute(Void result) {
                		//Direct to register successful class
	                    	Intent intent = new Intent(MainActivity.this,RegisterSuccessful.class);
	                    	intent.putExtra("regid", regId);
	                    	intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
	                    	startActivity(intent);
	                    	finish();
                        mRegisterTask = null;
                        //progressDialog.dismiss();
                    }

                };
                mRegisterTask.execute(null, null, null);
				
			}
		});*/
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

   
}
