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
package org.wso2.iot.agent.services;

import android.os.AsyncTask;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.api.ApplicationManager;
import org.wso2.iot.agent.beans.Operation;
import org.wso2.iot.agent.utils.Constants;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * This class is a broadcast receiver which triggers on local notification timeouts.
 */
public class AlarmReceiver extends BroadcastReceiver {

	private static final String TAG = AlarmReceiver.class.getName();

	@Override
	public void onReceive(Context context, Intent intent) {
		if (Constants.DEBUG_MODE_ENABLED) {
			Log.d(TAG, "Recurring alarm; requesting alarm service.");
		}

		if (intent.hasExtra(context.getResources().getString(R.string.alarm_scheduled_operation))) {
			String operationCode = intent.getStringExtra(context.getResources()
					.getString(R.string.alarm_scheduled_operation));
			ApplicationManager applicationManager = new ApplicationManager(context
					.getApplicationContext());
			if (Constants.DEBUG_MODE_ENABLED) {
				Log.d(TAG, "Triggering scheduled operation: " + operationCode);
			}
			if(operationCode != null && operationCode.trim().equals(Constants.Operation.INSTALL_APPLICATION)) {
				String appUrl = intent.getStringExtra(context.getResources().getString(R.string.app_url));
				Operation operation = null;
				if (intent.hasExtra(context.getResources()
						.getString(R.string.alarm_scheduled_operation_payload))) {
                    operation = (Operation) intent.getSerializableExtra(context.getResources()
							.getString(R.string.alarm_scheduled_operation_payload));
                }
				try {
					applicationManager.installApp(appUrl, null, operation);
				} catch (AndroidAgentException e) {
					Log.e(TAG, "This is very unlikely to happen since schedule is null");
				}
			} else if(operationCode != null && operationCode.trim().equals(Constants.Operation.UNINSTALL_APPLICATION)) {
				String packageUri = intent.getStringExtra(context.getResources().getString(R.string.app_uri));
                Operation operation = null;
                if (intent.hasExtra(context.getResources().getString(R.string.alarm_scheduled_operation_payload))) {
                    operation = (Operation) intent.getSerializableExtra(context.getResources()
							.getString(R.string.alarm_scheduled_operation_payload));
                }
                try {
                    applicationManager.uninstallApplication(packageUri, operation, null);
                } catch (AndroidAgentException e) {
					Log.e(TAG, "App uninstallation failed." + e);
				}
			}

		} else {
			if (Constants.DEBUG_MODE_ENABLED) {
				Log.v(TAG, "Recurring alarm; intent URI: " + intent.toUri(0));
				Log.d(TAG, "Recurring alarm; Polling pending operations");
			}
			OperationTask operationTask = new OperationTask();
			operationTask.execute(context);
		}

	}

	private static class OperationTask extends AsyncTask<Context, Void, Void> {

		@Override
		protected Void doInBackground(Context... params) {
			if (params != null) {
				MessageProcessor messageProcessor = new MessageProcessor(params[0]);
				try {
					messageProcessor.getMessages();
				} catch (AndroidAgentException e) {
					Log.e(TAG, "Failed to perform operation", e);
				}
			}
			return null;
		}
	}
}
