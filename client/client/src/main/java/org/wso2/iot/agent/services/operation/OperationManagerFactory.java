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

package org.wso2.iot.agent.services.operation;

import android.annotation.TargetApi;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Build;
import org.wso2.iot.agent.api.DeviceInfo;
import org.wso2.iot.agent.utils.Constants;

/**
 * This class produce the matching Operation Manager according to the Device Configurations.
 */
public class OperationManagerFactory {

    private DeviceInfo info;
    private Context context;
    private DevicePolicyManager manager;

    public OperationManagerFactory(Context context) {
        this.context = context;
        this.info = new DeviceInfo(context);
        this.manager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public OperationManager getOperationManager() {
        if ((info.getSdkVersion() >= Build.VERSION_CODES.JELLY_BEAN) &&
                (info.getSdkVersion() < Build.VERSION_CODES.LOLLIPOP)) {
            return new OperationManagerOlderSdk(context);
        } else {
            return getLollipopUpwardsOperationManager();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private OperationManager getLollipopUpwardsOperationManager() {
        if(Constants.DEFAULT_OWNERSHIP == Constants.OWNERSHIP_COSU){
          return new OperationManagerCOSU(context);
        }
        else if (manager.isProfileOwnerApp(Constants.AGENT_PACKAGE)) {
            return new OperationManagerWorkProfile(context);
        }
        else if (manager.isDeviceOwnerApp(Constants.SYSTEM_SERVICE_PACKAGE) || manager.isDeviceOwnerApp(Constants.AGENT_PACKAGE)) {
                return new OperationManagerDeviceOwner(context);
        }
        else {
            return new OperationManagerBYOD(context);
        }
    }

}
