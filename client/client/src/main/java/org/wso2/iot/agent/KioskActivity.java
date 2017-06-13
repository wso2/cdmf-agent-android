package org.wso2.iot.agent;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.wso2.iot.agent.api.ApplicationManager;
import org.wso2.iot.agent.events.EventRegistry;
import org.wso2.iot.agent.events.listeners.KioskAppInstallationListener;
import org.wso2.iot.agent.services.LocalNotification;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

public class KioskActivity extends Activity {
    private TextView textViewWipeData;
    private Context context;
    private TextView textViewKiosk;
    private static TextView textViewLaunch;
    private int kioskExit;
    private static String packageName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kiosk);
        context = this.getApplicationContext();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Preference.putBoolean(getApplicationContext(), Constants.PreferenceFlag.DEVICE_ACTIVE, true);

        textViewLaunch = (TextView) findViewById(R.id.textViewLaunch);
        textViewLaunch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchKioskAppIfExists();
            }
        });
        textViewLaunch.setVisibility(View.VISIBLE);

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
        installKioskApp();
        launchKioskAppIfExists();

        ComponentName component = new ComponentName(KioskActivity.this, KioskAppInstallationListener.class);
        getPackageManager()
                .setComponentEnabledSetting(component,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
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

    /*Checks whether there is an already installed app and if exists the app will be launched*/
    private void launchKioskAppIfExists() {
        packageName = Preference.getString(context.getApplicationContext(), Constants.KIOSK_APP_PACKAGE_NAME);
        if (packageName != null && !packageName.equals("")) {
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

        startEvents();
        startPolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void installKioskApp() {
        String appUrl = Preference.getString(getApplicationContext(), Constants.KIOSK_APP_DOWNLOAD_URL);
        if (appUrl != null) {
            Preference.removePreference(getApplicationContext(), Constants.KIOSK_APP_DOWNLOAD_URL);
            ApplicationManager applicationManager = new ApplicationManager(context.getApplicationContext());
            applicationManager.installApp(appUrl, null, null);
        }
    }

}
