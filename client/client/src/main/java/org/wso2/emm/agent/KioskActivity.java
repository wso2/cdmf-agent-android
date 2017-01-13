package org.wso2.emm.agent;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import org.wso2.emm.agent.events.EventRegistry;
import org.wso2.emm.agent.services.LocalNotification;
import org.wso2.emm.agent.utils.Constants;
import org.wso2.emm.agent.utils.Preference;

public class KioskActivity extends Activity {

    private DevicePolicyManager devicePolicyManager;
    private ComponentName cdmDeviceAdmin;
    private boolean freshRegFlag = false;
    private static final int ACTIVATION_REQUEST = 47;

    TextView textViewKiosk;
    int kioskExit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kiosk);

        textViewKiosk = (TextView) findViewById(R.id.textViewKiosk);
        textViewKiosk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                kioskExit++;
                if(kioskExit == 6){
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        stopLockTask();
                    }
                    finish();
                }
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startLockTask();
        }

        startEvents();
        startPolling();
    }

    @Override
    public void onBackPressed() {

    }

    private void startEvents() {
        if(!EventRegistry.eventListeningStarted) {
            EventRegistry registerEvent = new EventRegistry(this);
            registerEvent.register();
        }
    }

    private void startPolling() {
        String notifier = Preference.getString(getApplicationContext(), Constants.PreferenceFlag.NOTIFIER_TYPE);
        if(Constants.NOTIFIER_LOCAL.equals(notifier) &&
                !Constants.AUTO_ENROLLMENT_BACKGROUND_SERVICE_ENABLED) {
            LocalNotification.startPolling(getApplicationContext());
        }
    }

}
