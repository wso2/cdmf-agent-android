/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.iot.agent.events.publisher;

import android.content.Context;
import android.util.Log;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.api.DeviceInfo;
import org.wso2.iot.agent.beans.ServerConfig;
import org.wso2.iot.agent.events.beans.EventPayload;
import org.wso2.iot.agent.proxy.interfaces.APIResultCallBack;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

import java.util.Map;

public class HttpDataPublisher implements APIResultCallBack, DataPublisher {

    private static final String TAG = HttpDataPublisher.class.getName();
    private ServerConfig utils;
    private Context context;

    public HttpDataPublisher(Context context) {
        this.context = context;
        String prefIP = Preference.getString(context, Constants.PreferenceFlag.IP);
        String serverIP = Constants.DEFAULT_HOST;
        if (prefIP != null) {
            serverIP = prefIP;
        }
        utils = new ServerConfig();
        utils.setServerIP(serverIP);
    }

    public void publish(EventPayload eventPayload) {
        DeviceInfo deviceInfo = new DeviceInfo(context);
        String deviceIdentifier = deviceInfo.getDeviceId();
        eventPayload.setDeviceIdentifier(deviceIdentifier);
        try {
            String responsePayload = CommonUtils.toJSON(eventPayload);
            if (Preference.getBoolean(context, Constants.PreferenceFlag.REGISTERED)) {
                CommonUtils.callSecuredAPI(context,
                        utils.getAPIServerURL(context) +
                                Constants.EVENT_ENDPOINT, org.wso2.iot.agent.proxy.utils.
                                Constants.HTTP_METHODS.POST,
                        responsePayload, HttpDataPublisher.this,
                        Constants.EVENT_REQUEST_CODE);
            }
        } catch (AndroidAgentException e) {
            Log.e(TAG, "Cannot convert event data to JSON");
        }
    }

    @Override
    public void onReceiveAPIResult(Map<String, String> result, int requestCode) {
        if (Constants.DEBUG_MODE_ENABLED) {
            String status = result.get(Constants.STATUS);
            Log.d(TAG, "Result for request: " + requestCode + " is " + status);
        }
    }
}
