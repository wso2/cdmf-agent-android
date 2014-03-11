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

import com.wso2.mobile.mdm.api.DeviceInfo;
import com.wso2.mobile.mdm.R;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;

public class AvailableOperationsActivity extends Activity {
	private String FROM_ACTIVITY = null;
	private String REG_ID = "";
	DeviceInfo info = null;
	    
	private String[] OP_NAME;
    private int[] ICONS;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_available_operations);

		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			if(extras.containsKey(getResources().getString(R.string.intent_extra_from_activity))){
				FROM_ACTIVITY = extras.getString(getResources().getString(R.string.intent_extra_from_activity));
			}
			
			if(extras.containsKey(getResources().getString(R.string.intent_extra_regid))){
				REG_ID = extras.getString(getResources().getString(R.string.intent_extra_regid));
			}
		}
		info = new DeviceInfo(AvailableOperationsActivity.this);
		
		if((info.getSdkVersion() >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH)){
			OP_NAME = new String[] { "Device Info", "Device Location", "Mute", "Lock Device", "Wipe Data", "Change Lock Code", "Notification", "Set WIFI", "Disable/Enable Camera", "Get Application List", "Install Application", "Uninstall Application", "Encrypt Storage" };
		    ICONS = new int[]{R.drawable.info, R.drawable.location, R.drawable.mute, R.drawable.lock,R.drawable.wipe, R.drawable.changepassword, R.drawable.notification, R.drawable.wifi, R.drawable.camera, R.drawable.applist, R.drawable.appinstall, R.drawable.appuninstall, R.drawable.encrypt};	
		}else{
			OP_NAME = new String[] { "Device Info", "Device Location", "Mute", "Lock Device", "Wipe Data", "Change Lock Code", "Notification", "Set WIFI", "Get Application List", "Install Application", "Uninstall Application"};
		    ICONS = new int[]{R.drawable.info, R.drawable.location, R.drawable.mute, R.drawable.lock,R.drawable.wipe, R.drawable.changepassword, R.drawable.notification, R.drawable.wifi, R.drawable.applist, R.drawable.appinstall, R.drawable.appuninstall};	
		}
		
		ListView listView = (ListView) findViewById(R.id.listview);  
		ColorDrawable grey = new ColorDrawable(this.getResources().getColor(R.color.light_grey));
		listView.setDivider(grey);
		listView.setDividerHeight(1);
		listView.setAdapter(new IconicAdapter());
	}

	class IconicAdapter extends ArrayAdapter<String> {
		IconicAdapter() {
			super(AvailableOperationsActivity.this, R.layout.row_with_icon, R.id.rowTextView, OP_NAME);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row=super.getView(position, convertView, parent);
			ImageView icon=(ImageView)row.findViewById(R.id.rowImage);
			icon.setImageResource(ICONS[position]);
			//TextView size=(TextView)row.findViewById(R.id.size);
			//size.setText(String.format(getString(R.string.size_template),OP_NAME[position].length()));
			return(row);
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && FROM_ACTIVITY != null && FROM_ACTIVITY.equals(AlreadyRegisteredActivity.class.getSimpleName())) {
    		Intent intent = new Intent(AvailableOperationsActivity.this,AlreadyRegisteredActivity.class);
    		intent.putExtra(getResources().getString(R.string.intent_extra_from_activity), AvailableOperationsActivity.class.getSimpleName());
    		intent.putExtra(getResources().getString(R.string.intent_extra_regid), REG_ID);
    		startActivity(intent);
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
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		//getMenuInflater().inflate(R.menu.available_operations, menu);
		return true;
	}

}
