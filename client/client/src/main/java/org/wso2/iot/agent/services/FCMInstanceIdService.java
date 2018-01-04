/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import android.content.Intent;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

public class FCMInstanceIdService extends FirebaseInstanceIdService {

    private static final String TAG = FCMInstanceIdService.class.getSimpleName();

    @Override
    public void onTokenRefresh() {
        // Get updated InstanceID token.
        String token = FirebaseInstanceId.getInstance().getToken();
        if (Constants.DEBUG_MODE_ENABLED){
            Log.d(TAG, "FCM Token: " + token);
        }
        Preference.putString(this, Constants.FCM_REG_ID, token);
        Intent intent = new Intent();
        intent.setAction(Constants.FCM_TOKEN_REFRESHED_BROADCAST_ACTION);
        sendBroadcast(intent);
    }
}
