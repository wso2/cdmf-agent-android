package org.wso2.iot.agent.services.operation.util;

import android.content.Context;

import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.beans.ComplianceFeature;
import org.wso2.iot.agent.beans.Operation;
import org.wso2.iot.agent.services.operation.OperationManager;



public class MockOperationManager extends OperationManager{

    public MockOperationManager(Context context) {
        super(context);
    }

    @Override
    public void wipeDevice(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void clearPassword(Operation operation) {

    }

    @Override
    public void installAppBundle(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void encryptStorage(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void setPasswordPolicy(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void changeLockCode(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void enterpriseWipe(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void disenrollDevice(Operation operation) {

    }

    @Override
    public void setSystemUpdatePolicy(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void hideApp(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void unhideApp(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void blockUninstallByPackageName(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void setProfileName(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void handleOwnersRestriction(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void handleDeviceOwnerRestriction(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void configureWorkProfile(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void restrictAccessToApplications(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void setPolicyBundle(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void setRuntimePermissionPolicy(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void setStatusBarDisabled(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void setScreenCaptureDisabled(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void setAutoTimeRequired(Operation operation) throws AndroidAgentException {

    }

    @Override
    public void configureCOSUProfile(Operation operation) throws AndroidAgentException {

    }

    @Override
    public ComplianceFeature checkWorkProfilePolicy(Operation operation, ComplianceFeature policy) throws AndroidAgentException {
        return null;
    }

    @Override
    public ComplianceFeature checkRuntimePermissionPolicy(Operation operation, ComplianceFeature policy) throws AndroidAgentException {
        return null;
    }
}