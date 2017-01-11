package org.wso2.emm.agent;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.wso2.emm.agent.events.EventRegistry;
import org.wso2.emm.agent.services.AgentDeviceAdminReceiver;
import org.wso2.emm.agent.services.LocalNotification;
import org.wso2.emm.agent.utils.Constants;
import org.wso2.emm.agent.utils.Preference;

public class KioskActivity extends Activity {

    private DevicePolicyManager devicePolicyManager;
    private ComponentName cdmDeviceAdmin;
    private boolean freshRegFlag = false;
    private static final int ACTIVATION_REQUEST = 47;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kiosk);

        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        cdmDeviceAdmin = new ComponentName(this, AgentDeviceAdminReceiver.class);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            if (extras.containsKey(getResources().getString(R.string.intent_extra_fresh_reg_flag))) {
                freshRegFlag = extras.getBoolean(getResources().getString(R.string.intent_extra_fresh_reg_flag));
            }
        }

        if (freshRegFlag) {
            Preference.putBoolean(this, Constants.PreferenceFlag.REGISTERED, true);
            if (!isDeviceAdminActive()) {
                startDeviceAdminPrompt(cdmDeviceAdmin);
            }
            freshRegFlag = false;

        } else if (Preference.getBoolean(this, Constants.PreferenceFlag.REGISTERED)) {
            if (isDeviceAdminActive()) {
                startEvents();
                startPolling();
            }
        }
    }

    private boolean isDeviceAdminActive() {
        return devicePolicyManager.isAdminActive(cdmDeviceAdmin);
    }

    private void startEvents() {
        if(!EventRegistry.eventListeningStarted) {
            EventRegistry registerEvent = new EventRegistry(this);
            registerEvent.register();
        }
    }

    private void startDeviceAdminPrompt(final ComponentName cdmDeviceAdmin) {
        KioskActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent deviceAdminIntent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                deviceAdminIntent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cdmDeviceAdmin);
                deviceAdminIntent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        getResources().getString(R.string.device_admin_enable_alert));
                startActivityForResult(deviceAdminIntent, ACTIVATION_REQUEST);
            }
        });
    }

    private void startPolling() {
        String notifier = Preference.getString(this, Constants.PreferenceFlag.NOTIFIER_TYPE);
        if(Constants.NOTIFIER_LOCAL.equals(notifier) &&
                !Constants.AUTO_ENROLLMENT_BACKGROUND_SERVICE_ENABLED) {
            LocalNotification.startPolling(this);
        }
    }

}
