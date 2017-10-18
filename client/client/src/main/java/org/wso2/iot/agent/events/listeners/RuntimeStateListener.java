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

package org.wso2.iot.agent.events.listeners;

import android.util.Log;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.api.RuntimeInfo;
import org.wso2.iot.agent.beans.Application;
import org.wso2.iot.agent.events.EventRegistry;
import org.wso2.iot.agent.events.beans.EventPayload;
import org.wso2.iot.agent.events.publisher.HttpDataPublisher;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;

/**
 * This is to listen to Runtime information such as CPU.
 */
public class RuntimeStateListener implements AlertEventListener {
    private static final int CPU_THRESHOLD = 75;
    private static final String TAG = RuntimeStateListener.class.getName();

    @Override
    public void startListening() {
        RuntimeInfo runtimeInfo = new RuntimeInfo(EventRegistry.context,
                                                  new String[]{"top", "-n", "1",
                                                               "-m", "1", "-s", "cpu"});
        Application application = runtimeInfo.getHighestCPU();
        if (application.getCpu() > CPU_THRESHOLD) {
            try {
                String appState = CommonUtils.toJSON(application);
                publishEvent(appState, Constants.EventListeners.RUNTIME_STATE);
                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, appState);
                }
            } catch (AndroidAgentException e) {
                Log.i(TAG, "Could not convert to JSON");
            }
        }
    }

    @Override
    public void stopListening() {
    }

    @Override
    public void publishEvent(String payload, String type) {
        EventPayload eventPayload = new EventPayload();
        eventPayload.setPayload(payload);
        eventPayload.setType(type);
        if (EventRegistry.context != null) {
            HttpDataPublisher httpDataPublisher = new HttpDataPublisher(EventRegistry.context);
            httpDataPublisher.publish(eventPayload);
        } else {
            Log.w(TAG, "Event registry context not available.");
        }
    }
}
