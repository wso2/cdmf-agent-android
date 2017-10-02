/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.iot.agent;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Image;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.wso2.iot.agent.adapters.AppDrawerAdapter;
import org.wso2.iot.agent.api.ApplicationManager;
import org.wso2.iot.agent.api.DeviceInfo;
import org.wso2.iot.agent.api.DeviceState;
import org.wso2.iot.agent.beans.Power;
import org.wso2.iot.agent.events.EventRegistry;
import org.wso2.iot.agent.events.listeners.KioskAppInstallationListener;
import org.wso2.iot.agent.services.LocalNotification;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class KioskActivity extends Activity {
    private Context context;
    private ImageView imageViewBatteryPlugged;
    private TextView textViewNoApps;
    private TextView textViewTime;
    private TextView textViewDate;
    private TextView textViewInitializingMsg;
    private TextView textViewBattery;
    private ProgressBar progressBarDeviceInitializing;
    private int kioskExit;
    private GridView gridView;
    private AppDrawerAdapter appDrawerAdapter;
    private final String TAG = KioskActivity.class.getSimpleName();
    private AudioManager audio;
    private int ringerMode;
    private int ringerVolume;
    private Uri defaultRingtoneUri;
    private Ringtone defaultRingtone;
    private DeviceInfo deviceInfo;
    private SeekBar seekBarBrightness;
    private int brightness=0;
    private static final int DEFAULT_FLAG = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kiosk);
        context = this.getApplicationContext();
        deviceInfo = new DeviceInfo(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Preference.putBoolean(getApplicationContext(),
                Constants.PreferenceFlag.DEVICE_ACTIVE, true);
        TextView textViewKiosk = (TextView) findViewById(R.id.textViewKiosk);
        textViewTime = (TextView) findViewById(R.id.textTime);
        textViewDate = (TextView) findViewById(R.id.textViewDate);
        textViewBattery = (TextView) findViewById(R.id.textViewBattery);
        imageViewBatteryPlugged = (ImageView) findViewById(R.id.imageViewBattryPlugged);
        textViewInitializingMsg = (TextView) findViewById(R.id.textViewInitializingMsg);
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        progressBarDeviceInitializing =
                (ProgressBar) findViewById(R.id.progressBarDeviceInitializing);
        seekBarBrightness = (SeekBar) findViewById(R.id.seekBarBrightness);
        seekBarBrightness.setMax(255);
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

        try {
            //Get the current system brightness state
            brightness = android.provider.Settings.System.getInt(
                    getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS);
            seekBarBrightness.setProgress(brightness);
        } catch (Exception e) {
            e.printStackTrace();
        }
        seekBarBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                brightness = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
                WindowManager.LayoutParams settings = getWindow().getAttributes();
                settings.screenBrightness = brightness;
                getWindow().setAttributes(settings);

            }});

        ComponentName component =
                new ComponentName(KioskActivity.this, KioskAppInstallationListener.class);
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

        TextView textViewWipeData = (TextView) this.findViewById(R.id.textViewWipeData);
        if (Constants.DEFAULT_OWNERSHIP.
                equals(Constants.OWNERSHIP_COSU) && Constants.DISPLAY_WIPE_DEVICE_BUTTON) {
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
                                            getApplicationContext().
                                                    getSystemService(Context.DEVICE_POLICY_SERVICE);
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                                        devicePolicyManager.
                                                wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE |
                                                        DevicePolicyManager.
                                                                WIPE_RESET_PROTECTION_DATA);
                                    }
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

        if (!Preference.getBoolean(context, Constants.PreferenceFlag.DEVICE_INITIALIZED)) {
            textViewNoApps.setVisibility(View.INVISIBLE);
            textViewInitializingMsg.setVisibility(View.VISIBLE);
            progressBarDeviceInitializing.setVisibility(View.VISIBLE);
            //checkAndDisplayDeviceInitializing();
        }
        displayDeviceInfo();
        checkAndDisplayDeviceInitializing();
        installKioskApp();
        if (Preference.getBoolean(context.getApplicationContext(), Constants.AGENT_FRESH_START)) {
            launchKioskAppIfExists();
        }

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            if (extras.containsKey(getResources().getString(R.string.intent_extra_type))) {
                String temp = extras.getString("type");
                switch (temp) {
                    case "ring": {
                        startRing();
                        showDialog("Ring Operation", "Your device is ringing", "OK");
                        break;
                    }
                    case "notification": {
                        String title = extras.getString("title");
                        String text = extras.getString("text");
                        showDialog(title,text,"OK");
                        break;
                    }

                }
            }
        }
    }

    private void showDialog(String title, String message, String buttonText) {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_kiosk);
        dialog.setTitle(title);

        // set the custom dialog components - text, image and button
        TextView text = (TextView) dialog.findViewById(R.id.textViewDialog);
        text.setText(message);

        Button dialogButton = (Button) dialog.findViewById(R.id.buttonDialog);
        dialogButton.setText(buttonText);
        // if button is clicked, close the custom dialog
        dialogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                stopRing();
            }
        });
        dialog.show();
    }

    private void checkAndDisplayDeviceInitializing() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    while (!Preference.
                            getBoolean(context, Constants.PreferenceFlag.DEVICE_INITIALIZED)) {
                        //Check if the initialization is completed in every one second.
                        Thread.sleep(1000);
                    }
                    textViewInitializingMsg.setVisibility(View.INVISIBLE);
                    progressBarDeviceInitializing.setVisibility(View.INVISIBLE);
                    refreshAppDrawer();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Thread is interrupted");
                }
            }
        };
        thread.start();
    }

    private void displayDeviceInfo() {
        Thread thread = new Thread() {
            final DeviceState phoneState = new DeviceState(context);
            Power power = phoneState.getBatteryDetails();
            String time;
            String date;

            @Override
            public void run() {
                try {
                    while (!isInterrupted()) {
                        Thread.sleep(1000);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                power = phoneState.getBatteryDetails();
                                time = new SimpleDateFormat(Constants.LAUNCHER_TIME_FORMAT,
                                        Locale.ENGLISH).format(Calendar.getInstance().getTime());
                                date = new SimpleDateFormat(Constants.LAUNCHER_DATE_FORMAT,
                                        Locale.ENGLISH).format(Calendar.getInstance().getTime());
                                textViewTime.setText(Constants.LAUNCHER_TIME_LABEL + time);
                                textViewDate.setText(Constants.LAUNCHER_DATE_LABEL + date);
                                textViewBattery.setText(Constants.LAUNCHER_BATTERY_LABEL +
                                        String.valueOf(power.getLevel()) +
                                        Constants.LAUNCHER_PERCENTAGE_MARK);
                                String plugged = power.getPlugged();
                                if (plugged == DeviceState.AC) {
                                    imageViewBatteryPlugged.setVisibility(View.VISIBLE);
                                } else {
                                    imageViewBatteryPlugged.setVisibility(View.INVISIBLE);
                                }
                                refreshAppDrawer();
                            }
                        });
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Thread is interrupted");
                }
            }
        };
        thread.start();
    }

    @Override
    public void onBackPressed() {

    }

    private void startEvents() {
        if (!EventRegistry.eventListeningStarted) {
            EventRegistry registerEvent = new EventRegistry(this);
            registerEvent.register();
        }
    }

    private void startPolling() {
        String notifier = Preference.
                getString(getApplicationContext(), Constants.PreferenceFlag.NOTIFIER_TYPE);
        if (Constants.NOTIFIER_LOCAL.equals(notifier) &&
                !Constants.AUTO_ENROLLMENT_BACKGROUND_SERVICE_ENABLED) {
            LocalNotification.startPolling(getApplicationContext());
        }
    }

    /*Checks whether there is an already installed app and if exists the app will be launched*/
    private void launchKioskAppIfExists() {
        Preference.putBoolean(context.getApplicationContext(), Constants.AGENT_FRESH_START, false);
        String appList = Preference.
                getString(context.getApplicationContext(), Constants.KIOSK_APP_PACKAGE_NAME);
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
        String appUrl = Preference.
                getString(getApplicationContext(), Constants.KIOSK_APP_DOWNLOAD_URL);
        if (appUrl != null) {
            Preference.removePreference(getApplicationContext(), Constants.KIOSK_APP_DOWNLOAD_URL);
            ApplicationManager applicationManager =
                    new ApplicationManager(context.getApplicationContext());
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
            if (Preference.getBoolean(context, Constants.PreferenceFlag.DEVICE_INITIALIZED)) {
                gridView.setVisibility(View.INVISIBLE);
                textViewNoApps.setVisibility(View.VISIBLE);
            }
        } else {
            appDrawerAdapter.setAppList();
            appDrawerAdapter.notifyDataSetChanged();
            textViewNoApps.setVisibility(View.INVISIBLE);
            gridView.setVisibility(View.VISIBLE);
        }
    }

    @TargetApi(21)
    private void startRing() {

        if (audio != null) {
            ringerMode = audio.getRingerMode();
            ringerVolume = audio.getStreamVolume(AudioManager.STREAM_RING);
            audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            audio.setStreamVolume(AudioManager.STREAM_RING, audio.getStreamMaxVolume(AudioManager.STREAM_RING),
                    AudioManager.FLAG_PLAY_SOUND);

            defaultRingtoneUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE);

            if (defaultRingtoneUri != null) {
                defaultRingtone = RingtoneManager.getRingtone(this, defaultRingtoneUri);

                if (defaultRingtone != null) {
                    if (deviceInfo.getSdkVersion() >= Build.VERSION_CODES.LOLLIPOP) {
                        AudioAttributes attributes = new AudioAttributes.Builder().
                                setUsage(AudioAttributes.USAGE_NOTIFICATION).
                                setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).
                                build();
                        defaultRingtone.setAudioAttributes(attributes);
                    } else {
                        defaultRingtone.setStreamType(AudioManager.STREAM_NOTIFICATION);
                    }
                    defaultRingtone.play();
                }
            }
        }
    }

    private void stopRing() {
        if (defaultRingtone != null && defaultRingtone.isPlaying()) {
            defaultRingtone.stop();
        }
        audio.setStreamVolume(AudioManager.STREAM_RING, ringerVolume, DEFAULT_FLAG);
        audio.setRingerMode(ringerMode);
    }

}
