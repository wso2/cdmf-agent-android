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

import org.wso2.emm.agent.R;
import org.wso2.emm.agent.api.DeviceInfo;
import org.wso2.emm.agent.utils.CommonUtilities;
import org.wso2.emm.agent.utils.ServerUtilities;

import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.SharedPreferences.Editor;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
	TextView ip;
	Button optionBtn;
	private String FROM_ACTIVITY = null;
	private String REG_ID = "";
	Context context;
	String senderID=null;
	DeviceInfo info = null;
	boolean accessFlag = true;
	TextView errorMessage;
	String error = "";
	AsyncTask<Void, Void, String> mSenderIDTask;
	ProgressDialog progressDialog;
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
			
			if(extras.containsKey(getResources().getString(R.string.intent_extra_regid))){
				REG_ID = extras.getString(getResources().getString(R.string.intent_extra_regid));
			}
		}
		
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
		String success = mainPref.getString(getResources().getString(R.string.shared_pref_registered), "");
		
		if(success.trim().equals(getResources().getString(R.string.shared_pref_reg_success))){
			Intent intent = new Intent(SettingsActivity.this,AlreadyRegisteredActivity.class);
			intent.putExtra(getResources().getString(R.string.intent_extra_regid), REG_ID);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
		}
    	
		if(ipSaved != null && ipSaved != ""){
			ip.setText(ipSaved);
			Intent intent = new Intent(SettingsActivity.this,EntryActivity.class);
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
				// TODO Auto-generated method stub
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
	
	DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch (which) {
			case DialogInterface.BUTTON_POSITIVE:
				if(!ip.getText().toString().trim().equals("")){
					CommonUtilities.setSERVER_URL(ip.getText().toString().trim());
					getSenderID();
					
				}else{
					Toast.makeText(context, getResources().getString(R.string.toast_message_enter_server_address), Toast.LENGTH_LONG).show();
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
    		/*Intent intent = new Intent(SettingsActivity.this,AuthenticationActivity.class);
    		intent.putExtra("from_activity_name", SettingsActivity.class.getSimpleName());
    		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    		intent.putExtra("regid", REG_ID);
    		startActivity(intent);*/
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
	
	public void getSenderID(){
		  mSenderIDTask = new AsyncTask<Void, Void, String>() {
	            @Override
	            protected String doInBackground(Void... params) {
	            	String response = "";
	            	try{
	            		response =ServerUtilities.getSenderID(context);
	            	}catch(Exception e){
	            		e.printStackTrace();
	            		//HandleNetworkError(e);
	            		//Toast.makeText(getApplicationContext(), "No Connection", Toast.LENGTH_LONG).show();
	            	}
	                return response;
	            }
	            
	            
	            //declare other objects as per your need
	            @Override
	            protected void onPreExecute()
	            {
	            	progressDialog= ProgressDialog.show(SettingsActivity.this, getResources().getString(R.string.dialog_sender_id),getResources().getString(R.string.dialog_please_wait), true);
	                progressDialog.setCancelable(true);
	                progressDialog.setOnCancelListener(cancelListener);     
	            };     

	            OnCancelListener cancelListener=new OnCancelListener(){
	                @Override
	                public void onCancel(DialogInterface arg0){
	                	showAlert(getResources().getString(R.string.error_connect_to_server), getResources().getString(R.string.error_heading_connection));
	                }
	            };
	            
	            @Override
	            protected void onPostExecute(String result) {
	            	if (progressDialog!=null && progressDialog.isShowing()){
	            		progressDialog.dismiss();
	                }
	            	if(result!=null && !result.equals("")){
	            		CommonUtilities.setSENDER_ID(result);
	            	}
	            	SharedPreferences mainPref = context.getSharedPreferences(
	            			getResources().getString(R.string.shared_pref_package), Context.MODE_PRIVATE);
	            	Editor editor = mainPref.edit();
	            	editor.putString(getResources().getString(R.string.shared_pref_sender_id), senderID);
					editor.putString(getResources().getString(R.string.shared_pref_ip), ip.getText().toString().trim());
					editor.commit();
					
					CommonUtilities.setSERVER_URL(ip.getText().toString().trim());
					Intent intent = new Intent(SettingsActivity.this,EntryActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
	    			startActivity(intent);	
	    			
	                mSenderIDTask = null;
	                
	            }

	        };
	        
	        mSenderIDTask.execute(null, null, null);
	        	
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
        /*builder1.setNegativeButton("No",
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });*/

        AlertDialog alert = builder.create();
        alert.show();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.settings, menu);
		return true;
	}

}
