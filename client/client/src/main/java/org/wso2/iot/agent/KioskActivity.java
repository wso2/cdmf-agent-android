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
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.TextView;

import org.wso2.iot.agent.adapters.AppDrawerAdapter;
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
    private TextView textViewNoApps;
    private int kioskExit;
    private GridView gridView;
    private AppDrawerAdapter appDrawerAdapter;
    private final String TAG = KioskActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kiosk);
        context = this.getApplicationContext();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Preference.putBoolean(getApplicationContext(), Constants.PreferenceFlag.DEVICE_ACTIVE, true);

        /*
<<<<<<< HEAD
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

=======
>>>>>>> 8638437c041d741405a69bfc8c7fb63e91f6d2fa */
        textViewKiosk = (TextView) findViewById(R.id.textViewKiosk);
        if (Constants.COSU_SECRET_EXIT) {
            textViewKiosk.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    kioskExit++;
                    if (kioskExit == 6) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            stopLockTask();
                        }
                        finish();
                    }
                }
            });
        }

        ComponentName component = new ComponentName(KioskActivity.this, KioskAppInstallationListener.class);
        getPackageManager()
                .setComponentEnabledSetting(component,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                startLockTask();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }

        textViewWipeData = (TextView) this.findViewById(R.id.textViewWipeData);
        if (Constants.DEFAULT_OWNERSHIP == Constants.OWNERSHIP_COSU && Constants.DISPLAY_WIPE_DEVICE_BUTTON) {
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
                                }
                            })
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                }
            });
        }

        gridView = (GridView) findViewById(R.id.gridview);
        textViewNoApps = (TextView) findViewById(R.id.textViewNoApps);
        appDrawerAdapter = new AppDrawerAdapter(context);
        gridView.setAdapter(appDrawerAdapter);
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v,
                                    int position, long id) {
                launchKioskApp((String) appDrawerAdapter.getItem(position));
            }
        });

        installKioskApp();
        if (Preference.getBoolean(context.getApplicationContext(), Constants.AGENT_FRESH_START)) {
            launchKioskAppIfExists();
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

    /*Checks whether there is an already installed app and if exists the app will be launched*/
    private void launchKioskAppIfExists() {
        Preference.putBoolean(context.getApplicationContext(), Constants.AGENT_FRESH_START, false);
        String appList = Preference.getString(context.getApplicationContext(), Constants.KIOSK_APP_PACKAGE_NAME);
        if (appList != null && !appList.equals("")) {
            String[] packageName = appList.split(context.getString(R.string.kiosk_application_package_split_regex));
            Intent launchIntent = getApplicationContext().getPackageManager()
                    .getLaunchIntentForPackage(packageName[packageName.length - 1]);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (launchIntent != null) {
                getApplicationContext().startActivity(launchIntent);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        refreshAppDrawer();
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

    private void launchKioskApp(String packageName) {
        if (packageName != null && !packageName.equals("")) {
            Intent launchIntent = getApplicationContext().getPackageManager()
                    .getLaunchIntentForPackage(packageName);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (launchIntent != null) {
                getApplicationContext().startActivity(launchIntent);
            }
        }
    }

    private void refreshAppDrawer() {
        String appList = Preference.getString(context, Constants.KIOSK_APP_PACKAGE_NAME);
        if (appList == null) {
            gridView.setVisibility(View.INVISIBLE);
            textViewNoApps.setVisibility(View.VISIBLE);
        } else {
            appDrawerAdapter.setAppList();
            appDrawerAdapter.notifyDataSetChanged();
            textViewNoApps.setVisibility(View.INVISIBLE);
            gridView.setVisibility(View.VISIBLE);
        }
    }

}
