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

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

import org.wso2.iot.agent.R;
import org.wso2.iot.agent.beans.Operation;
import org.wso2.iot.agent.services.FileUploadService;

/**
 * This class handles the file upload process when Allow button
 * of file upload notification is clicked.
 */
public class FileUploadReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Resources resources = context.getResources();
        Operation operation = (Operation) intent.getExtras().getSerializable(resources.
                getString(R.string.intent_extra_operation_object));
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (operation != null) {
            manager.cancel(operation.getId());
        }
        Intent upload = new Intent(context, FileUploadService.class);
        upload.putExtra(resources.
                getString(R.string.intent_extra_operation_object), operation);
        context.startService(upload);
    }
}
