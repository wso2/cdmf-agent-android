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

import org.wso2.emm.agent.api.DeviceInfo;
import org.wso2.emm.agent.utils.CommonUtilities;
import org.wso2.emm.agent.utils.ServerUtilities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

import com.google.android.gcm.GCMRegistrar;


public class EntryActivity extends Activity {
	TextView mDisplay;
	AsyncTask<Void, Void, Void> mRegisterTask;
	AsyncTask<Void, Void, String> mLicenseTask;
	DeviceInfo info = null;
	String regId = "";
	boolean accessFlag = true;
	boolean state = false;
	TextView errorMessage;
	Context context;
	String error="";
	ProgressDialog progressDialog;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_entry);
		checkNotNull(CommonUtilities.SERVER_URL, "SERVER_URL");
        checkNotNull(CommonUtilities.SENDER_ID, "SENDER_ID");
        
        if(CommonUtilities.DEBUG_MODE_ENABLED){
        	Log.e("SENDER ID : ", CommonUtilities.SENDER_ID);
        }
        info = new DeviceInfo(EntryActivity.this);       
        context = EntryActivity.this;
        
		if((info.getSdkVersion() >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) && !info.isRooted()){
        	accessFlag = true;
        }else{
        	accessFlag = false;
        }
		
		if(!(info.getSdkVersion() > android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) && info.isRooted()){
        	error = getString(R.string.device_not_compatible_error);
        }else if(info.getSdkVersion() > android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1){
        	error = getString(R.string.device_not_compatible_error_os);
        }else if(info.isRooted()){
        	error = getString(R.string.device_not_compatible_error_root);
        }
		
        // Make sure the device has the proper dependencies.
        GCMRegistrar.checkDevice(this);
        // Make sure the manifest was properly set - comment out this line
        // while developing the app, then uncomment it when it's ready.
        GCMRegistrar.checkManifest(this);
    //    mDisplay = (TextView) findViewById(R.id.display);
        registerReceiver(mHandleMessageReceiver,
                new IntentFilter(CommonUtilities.DISPLAY_MESSAGE_ACTION));
        //ImageView optionBtn = (ImageView) findViewById(R.id.option_button);	
		errorMessage = (TextView) findViewById(R.id.textView1);
		errorMessage.setText(error);
		if(!accessFlag){
			errorMessage.setVisibility(View.VISIBLE);
			showAlert(error, getResources().getString(R.string.error_authorization_failed));
		}

		/*optionBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Intent intent = new Intent(Entry.this,DisplayDeviceInfo.class);
				intent.putExtra("from_activity_name", Entry.class.getSimpleName());
				startActivity(intent);
			}
		});*/
		
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			if(extras.containsKey(getResources().getString(R.string.intent_extra_regid))){
				regId = extras.getString(getResources().getString(R.string.intent_extra_regid));
			}
		}
