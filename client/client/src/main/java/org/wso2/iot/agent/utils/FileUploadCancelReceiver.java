/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.iot.agent.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.wso2.iot.agent.beans.Operation;


public class FileUploadCancelReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Operation operation = (Operation) intent.getExtras().getSerializable("operation");
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        if (operation != null) {
            operation.setStatus("ERROR");
            operation.setOperationResponse("Request rejected by the device user.");
            operation.setEnabled(true);
            editor.putInt("FILE_UPLOAD_ID", operation.getId());
            editor.putString("FILE_UPLOAD_STATUS", operation.getStatus());
            editor.putString("FILE_UPLOAD_RESPONSE", operation.getOperationResponse());
            editor.apply();
        }
    }
}
