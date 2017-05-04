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
import android.os.Bundle;
import android.widget.TextView;

import org.wso2.iot.agent.R;
import org.wso2.iot.agent.api.DeviceInfo;

/**
 * Activity which displays device information.
 */
public class DisplayDeviceInfoActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_display_device_info);
		DeviceInfo deviceInfo = new DeviceInfo(this.getApplicationContext());
		TextView deviceId = (TextView) findViewById(R.id.txtId);
		TextView deviceName = (TextView) findViewById(R.id.txtDevice);
		TextView model = (TextView) findViewById(R.id.txtModel);
		TextView operator = (TextView) findViewById(R.id.txtOperator);
		TextView sdk = (TextView) findViewById(R.id.txtSDK);
		TextView os = (TextView) findViewById(R.id.txtOS);
		TextView root = (TextView) findViewById(R.id.txtRoot);

		deviceId.setText(getResources().getString(R.string.info_label_imei) +
		                 getResources().getString(R.string.intent_extra_space) +
		                 deviceInfo.getDeviceId());
		
		deviceName.setText(getResources().getString(R.string.info_label_device) +
		                   getResources().getString(R.string.intent_extra_space) +
		                   deviceInfo.getDeviceName());
		
		model.setText(getResources().getString(R.string.info_label_model) +
		              getResources().getString(R.string.intent_extra_space) +
		              deviceInfo.getDeviceModel());

		String operators;
		
		if (deviceInfo.getNetworkOperatorName() != null) {
			operators = deviceInfo.getNetworkOperatorName();
		}else{
			operators = getResources().getString(R.string.info_label_no_sim);
		}
		
		operator.setText(getResources().getString(R.string.info_label_operator) +
		                 getResources().getString(R.string.intent_extra_space) + operators);
		
		if (deviceInfo.getIMSINumber() != null) {
			sdk.setText(getResources().getString(R.string.info_label_imsi) +
			            getResources().getString(R.string.intent_extra_space) +
			            deviceInfo.getIMSINumber());
		} else {
			sdk.setText(getResources().getString(R.string.info_label_imsi) +
			            getResources().getString(R.string.intent_extra_space) + operators);
		}
		
		os.setText(getResources().getString(R.string.info_label_os) +
		           getResources().getString(R.string.intent_extra_space) +
		           deviceInfo.getOsVersion());
		
		root.setText(getResources().getString(R.string.info_label_rooted) +
		             getResources().getString(R.string.intent_extra_space) +
		             (deviceInfo.isRooted() ? getResources().getString(R.string.yes)
		                                    : getResources().getString(R.string.no)));
	}

}
