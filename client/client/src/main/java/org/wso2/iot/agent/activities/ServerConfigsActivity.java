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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.wso2.iot.agent.KioskActivity;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.api.DeviceState;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;
import org.wso2.iot.agent.utils.Response;

/**
 * This the the activity that is used to capture the server's host name.
 */
public class ServerConfigsActivity extends AppCompatActivity {

	private EditText editTextServerIP;
	private Button btnStartRegistration;
	private Context context;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(!Constants.OWNERSHIP_COSU.equals(Constants.DEFAULT_OWNERSHIP)) {
			setContentView(R.layout.activity_server_configs);
		}
		context = this.getApplicationContext();
		editTextServerIP = (EditText) findViewById(R.id.editTextServerIP);
		btnStartRegistration = (Button) findViewById(R.id.btnStartRegistration);

		btnStartRegistration.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (!editTextServerIP.getText().toString().trim().isEmpty()) {
					String host = editTextServerIP.getText().toString().trim();
					CommonUtils.saveHostDeatils(context, host);
					startAuthenticationActivity();
				} else {
					Toast.makeText(context.getApplicationContext(),
							getResources().getString(
									R.string.toast_message_enter_server_address),
							Toast.LENGTH_LONG).show();
				}
			}
		});

		DeviceState state = new DeviceState(context);
		Response deviceCompatibility = state.evaluateCompatibility();
		TextView textViewDescription = (TextView) findViewById(R.id.textViewDescription);
		if (!deviceCompatibility.getCode()) {
			textViewDescription.setText(deviceCompatibility.getDescriptionResourceID());
			btnStartRegistration.setVisibility(View.GONE);
			editTextServerIP.setVisibility(View.GONE);
		} else {
			btnStartRegistration.setVisibility(View.VISIBLE);
			editTextServerIP.setVisibility(View.VISIBLE);
			String ipSaved = Constants.DEFAULT_HOST;
			String prefIP = Preference.getString(context.getApplicationContext(), Constants.PreferenceFlag.IP);
			if (prefIP != null) {
				ipSaved = prefIP;
			}

			if (Constants.DEFAULT_HOST != null) {
				ipSaved = Constants.DEFAULT_HOST;
				CommonUtils.saveHostDeatils(context, ipSaved);
			}

			// check if we have the IP saved previously.
			if (ipSaved != null && !ipSaved.isEmpty()) {
				editTextServerIP.setText(ipSaved);
				btnStartRegistration.setBackgroundResource(R.drawable.btn_orange);
				btnStartRegistration.setTextColor(ContextCompat.getColor(this, R.color.white));
				btnStartRegistration.setEnabled(true);
				btnStartRegistration.invalidate();
			}
			boolean isDeviceActive = Preference.getBoolean(context, Constants.PreferenceFlag.DEVICE_ACTIVE);

			if (isDeviceActive) {
				if (Constants.OWNERSHIP_COSU.equals(Constants.DEFAULT_OWNERSHIP)) {
					Intent intent = new Intent(ServerConfigsActivity.this, KioskActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					startActivity(intent);
				} else {
					Intent intent = new Intent(ServerConfigsActivity.this, AlreadyRegisteredActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					startActivity(intent);
				}
				finish();
			}

			editTextServerIP.addTextChangedListener(new TextWatcher() {
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
	}

	/**
	 * Validation done to see if the server IP field is properly
	 * entered.
	 */
	private void enableSubmitIfReady() {

		boolean isReady = false;

		if (editTextServerIP.getText().toString().length() >= 1) {
			isReady = true;
		}

		if (isReady) {
			btnStartRegistration.setBackgroundResource(R.drawable.btn_orange);
			btnStartRegistration.setTextColor(ContextCompat.getColor(this, R.color.white));
			btnStartRegistration.setEnabled(true);
		} else {
			btnStartRegistration.setBackgroundResource(R.drawable.btn_grey);
			btnStartRegistration.setTextColor(ContextCompat.getColor(this, R.color.black));
			btnStartRegistration.setEnabled(false);
		}
		btnStartRegistration.invalidate();
	}

	/**
	 * This method is called to open AuthenticationActivity.
	 */
	private void startAuthenticationActivity() {
		if (!Constants.AUTO_ENROLLMENT_BACKGROUND_SERVICE_ENABLED) {
			Intent intent = new Intent(ServerConfigsActivity.this, AuthenticationActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
		}
	}

	@Override
	protected void onDestroy() {
		context = null;
		super.onDestroy();
	}
}
