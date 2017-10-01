/*
 *
 *  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.wso2.iot.agent.services;

import android.content.Context;

import android.os.Build;
import android.support.annotation.RequiresApi;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.api.ApplicationManager;
import org.wso2.iot.agent.beans.ComplianceFeature;
import org.wso2.iot.agent.services.operation.OperationManager;
import org.wso2.iot.agent.services.operation.OperationManagerFactory;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;

/**
 * This class is used to check device policy compliance by checking each policy
 * with current device status.
 */
public class PolicyComplianceChecker {

    private Context context;
    private ComplianceFeature policy;
    private ApplicationManager applicationManager;
    private OperationManager operationManager;

    public PolicyComplianceChecker(Context context) {
        this.context = context;
        this.applicationManager = new ApplicationManager(context.getApplicationContext());
        OperationManagerFactory operationManagerFactory = new OperationManagerFactory(context);
        operationManager = operationManagerFactory.getOperationManager();
    }

    /**
     * Checks EMM policy on the device.
     *
     * @param operation - Operation object.
     * @return policy - ComplianceFeature object.
     */
    public ComplianceFeature checkPolicyState(org.wso2.iot.agent.beans.Operation operation)
            throws AndroidAgentException {

        policy = new ComplianceFeature();
        policy.setFeatureCode(operation.getCode());

        switch (operation.getCode()) {

            case Constants.Operation.CAMERA:
                return operationManager.checkCameraPolicy(operation, policy);
            case Constants.Operation.INSTALL_APPLICATION:
                return operationManager.checkInstallAppPolicy(operation, policy);
            case Constants.Operation.UNINSTALL_APPLICATION:
                return operationManager.checkUninstallAppPolicy(operation, policy);
            case Constants.Operation.ENCRYPT_STORAGE:
                return operationManager.checkEncryptPolicy(operation, policy);
            case Constants.Operation.PASSCODE_POLICY:
                return operationManager.checkPasswordPolicy(policy);
            case Constants.Operation.WIFI:
                return operationManager.checkWifiPolicy(operation, policy);
            case Constants.Operation.WORK_PROFILE:
                return operationManager.checkWorkProfilePolicy(operation, policy);
            case Constants.Operation.RUNTIME_PERMISSION_POLICY:
                return operationManager.checkRuntimePermissionPolicy(operation, policy);
            case Constants.Operation.VPN:
                //TODO: Compliance VPN settings
                policy.setCompliance(true);
                return policy;
            case Constants.Operation.COSU_PROFILE_POLICY:
            case Constants.Operation.DISALLOW_ADJUST_VOLUME:
            case Constants.Operation.DISALLOW_CONFIG_BLUETOOTH:
            case Constants.Operation.DISALLOW_CONFIG_CELL_BROADCASTS:
            case Constants.Operation.DISALLOW_CONFIG_CREDENTIALS:
            case Constants.Operation.DISALLOW_CONFIG_MOBILE_NETWORKS:
            case Constants.Operation.DISALLOW_CONFIG_TETHERING:
            case Constants.Operation.DISALLOW_CONFIG_VPN:
            case Constants.Operation.DISALLOW_CONFIG_WIFI:
            case Constants.Operation.DISALLOW_APPS_CONTROL:
            case Constants.Operation.DISALLOW_CREATE_WINDOWS:
            case Constants.Operation.DISALLOW_CROSS_PROFILE_COPY_PASTE:
            case Constants.Operation.DISALLOW_DEBUGGING_FEATURES:
            case Constants.Operation.DISALLOW_FACTORY_RESET:
            case Constants.Operation.DISALLOW_ADD_USER:
            case Constants.Operation.DISALLOW_INSTALL_APPS:
            case Constants.Operation.DISALLOW_INSTALL_UNKNOWN_SOURCES:
            case Constants.Operation.DISALLOW_MODIFY_ACCOUNTS:
            case Constants.Operation.DISALLOW_MOUNT_PHYSICAL_MEDIA:
            case Constants.Operation.DISALLOW_NETWORK_RESET:
            case Constants.Operation.DISALLOW_OUTGOING_BEAM:
            case Constants.Operation.DISALLOW_OUTGOING_CALLS:
            case Constants.Operation.DISALLOW_REMOVE_USER:
            case Constants.Operation.DISALLOW_SAFE_BOOT:
            case Constants.Operation.DISALLOW_SHARE_LOCATION:
            case Constants.Operation.DISALLOW_SMS:
            case Constants.Operation.DISALLOW_UNINSTALL_APPS:
            case Constants.Operation.DISALLOW_UNMUTE_MICROPHONE:
            case Constants.Operation.DISALLOW_USB_FILE_TRANSFER:
            case Constants.Operation.ALLOW_PARENT_PROFILE_APP_LINKING:
            case Constants.Operation.ENSURE_VERIFY_APPS:
            case Constants.Operation.AUTO_TIME:
            case Constants.Operation.ENABLE_ADMIN:
            case Constants.Operation.SET_SCREEN_CAPTURE_DISABLED:
            case Constants.Operation.SET_STATUS_BAR_DISABLED:
            case Constants.Operation.SYSTEM_UPDATE_POLICY:
                if(applicationManager.isPackageInstalled(Constants.SYSTEM_SERVICE_PACKAGE)) {
                    CommonUtils.callSystemApp(context, operation.getCode(),
                            Boolean.toString(operation.isEnabled()), null);
                }
                // Since without rooting the device a policy set by the device owner cannot
                // be violated or overridden, no compliance check is necessary.
                //TODO: implement a mechanism in the system app to call the agent back and report
                //policy status to agent.
                policy.setCompliance(true);
                return policy;
            case Constants.Operation.APP_RESTRICTION:
                return operationManager.checkAppRestrictionPolicy(operation, policy);
            default:
                throw new AndroidAgentException("Invalid operation code received");
        }
    }
}

