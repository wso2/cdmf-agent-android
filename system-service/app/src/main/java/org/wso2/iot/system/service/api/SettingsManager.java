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

package org.wso2.iot.system.service.api;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.os.Build;
import android.util.Log;
import org.wso2.iot.system.service.BuildConfig;
import org.wso2.iot.system.service.SystemService;

public class SettingsManager {
    private static final String TAG = SettingsManager.class.getName();

    public static void makeDeviceOwner() {
        ComponentName componentName = new ComponentName(BuildConfig.APPLICATION_ID, SystemService.class.getName());
        SystemService.devicePolicyManager.setDeviceOwner(componentName);
        Log.i(TAG, "enabled device owner");
    }

    public static void clearDeviceOwner() {
        Log.i(TAG, "disabled device owner");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            SystemService.devicePolicyManager.clearDeviceOwnerApp(BuildConfig.APPLICATION_ID);
        }
    }

    public static boolean restrict(String code, boolean state) {
        Log.d(TAG, "Restriction :" + code + " , set to:" + state);
        boolean restrictionState = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
            SystemService.devicePolicyManager != null) {
            if (isDeviceOwner()) {
                if (state) {
                    SystemService.devicePolicyManager.
                            addUserRestriction(SystemService.cdmDeviceAdmin, code);
                } else {
                    SystemService.devicePolicyManager.
                            clearUserRestriction(SystemService.cdmDeviceAdmin, code);
                }
                restrictionState = true;
            } else {
                Log.i(TAG, "Not the device owner.");
            }
        }
        return restrictionState;
    }

    public static void setScreenCaptureDisabled(boolean value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            SystemService.devicePolicyManager.setScreenCaptureDisabled(SystemService.cdmDeviceAdmin, value);
        }
    }

    public static void setStatusBarDisabled(boolean value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            SystemService.devicePolicyManager.setStatusBarDisabled(SystemService.cdmDeviceAdmin, value);
        }
    }

    public static void setAutoTimeRequired(boolean value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            SystemService.devicePolicyManager.setAutoTimeRequired(SystemService.cdmDeviceAdmin, value);
        }
    }

    public static void setVisibilityOfApp(String packageName , boolean visibility) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Log.i(TAG, "visibility of package "+ packageName + " will be set to "+ visibility);
            SystemService.devicePolicyManager.setApplicationHidden(SystemService.cdmDeviceAdmin, packageName, !visibility);
        }
    }

    /**
     * This method is used to check whether agent is registered as the device owner.
     *
     * @return true if agent is the device owner.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public static boolean isDeviceOwner() {
        return SystemService.devicePolicyManager.isDeviceOwnerApp(BuildConfig.APPLICATION_ID);
    }

}
