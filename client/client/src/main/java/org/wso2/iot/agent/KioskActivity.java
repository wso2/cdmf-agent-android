package org.wso2.iot.agent;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.wso2.iot.agent.events.EventRegistry;
import org.wso2.iot.agent.services.AgentDeviceAdminReceiver;
import org.wso2.iot.agent.services.LocalNotification;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

public class KioskActivity extends Activity {

    private DevicePolicyManager devicePolicyManager;
    private ComponentName cdmfDeviceAdmin;
    private boolean freshRegFlag = false;
    private static final int ACTIVATION_REQUEST = 47;
    private TextView textViewWipeData;

    AppInstallationBroadcastReceiver appInstallationBroadcastReceiver;
    boolean isAppInstallationBroadcastReceiverRegistered = false;

    private static final String ACTION_INSTALL_COMPLETE = "INSTALL_COMPLETED";

    TextView textViewKiosk;
    static TextView textViewLaunch;
    int kioskExit;

    static String packageName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kiosk);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Preference.putBoolean(getApplicationContext(), Constants.PreferenceFlag.DEVICE_ACTIVE, true);

        textViewLaunch = (TextView) findViewById(R.id.textViewLaunch);
        textViewLaunch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent launchIntent = getApplicationContext().getPackageManager()
                        .getLaunchIntentForPackage(KioskActivity.this.packageName);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (launchIntent != null) {
                    getApplicationContext().startActivity(launchIntent);
                }
            }
        });

        textViewKiosk = (TextView) findViewById(R.id.textViewKiosk);
        if(Constants.COSU_SECRET_EXIT){
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
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startLockTask();
        }

        devicePolicyManager =
                (DevicePolicyManager) getApplicationContext().getSystemService(Context.DEVICE_POLICY_SERVICE);

        cdmfDeviceAdmin = AgentDeviceAdminReceiver.getComponentName(getApplicationContext());


        textViewWipeData = (TextView) this.findViewById(R.id.textViewWipeData);
        if(Constants.DEFAULT_OWNERSHIP == Constants.OWNERSHIP_COSU && Constants.DISPLAY_WIPE_DEVICE_BUTTON){
            textViewWipeData.setVisibility(View.VISIBLE);
            textViewWipeData.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    new AlertDialog.Builder(KioskActivity.this)
                            .setTitle(getString(R.string.app_name))
                            .setMessage(R.string.wipe_confirmation)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    DevicePolicyManager devicePolicyManager = (DevicePolicyManager)
                                            getApplicationContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
                                    devicePolicyManager.
                                            wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE |
                                                    DevicePolicyManager.WIPE_RESET_PROTECTION_DATA);
                                }})
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                }
            });
        }
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


    private class AppInstallationBroadcastReceiver extends BroadcastReceiver {

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onReceive(Context context, Intent intent) {
            String packageName =  intent.getData().getEncodedSchemeSpecificPart();
            devicePolicyManager.setLockTaskPackages(cdmfDeviceAdmin,
                    new String[]{context.getApplicationContext().getPackageName(), packageName});
            //Setting permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                for(String permission: Constants.ANDROID_COSU_PERMISSIONS){
                    devicePolicyManager.setPermissionGrantState(cdmfDeviceAdmin,
                            packageName, permission,
                            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
                }
            }
            KioskActivity.packageName = packageName;
            textViewLaunch.setVisibility(View.VISIBLE);
            Intent launchIntent = getApplicationContext().getPackageManager()
                    .getLaunchIntentForPackage(packageName);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (launchIntent != null) {
                getApplicationContext().startActivity(launchIntent);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isAppInstallationBroadcastReceiverRegistered) {
            if (appInstallationBroadcastReceiver == null)
                appInstallationBroadcastReceiver = new AppInstallationBroadcastReceiver();
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
            intentFilter.addAction(Intent.ACTION_PACKAGE_INSTALL);
            intentFilter.addDataScheme("package");
            registerReceiver(appInstallationBroadcastReceiver, intentFilter);
            isAppInstallationBroadcastReceiverRegistered = true;
        }

        startEvents();
        startPolling();
    }

    @Override
    protected void onPause() {
        if (isAppInstallationBroadcastReceiverRegistered) {
            unregisterReceiver(appInstallationBroadcastReceiver);
            appInstallationBroadcastReceiver = null;
            isAppInstallationBroadcastReceiverRegistered = false;
        }

        super.onPause();
    }

}