//		if(regId == null || regId.equals("")){
//			regId = GCMRegistrar.getRegistrationId(this);
//		}
		
		SharedPreferences mainPref = context.getSharedPreferences(
    			getResources().getString(R.string.shared_pref_package), Context.MODE_PRIVATE);
		String success = mainPref.getString(getResources().getString(R.string.shared_pref_registered), "");
		if(success.trim().equals(getResources().getString(R.string.shared_pref_reg_success))){
			state = true;
		}
		
    	if(accessFlag){
        	if(state){
    			Intent intent = new Intent(EntryActivity.this,AlreadyRegisteredActivity.class);
    			intent.putExtra(getResources().getString(R.string.intent_extra_regid), regId);
    			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    			startActivity(intent);
        	}
    	}

		if(CommonUtilities.DEBUG_MODE_ENABLED){Log.v("REGIDDDDD",regId);}
        if (regId.equals("") || regId == null) {
            GCMRegistrar.register(context, CommonUtilities.SENDER_ID);
        } else {
            if (GCMRegistrar.isRegisteredOnServer(this)) {
            	if(CommonUtilities.DEBUG_MODE_ENABLED){Log.v("Check GCM is Registered Func","RegisteredOnServer");}
              //  mDisplay.append(getString(R.string.already_registered) + "\n");
            } else {
                final Context context = this;
                mRegisterTask = new AsyncTask<Void, Void, Void>() {

                    @Override
                    protected Void doInBackground(Void... params) {
                      //  boolean registered = ServerUtilities.register(context, regId);
                    	boolean registered = true; 
                    	if (!registered) {
                            GCMRegistrar.unregister(context);
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void result) {
                    	if(CommonUtilities.DEBUG_MODE_ENABLED){Log.v("REG IDDDD",regId);}
                        mRegisterTask = null;
                    }

                };
                if(accessFlag){
                	mRegisterTask.execute(null, null, null);
                }else{
                	//Toast.makeText(getApplicationContext(), getString(R.string.device_not_compatible_error), Toast.LENGTH_LONG).show();
                }
            }
        }
        final Context context = this;

        mRegisterTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
            	try{
            		state =ServerUtilities.isRegistered(regId, context);
            	}catch(Exception e){
            		e.printStackTrace();
            		//HandleNetworkError(e);
            		//Toast.makeText(getApplicationContext(), "No Connection", Toast.LENGTH_LONG).show();
            	}
                return null;
            }
            
            //declare other objects as per your need
            @Override
            protected void onPreExecute()
            {
                progressDialog= ProgressDialog.show(EntryActivity.this, getResources().getString(R.string.dialog_checking_reg),getResources().getString(R.string.dialog_please_wait), true);
                progressDialog.setCancelable(true);
                progressDialog.setOnCancelListener(cancelListener);
                //do initialization of required objects objects here                
            };    
            
            OnCancelListener cancelListener=new OnCancelListener(){
                @Override
                public void onCancel(DialogInterface arg0){
                	showAlert(getResources().getString(R.string.error_connect_to_server), getResources().getString(R.string.error_heading_connection));
                }
            };

            @Override
            protected void onPostExecute(Void result) {
            	if (progressDialog!=null && progressDialog.isShowing()){
            		progressDialog.dismiss();
                }
            	SharedPreferences mainPref = context.getSharedPreferences(
            			getResources().getString(R.string.shared_pref_package), Context.MODE_PRIVATE);
    			String success = mainPref.getString(getResources().getString(R.string.shared_pref_registered), "");
    			if(success.trim().equals(getResources().getString(R.string.shared_pref_reg_success))){
    				state = true;
    			}
    			
            	if(accessFlag){
	            	if(state){
	        			Intent intent = new Intent(EntryActivity.this,AlreadyRegisteredActivity.class);
	        			intent.putExtra(getResources().getString(R.string.intent_extra_regid), regId);
	        			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
	        			startActivity(intent);
	        			//finish();
	        		}else{
	        			mLicenseTask = new AsyncTask<Void, Void, String>() {

	        	            @Override
	        	            protected String doInBackground(Void... params) {
	        	              //  boolean registered = ServerUtilities.register(context, regId);
	        	            	String response="";
	        	            	try{
	        	            		response =ServerUtilities.getEULA(context,"");
	        	            	}catch(Exception e){
	        	            		e.printStackTrace();
	        	            	}
	        	                return response;
	        	            }

	        	            @Override
	        	            protected void onPostExecute(String result) {
	        	            	if(CommonUtilities.DEBUG_MODE_ENABLED){Log.v("REG IDDDD",regId);}
	        	            	if(result != null){
	        	            		SharedPreferences mainPref = EntryActivity.this.getSharedPreferences(getResources().getString(R.string.shared_pref_package),
	        	    						Context.MODE_PRIVATE);
	        	    				Editor editor = mainPref.edit();
	        	    				editor.putString(getResources().getString(R.string.shared_pref_eula), result);
	        	    				editor.commit();
	        	            	}
	        	            	mLicenseTask = null;
	        	            }

	        	        };

	        	        //mLicenseTask.execute();
	        			Intent intent = new Intent(EntryActivity.this,AuthenticationActivity.class);
	        			intent.putExtra(getResources().getString(R.string.intent_extra_regid), regId);
	        			//intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
	        			startActivity(intent);
	        			//finish();
	        		}
            	}
                mRegisterTask = null;
                
            }

        };
        if(accessFlag){
        	if(state){
        		Intent intent = new Intent(EntryActivity.this,AlreadyRegisteredActivity.class);
    			intent.putExtra(getResources().getString(R.string.intent_extra_regid), regId);
    			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    			startActivity(intent);
        	}else{
        		mRegisterTask.execute(null, null, null);
        	}
        }else{
        	showAlert(getResources().getString(R.string.device_not_compatible_error), getResources().getString(R.string.error_authorization_failed));
        }
        
        
       
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.entry, menu);
		return true;
	}
	
	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		if (progressDialog!=null && progressDialog.isShowing()){
    		progressDialog.dismiss();
    		progressDialog=null;
        }
	}
	
	protected void onDestroy() {
        if (mRegisterTask != null) {
            mRegisterTask.cancel(true);
        }
        
        if(mLicenseTask!=null){
        	mLicenseTask.cancel(true);
        }
        
        if(progressDialog!=null && progressDialog.isShowing()){
        	progressDialog.dismiss();
        	progressDialog = null;
        }
        try{
        unregisterReceiver(mHandleMessageReceiver);
        GCMRegistrar.onDestroy(getApplicationContext());
        }catch(Exception ex){
        	ex.printStackTrace();
        }
        super.onDestroy();
    }

    private void checkNotNull(Object reference, String name) {
        if (reference == null) {
            throw new NullPointerException(
                    getString(R.string.error_config, name));
        }
    }
    
    void HandleNetworkError(Exception e) {
    	//Toast.makeText(context, "Connecting to server failed", Toast.LENGTH_LONG).show();
    	if (mRegisterTask != null) {
            mRegisterTask.cancel(true);
        }
    	Intent intentIP = new Intent(EntryActivity.this,SettingsActivity.class);
		intentIP.putExtra(getResources().getString(R.string.intent_extra_from_activity), AuthenticationActivity.class.getSimpleName());
		intentIP.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intentIP);
    }
    
    @Override
    protected void onResume() {
    	// TODO Auto-generated method stub
       mRegisterTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
            	try{
            		state =ServerUtilities.isRegistered(regId, context);
            	}catch(Exception e){
            		e.printStackTrace();
            		//HandleNetworkError(e);
            		//Toast.makeText(getApplicationContext(), "No Connection", Toast.LENGTH_LONG).show();
            	}
                return null;
            }
            
            
            //declare other objects as per your need
            @Override
            protected void onPreExecute()
            {
                progressDialog= ProgressDialog.show(EntryActivity.this, "Checking Registration Info","Please wait", true);
                progressDialog.setCancelable(true);
                progressDialog.setOnCancelListener(cancelListener);
                //do initialization of required objects objects here                
            };     

            OnCancelListener cancelListener=new OnCancelListener(){
                @Override
                public void onCancel(DialogInterface arg0){
                	showAlert("Could not connect to server please check your internet connection and try again", "Connection Error");
                    //finish();
                }
            };
            @Override
            protected void onPostExecute(Void result) {
            	if (progressDialog!=null && progressDialog.isShowing()){
            		progressDialog.dismiss();
                }
            	SharedPreferences mainPref = context.getSharedPreferences(
            			getResources().getString(R.string.shared_pref_package), Context.MODE_PRIVATE);
    			String success = mainPref.getString(getResources().getString(R.string.shared_pref_registered), "");
    			if(success.trim().equals(getResources().getString(R.string.shared_pref_reg_success))){
    				state = true;
    			}
    			
                mRegisterTask = null;
                
            }

        };
        if(accessFlag){
        	mRegisterTask.execute(null, null, null);
        	if(progressDialog!=null && progressDialog.isShowing()){
            	progressDialog.dismiss();
            	progressDialog = null;
            }
        }else{
        	//Toast.makeText(getApplicationContext(), getString(R.string.device_not_compatible_error), Toast.LENGTH_LONG).show();
        }
    	super.onResume();
    }
    
    public void showAlert(String message, String title){
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message);
        builder.setTitle(title);
        builder.setCancelable(true);
        builder.setPositiveButton(getResources().getString(R.string.button_ok),
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            	cancelEntry();
                dialog.cancel();
            }
        });
        /*builder1.setNegativeButton("No",
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });*/

        AlertDialog alert = builder.create();
        alert.show();
	}

    public void cancelEntry(){
		SharedPreferences mainPref = context.getSharedPreferences(getResources().getString(R.string.shared_pref_package),
				Context.MODE_PRIVATE);
		Editor editor = mainPref.edit();
		editor.putString(getResources().getString(R.string.shared_pref_policy), "");
		editor.putString(getResources().getString(R.string.shared_pref_isagreed), "0");
		editor.putString(getResources().getString(R.string.shared_pref_registered),"0");	
		editor.putString(getResources().getString(R.string.shared_pref_ip),"");
		editor.commit();
		//finish();
		
		Intent intentIP = new Intent(EntryActivity.this,SettingsActivity.class);
		intentIP.putExtra(getResources().getString(R.string.intent_extra_from_activity), AuthenticationActivity.class.getSimpleName());
		intentIP.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intentIP);
		
	}
    
    private final BroadcastReceiver mHandleMessageReceiver =
            new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String newMessage = intent.getExtras().getString(CommonUtilities.EXTRA_MESSAGE);
            mDisplay.append(newMessage + "\n");
        }
    };

}
