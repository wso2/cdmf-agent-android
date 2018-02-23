/*
 * Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.emm.system.service.api;

import android.app.DownloadManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RecoverySystem;
import android.os.SystemProperties;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.emm.system.service.MainActivity;
import org.wso2.emm.system.service.R;
import org.wso2.emm.system.service.services.NotificationActionReceiver;
import org.wso2.emm.system.service.utils.CommonUtils;
import org.wso2.emm.system.service.utils.Constants;
import org.wso2.emm.system.service.utils.FileUtils;
import org.wso2.emm.system.service.utils.Preference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * This class handles the functionality required for performing OTA updates. Basically it handles
 * all the steps required from downloading the update package to installing it on the device.
 */
public class OTAServerManager {
    private static final String TAG = "OTA_SM";
    private static final String BUILD_DATE_UTC_PROPERTY = "ro.build.date.utc";
    private static final int DEFAULT_STATE_ERROR_CODE = 0;
    private static final int DEFAULT_STATE_INFO_CODE = 0;
    private static final int CURSOR_ATTEMPTS = 2;
    private static final int DEFAULT_BYTES = 100 * 1024;
    private static final int DEFAULT_STREAM_LENGTH = 153600;
    private static final int DEFAULT_OFFSET = 0;
    private OTAStateChangeListener stateChangeListener;
    private OTAServerConfig serverConfig;
    private long cacheProgress = -1;
    private Context context;
    private WakeLock wakeLock;
    private volatile long downloadedLength = 0;
    private volatile int lengthOfFile = 0;
    private volatile boolean isProgressUpdateTerminated = false;
    private AsyncTask asyncTask = null;
    private Executor executor;
    private static volatile boolean downloadOngoing = false;
    private static final int DOWNLOAD_PERCENTAGE_TOTAL = 100;
    private static final int DOWNLOADER_INCREMENT = 10;
    private NotificationManager mNotificationManager = null;
    private final static int OTA_NOTIFICATION_ID_START = 362738283;
    private final static int OTA_NOTIFICATION_ID_PROGRESS = 362738284;

    private int corePoolSize = 60;
    private int maximumPoolSize = 80;
    private int keepAliveTime = 10;
    private long downloadReference;
    private long startTimeStamp;

    private final List<String> dontDeleteTheseFolders = Arrays.asList("backup", "lost+found", "vfienv", "ota");


    //Use our own thread pool executor for async task to schedule new tasks upon download failures.
    private BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(maximumPoolSize);
    private Executor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);

    private RecoverySystem.ProgressListener recoveryVerifyListener = new RecoverySystem.ProgressListener() {
        public void onProgress(int progress) {
            Log.d(TAG, "Verify progress " + progress);
            if (stateChangeListener != null) {
                stateChangeListener.onStateOrProgress(OTAStateChangeListener.MESSAGE_VERIFY_PROGRESS,
                        DEFAULT_STATE_ERROR_CODE, null, progress);
            }
        }
    };

    public OTAServerManager(Context context) throws MalformedURLException {
        serverConfig = new OTAServerConfig(Build.PRODUCT, context);
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "OTA Wakelock");
        this.context = context;
        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void setStateChangeListener(OTAStateChangeListener stateChangeListener) {
        this.stateChangeListener = stateChangeListener;
    }

    public boolean checkNetworkOnline() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        boolean status = false;
        if (info != null && info.isConnectedOrConnecting()) {
            status = true;
        }

        return status;
    }

    public void startCheckingVersion() {
        if (this.stateChangeListener != null) {
            if (checkNetworkOnline()) {
                getTargetPackagePropertyList(this.serverConfig.getBuildPropURL());
            } else {
                reportCheckingError(OTAStateChangeListener.ERROR_WIFI_NOT_AVAILABLE);
                String message = "Connection failure while downloading the update.";
                Log.e(TAG, message);
                CommonUtils.sendBroadcast(context, Constants.Operation.GET_FIRMWARE_UPGRADE_PACKAGE_STATUS, Constants.Code.FAILURE,
                        Constants.Status.NETWORK_UNREACHABLE, message);
                CommonUtils.callAgentApp(context, Constants.Operation.FAILED_FIRMWARE_UPGRADE_NOTIFICATION, Preference.getInt(
                        context, context.getResources().getString(R.string.operation_id)), message);
            }
        } else {
            reportCheckingError(OTAStateChangeListener.ERROR_CANNOT_FIND_SERVER);
        }
    }

    /**
     * Compares device firmware version with the latest upgrade file from the server.
     *
     * @return - Returns true if the firmware needs to be upgraded.
     */
    public boolean compareLocalVersionToServer(BuildPropParser parser) {
        if (parser == null) {
            Log.d(TAG, "compareLocalVersion Without fetch remote prop list.");
            return false;
        }

        Long buildTime = Long.parseLong(SystemProperties.get(BUILD_DATE_UTC_PROPERTY));
        String buildTimeUTC = parser.getProp(BUILD_DATE_UTC_PROPERTY);
        Long remoteBuildUTC;
        if ((buildTimeUTC != null) && (!(buildTimeUTC.equals("null")))) {
            remoteBuildUTC = Long.parseLong(buildTimeUTC);
        } else {
            remoteBuildUTC = Long.MIN_VALUE;
            Log.e(TAG, "UTC date not found in config file, config may be corrupted or missing");
        }

        Log.d(TAG, "Local Version:" + Build.VERSION.INCREMENTAL + " Server Version:" + parser.getNumRelease());
        boolean upgrade = remoteBuildUTC > buildTime;
        Log.d(TAG, "Remote build time : " + remoteBuildUTC + " Local build time : " + buildTime);
        return upgrade;
    }

    private void publishDownloadProgress(long total, long downloaded) {
        long progress = (downloaded * 100) / total;
        long published = -1L;
        if (Preference.getString(context, context.getResources().getString(R.string.firmware_download_progress)) != null) {
            published = Long.valueOf(Preference.getString(context, context.getResources().getString(
                    R.string.firmware_download_progress)));
        }

        if (progress != published) {
            publishFirmwareDownloadProgress(progress);
            Preference.putString(context, context.getResources().getString(R.string.firmware_download_progress),
                    String.valueOf(progress));
            Log.d(TAG, "Download Progress - " + progress + "% - Downloaded:" + downloaded + "/" + total);
            if (progress == 100) {
                Preference.putString(context, context.getResources().getString(R.string.firmware_download_progress),
                        String.valueOf(DEFAULT_STATE_INFO_CODE));
            }
        }

        if (this.stateChangeListener != null && progress != cacheProgress) {
            this.stateChangeListener.onStateOrProgress(OTAStateChangeListener.MESSAGE_DOWNLOAD_PROGRESS,
                    DEFAULT_STATE_INFO_CODE, null, progress);
            cacheProgress = progress;
        }
    }

    private void publishFirmwareDownloadProgress(long progress) {
        JSONObject result = new JSONObject();
        try {
            result.put("progress", String.valueOf(progress));
            CommonUtils.sendBroadcast(context, Constants.Operation.UPGRADE_FIRMWARE, Constants.Code.SUCCESS,
                    Constants.Status.OTA_UPGRADE_ONGOING, result.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create JSON object when publishing OTA progress.");
            CommonUtils.sendBroadcast(context, Constants.Operation.UPGRADE_FIRMWARE, Constants.Code.SUCCESS,
                    Constants.Status.INTERNAL_ERROR, e.getMessage());
        }
    }

    void reportCheckingError(int error) {
        if (this.stateChangeListener != null) {
            this.stateChangeListener.onStateOrProgress(OTAStateChangeListener.STATE_IN_CHECKED,
                    error, null, DEFAULT_STATE_INFO_CODE);
        }
    }

    void reportDownloadError(int error) {
        if (this.stateChangeListener != null) {
            this.stateChangeListener.onStateOrProgress(OTAStateChangeListener.STATE_IN_DOWNLOADING,
                    error, null, DEFAULT_STATE_INFO_CODE);
        }
    }

    void reportInstallError(int error) {
        if (this.stateChangeListener != null) {
            this.stateChangeListener.onStateOrProgress(OTAStateChangeListener.STATE_IN_UPGRADING,
                    error, null, DEFAULT_STATE_INFO_CODE);
        }
    }

    private class Timeout extends TimerTask {
        private AsyncTask asyncTask;

        public Timeout(AsyncTask task) {
            asyncTask = task;
        }

        @Override
        public void run() {
            String message;

            isProgressUpdateTerminated = true;
            asyncTask.cancel(true);

            Log.w(TAG,"Timed out while downloading.");

            File targetFile = new File(FileUtils.getUpgradePackageFilePath());
            if (targetFile.exists()) {
                targetFile.delete();
                Log.w(TAG,"Partially downloaded update has been deleted.");
            }

            if (checkNetworkOnline()) {
                message = "Connection failure (Socket timeout) when downloading the update package.";
                Preference.putString(context, context.getResources().getString(R.string.upgrade_download_status), Constants.Status.CONNECTION_FAILED);
                CommonUtils.sendBroadcast(context, Constants.Operation.UPGRADE_FIRMWARE, Constants.Code.FAILURE,
                        Constants.Status.CONNECTION_FAILED, message);
            } else {
                message = "Disconnected from WiFi when downloading the update package.";
                Preference.putString(context, context.getResources().getString(R.string.upgrade_download_status), Constants.Status.WIFI_OFF);
                CommonUtils.sendBroadcast(context, Constants.Operation.UPGRADE_FIRMWARE, Constants.Code.FAILURE,
                        Constants.Status.WIFI_OFF, message);
            }
            Log.e(TAG, message);
            CommonUtils.callAgentApp(context, Constants.Operation.FAILED_FIRMWARE_UPGRADE_NOTIFICATION, Preference.getInt(
                    context, context.getResources().getString(R.string.operation_id)), message);
        }
    }

    private class DownloadProgressUpdateExecutor implements Executor {
        public void execute(@NonNull Runnable r) {
            new Thread(r).start();
        }
    }

    private void renameDownloadedFile(String otaPackageName){
        try {

            File otaFolder = new File(FileUtils.getOTAPackageFilePath());
            if (!otaFolder.exists()) {
                if (otaFolder.mkdir()) {
                    Log.i(TAG, "creating folder: " + otaFolder.toString());
                } else {
                    Log.i(TAG, "Failed to create directory!" + otaFolder.toString());
                }
            }
            String downloadedFilename = otaPackageName;
            Log.i(TAG, "moving file from " + downloadedFilename + " to " + FileUtils.getUpgradePackageFilePath());
            File from = new File(downloadedFilename);
            File to = new File(FileUtils.getUpgradePackageFilePath());
            org.apache.commons.io.FileUtils.moveFile(from, to);
        } catch (IOException ex){
            Log.e(TAG, "Error renaming file", ex);
        }
    }

    private void clearCacheDirectory(){
        File cacheDirectory = new File(FileUtils.getUpgradePackageDirectory());

        if (cacheDirectory.exists()) {
            Log.i(TAG, "Cleaning the folder: " + cacheDirectory);
            try {
                deleteRecursive(cacheDirectory);

            } catch (IOException ex){
                Log.e(TAG, "Can't clean up folder: " + cacheDirectory + "; " + ex.getMessage());
            }
        } else {
            Log.e(TAG, "Can't clean up folder: " + cacheDirectory + " does not exist");
        }
    }

    private void deleteRecursive(File fileOrDirectory) throws IOException {
        deleteRecursive(fileOrDirectory, 0);
    }

    private void deleteRecursive(File fileOrDirectory, int level) throws IOException {
        if (fileOrDirectory != null && fileOrDirectory.isDirectory() && fileOrDirectory.listFiles() != null && fileOrDirectory.listFiles().length > 0) {
            for (File child : fileOrDirectory.listFiles()) {
                if (!dontDeleteTheseFolders.contains(child.getName())) {
                    deleteRecursive(child, level + 1);
                }
            }
        }


        if (level != 0) {// don't delete top level folder
            if (!dontDeleteTheseFolders.contains(fileOrDirectory.getName())) {
                if (fileOrDirectory.isDirectory()){
                    Log.i(TAG, "erasing folder: " + fileOrDirectory.getAbsolutePath());
                } else {
                    Log.i(TAG, "erasing file: " + fileOrDirectory.getAbsolutePath() + " of size: " + fileOrDirectory.length());
                }
                if (!fileOrDirectory.delete()) {
                    Log.e(TAG, "couldn't erase: " + fileOrDirectory.getName());
                }
            } else {
                Log.i(TAG, "ignoring file: " + fileOrDirectory.getAbsolutePath() + " of size: " + fileOrDirectory.length());
            }
        }
    }

    public void startDownloadUpgradePackage(final OTAServerManager serverManager) {
        boolean isAvailabledownloadReference = Preference.getBoolean(context, context.getResources().getString(R.string.download_manager_reference_id_available));
        if (!isAvailabledownloadReference) {
            if (asyncTask != null) {
                Log.i(TAG, "Canceling existing task");
                asyncTask.cancel(true);
            }

            //If there's an update.zip(any other) downloaded previously to /cache/ota/, that will be deleted.
            File targetDir = new File(FileUtils.getOTAPackageFilePath());
            if (targetDir.exists()) {
                try {
                    org.apache.commons.io.FileUtils.cleanDirectory(targetDir);
                } catch (IOException ex) {
                    Log.e(TAG, "Can't clean up folder: " + targetDir + "; " + ex.getMessage());
                }
            }

            //If there are any files (downloaded previously) in /cache, that will be deleted.
            String previousOTAFile = Preference.getString(context, context.getResources().
                    getString(R.string.firmware_upgrade_file_name_pref));
            if (previousOTAFile != null) {
                File previousFile = new File(FileUtils.getUpgradePackageDirectory() + File.separator + previousOTAFile);
                if (previousFile.exists()) {
                    previousFile.delete();
                    Log.i(TAG, "Old update has been deleted.");
                }
            }
            //Apart from the above deletions, this method will carry further /cache clearance
            clearCacheDirectory();
        }

        asyncTask = new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... unused) {
                final DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                //Checks whether there are any pending download references
                boolean isAvailabledownloadReference = Preference.getBoolean(context, context.getResources().getString(R.string.download_manager_reference_id_available));
                if (!isAvailabledownloadReference) {
                    Log.i(TAG, "Firmware download started");
                    Preference.putString(context, context.getResources().getString(R.string.upgrade_download_status),
                            Constants.Status.OTA_UPGRADE_ONGOING);

                    URL url = serverConfig.getPackageURL();
                    Log.d(TAG, "Start downloading package:" + url.toString());

                    Uri downloadUri = Uri.parse(url.toString());
                    DownloadManager.Request request = new DownloadManager.Request(downloadUri);
                    // Types of networks over which this download may proceed.
                    request.setAllowedOverMetered(true);
                    // Set the title of this download, to be displayed in notifications
                    if (Constants.OTA_DOWNLOAD_PROGRESS_BAR_ENABLED) {
                        request.setVisibleInDownloadsUi(true);
                        request.setTitle("Downloading firmware upgrade");
                        request.setDescription("WSO2 Agent");
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
                    } else {
                        request.setVisibleInDownloadsUi(false);
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
                    }

                    request.setDestinationToSystemCache();
                    //Save the timestamp when the download is started (initially).
                    startTimeStamp = Calendar.getInstance().getTime().getTime();
                    downloadReference = downloadManager.enqueue(request);
                    Preference.putLong(context, context.getResources().getString(R.string.download_manager_reference_id), downloadReference);
                    Preference.putBoolean(context, context.getResources().getString(R.string.download_manager_reference_id_available), true);
                    Preference.putLong(context, context.getResources().getString(R.string.download_manager_start_time), startTimeStamp);
                    if (Constants.DEBUG_MODE_ENABLED) {
                        Log.d(TAG, "download manager reference id: " + String.valueOf(Preference.getLong(context, context.getResources().getString(R.string.download_manager_reference_id))));
                    }
                } else {
                    Log.i(TAG, "Resuming firmware download after reboot");
                    downloadReference = Preference.getLong(context, context.getResources().getString(R.string.download_manager_reference_id));
                    //Save the timestamp if the download is resumed after a reboot or any other interrupt.
                    startTimeStamp = Preference.getLong(context, context.getResources().getString(R.string.download_manager_start_time));
                }

                //Download monitoring thread.
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        boolean downloading = true;
                        boolean isFileNameAvailable = false;
                        int progress = 0;
                        int previousPercentage = 0;
                        String otaPackageName = null;

                        long pauseTimeStamp = 0l;
                        boolean isPaused = false;
                        int cursorFixAttempts = 0;
                        Cursor cursor = null;
                        DownloadManager.Query query = null;

                        sendNotification(OTA_NOTIFICATION_ID_START, context.getString(R.string.txt_ota_start_download_text), context.getString(R.string.txt_ota_start_download_title), true);


                        while (downloading) {
                            downloadOngoing = true;

                            query = new DownloadManager.Query();
                            query.setFilterById(downloadReference);
                            cursor = downloadManager.query(query);

                            if (cursor != null && cursor.moveToFirst()) {
                                lengthOfFile = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                            } else {
                                cursorFixAttempts++;
                                if (cursor != null) {
                                    cursor.close();
                                }
                                if (cursorFixAttempts == CURSOR_ATTEMPTS) {
                                    downloadManager.remove(downloadReference);
                                    Preference.putBoolean(context, context.getResources().getString(R.string.download_manager_reference_id_available), false);
                                    Preference.putLong(context, context.getResources().getString(R.string.download_manager_reference_id), -1);
                                    String message = "Android Database cursor error; aborting";
                                    Log.e(TAG, message);
                                    CommonUtils.sendBroadcast(context, Constants.Operation.UPGRADE_FIRMWARE, Constants.Code.FAILURE,
                                            Constants.Status.INTERNAL_ERROR, message);
                                    CommonUtils.callAgentApp(context, Constants.Operation.FAILED_FIRMWARE_UPGRADE_NOTIFICATION, Preference.getInt(
                                            context, context.getResources().getString(R.string.operation_id)), message);
                                    Preference.putString(context, context.getResources().getString(R.string.upgrade_download_status),
                                            Constants.Status.INTERNAL_ERROR);
                                    if (serverManager.stateChangeListener != null) {
                                        serverManager.stateChangeListener.onStateOrProgress(OTAStateChangeListener.STATE_IN_DOWNLOADING,
                                                OTAStateChangeListener.ERROR_PACKAGE_INSTALL_FAILED, null, DEFAULT_STATE_INFO_CODE);
                                    }
                                    break;
                                }
                                Log.e(TAG, "Cursor is null or empty");
                                continue;
                            }

                            //Get the OTA download file name and stored it in shared preference "firmware_upgrade_file_name_pref"
                            otaPackageName = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
                            if (otaPackageName != null && !otaPackageName.isEmpty()) {
                                if (!isFileNameAvailable) {
                                    Preference.putString(context, context.getResources().
                                            getString(R.string.firmware_upgrade_file_name_pref), otaPackageName);
                                    isFileNameAvailable = true;
                                }
                            }

                            //This will setup a total time for the upgrade firmware operation. Firmware operation needs to be
                            //completed within this time limit otherwise the operation will be aborted.
                            if ((Calendar.getInstance().getTime().getTime() - startTimeStamp) > Constants.FIRMWARE_DOWNLOAD_TIMEOUT) {
                                downloadManager.remove(downloadReference);
                                Preference.putBoolean(context, context.getResources().getString(R.string.download_manager_reference_id_available), false);
                                Preference.putLong(context, context.getResources().getString(R.string.download_manager_reference_id), -1);
                                String message = "Download took more than the maximum allowed time; aborting";
                                sendNotification(OTA_NOTIFICATION_ID_START, context.getString(R.string.txt_ota_failure_download_text), context.getString(R.string.txt_ota_start_download_title), false);
                                Log.e(TAG, message);
                                CommonUtils.sendBroadcast(context, Constants.Operation.UPGRADE_FIRMWARE, Constants.Code.FAILURE,
                                        Constants.Status.CONNECTION_FAILED, message);
                                CommonUtils.callAgentApp(context, Constants.Operation.FAILED_FIRMWARE_UPGRADE_NOTIFICATION, Preference.getInt(
                                        context, context.getResources().getString(R.string.operation_id)), message);
                                Preference.putString(context, context.getResources().getString(R.string.upgrade_download_status),
                                        Constants.Status.CONNECTION_FAILED);
                                if (serverManager.stateChangeListener != null) {
                                    serverManager.stateChangeListener.onStateOrProgress(OTAStateChangeListener.STATE_IN_DOWNLOADING,
                                            OTAStateChangeListener.ERROR_PACKAGE_INSTALL_FAILED, null, DEFAULT_STATE_INFO_CODE);
                                }
                                downloading = false;
                                downloadReference = 0L;
                                cursor.close();
                                return;
                            }

                            int bytesDownloaded = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));

                            //Checks whether there is enough storage capacity to download the ota file
                            if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON)) == DownloadManager.ERROR_INSUFFICIENT_SPACE) {
                                downloadManager.remove(downloadReference);
                                Preference.putBoolean(context, context.getResources().getString(R.string.download_manager_reference_id_available), false);
                                Preference.putLong(context, context.getResources().getString(R.string.download_manager_reference_id), -1);
                                String message = "Device does not have enough memory to download the OTA update";
                                CommonUtils.sendBroadcast(context, Constants.Operation.UPGRADE_FIRMWARE, Constants.Code.FAILURE,
                                        Constants.Status.LOW_DISK_SPACE, message);
                                CommonUtils.callAgentApp(context, Constants.Operation.FIRMWARE_UPGRADE_FAILURE,
                                        Preference.getInt(context, context.getResources().getString(R.string.operation_id)), message);
                                cursor.close();
                                break;
                            } else if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.
                                    STATUS_SUCCESSFUL) {
                                Preference.putBoolean(context, context.getResources().getString(R.string.download_manager_reference_id_available), false);
                                Preference.putLong(context, context.getResources().getString(R.string.download_manager_reference_id), -1);
                                cursor.close();
                                renameDownloadedFile(otaPackageName);
                                Preference.putString(context, context.getResources().getString(R.string.upgrade_download_status),
                                        context.getResources().getString(R.string.status_success));
                                Log.i(TAG, "Download successful");
                                if (serverManager.stateChangeListener != null) {
                                    serverManager.stateChangeListener.onStateOrProgress(OTAStateChangeListener.STATE_IN_DOWNLOADING,
                                            DEFAULT_STATE_ERROR_CODE, null, DEFAULT_STATE_INFO_CODE);
                                }
                                break;
                            } else if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.
                                    STATUS_PAUSED) {
                                if (!isPaused) {
                                    int reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));
                                    String reasonString = "Unknown";
                                    switch (reason) {
                                        case DownloadManager.PAUSED_QUEUED_FOR_WIFI:
                                            reasonString = "PAUSED_QUEUED_FOR_WIFI";
                                            break;
                                        case DownloadManager.PAUSED_UNKNOWN:
                                            reasonString = "PAUSED_UNKNOWN";
                                            break;
                                        case DownloadManager.PAUSED_WAITING_FOR_NETWORK:
                                            reasonString = "PAUSED_WAITING_FOR_NETWORK";
                                            break;
                                        case DownloadManager.PAUSED_WAITING_TO_RETRY:
                                            reasonString = "PAUSED_WAITING_TO_RETRY";
                                            break;
                                    }
                                    Log.w(TAG, "Download paused. Reason: " + reasonString + " (code: " + reason + ")");
                                    sendNotification(OTA_NOTIFICATION_ID_START, context.getString(R.string.txt_ota_paused_text), context.getString(R.string.txt_ota_start_download_title), true);
                                }
                                isPaused = true;
                                cursor.close();
                            } else if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.
                                    STATUS_FAILED) {
                                downloadManager.remove(downloadReference);
                                Preference.putBoolean(context, context.getResources().getString(R.string.download_manager_reference_id_available), false);
                                Preference.putLong(context, context.getResources().getString(R.string.download_manager_reference_id), -1);
                                int columnReason = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                                int reason = cursor.getInt(columnReason);
                                String reasonString = "Unknown";
                                switch (reason) {
                                    case DownloadManager.ERROR_CANNOT_RESUME:
                                        reasonString = "ERROR_CANNOT_RESUME";
                                        break;
                                    case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                                        reasonString = "ERROR_DEVICE_NOT_FOUND";
                                        break;
                                    case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                                        reasonString = "ERROR_FILE_ALREADY_EXISTS";
                                        break;
                                    case DownloadManager.ERROR_FILE_ERROR:
                                        reasonString = "ERROR_FILE_ERROR";
                                        break;
                                    case DownloadManager.ERROR_HTTP_DATA_ERROR:
                                        reasonString = "ERROR_HTTP_DATA_ERROR";
                                        break;
                                    case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                                        reasonString = "ERROR_INSUFFICIENT_SPACE";
                                        break;
                                    case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                                        reasonString = "ERROR_TOO_MANY_REDIRECTS";
                                        break;
                                    case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                                        reasonString = "ERROR_UNHANDLED_HTTP_CODE";
                                        break;
                                }

                                String message = "Download failed. Reason: " + reasonString + " (code: " + reason + ")";
                                Log.e(TAG, message);

                                Preference.putString(context, context.getResources().getString(R.string.upgrade_download_status),
                                        Constants.Status.OTA_DOWNLOAD_FAILED);
                                CommonUtils.sendBroadcast(context, Constants.Operation.UPGRADE_FIRMWARE, Constants.Code.FAILURE,
                                        Constants.Status.CONNECTION_FAILED, message);
                                CommonUtils.callAgentApp(context, Constants.Operation.FAILED_FIRMWARE_UPGRADE_NOTIFICATION, Preference.getInt(
                                        context, context.getResources().getString(R.string.operation_id)), message);
                                reportDownloadError(OTAStateChangeListener.ERROR_WRITE_FILE_ERROR);
                                cursor.close();
                                break;
                            } else if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.
                                    STATUS_RUNNING) {

                                if (isPaused) {
                                    Log.i(TAG, "Download resuming.");
                                    sendNotification(OTA_NOTIFICATION_ID_START, context.getString(R.string.txt_ota_start_download_text), context.getString(R.string.txt_ota_start_download_title), true);
                                }
                                isPaused = false;

                                int downloadProgress = 0;
                                if (lengthOfFile > 0) {
                                    downloadProgress = (int) ((bytesDownloaded * 100l) / lengthOfFile);
                                }

                                if (downloadProgress >= previousPercentage + Constants.OTA_DOWNLOAD_PERCENTAGE_FACTOR) {
                                    previousPercentage = downloadProgress;
                                    if (Constants.DEBUG_MODE_ENABLED) {
                                        Log.i(TAG, "download progress: " + downloadProgress + "%");
                                    }
                                    progress = downloadProgress;
                                    Preference.putString(context, context.getResources().getString(R.string.firmware_download_progress),
                                            String.valueOf(progress));

                                    String progressMessage =  downloadProgress + "% downloaded";
                                    CommonUtils.sendBroadcast(context, Constants.Operation.UPGRADE_FIRMWARE, Constants.Code.SUCCESS,
                                            Constants.Status.OTA_UPGRADE_ONGOING, progressMessage);
                                    CommonUtils.callAgentApp(context, Constants.Operation.FIRMWARE_IMAGE_DOWNLOADING, Preference.getInt(
                                            context, context.getResources().getString(R.string.operation_id)), progressMessage);

                                }

                                Preference.putString(context, context.getResources().getString(R.string.upgrade_download_status),
                                        Constants.Status.OTA_UPGRADE_ONGOING);

                            }
                            try {
                                if (!cursor.isClosed())
                                    cursor.close();
                            } catch (Exception ex) {
                                Log.e(TAG, "Error closing the cursor");
                            }
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                Log.e(TAG, e.getMessage());
                            }
                        }
                        downloadOngoing = false;
                        mNotificationManager.cancel(OTA_NOTIFICATION_ID_START);
                        mNotificationManager.cancel(OTA_NOTIFICATION_ID_PROGRESS);
                    }
                }
                ).start();

                return null;
            }
        }.executeOnExecutor(threadPoolExecutor);
    }

    private void sendNotification(int id, String messageText, String messageTitle, boolean ongoing){
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context)
                        .setSmallIcon(R.drawable.notification)
                        .setContentTitle(messageTitle)
                        .setContentText(messageText)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(messageText))
                        .setPriority(android.app.Notification.PRIORITY_MAX)
                        .setOngoing(ongoing)
                        .setOnlyAlertOnce(true);


        mNotificationManager.notify(id, builder.build());
    }

    public void startVerifyUpgradePackage() {
        Preference.putBoolean(context, context.getResources().getString(R.string.verification_failed_flag), false);
        File recoveryFile = new File(FileUtils.getUpgradePackageFilePath());
        try {
            wakeLock.acquire();
            Log.d(TAG, "Verifying upgrade package");
            RecoverySystem.verifyPackage(recoveryFile, recoveryVerifyListener, null);
        } catch (IOException e) {
            reportInstallError(OTAStateChangeListener.ERROR_PACKAGE_VERIFY_FAILED);
            String message = "Update verification failed due to file error.";
            Log.e(TAG, message);
        } catch (GeneralSecurityException e) {
            reportInstallError(OTAStateChangeListener.ERROR_PACKAGE_VERIFY_FAILED);
            String message = "Update verification failed due to security check failure.";
            Log.e(TAG, message);
        } finally {
            wakeLock.release();
        }
    }

    public OTAServerConfig getServerConfig() {
        return this.serverConfig;
    }

    public void startInstallUpgradePackage() {
        Preference.putString(context, context.getResources().getString(R.string.upgrade_download_status), Constants.Status.SUCCESSFUL);
        Preference.putString(context, context.getResources().getString(R.string.upgrade_install_status), Constants.Status.REQUEST_PLACED);
        File recoveryFile = new File(FileUtils.getUpgradePackageFilePath());
        try {
            wakeLock.acquire();
            boolean isAutomaticRetryEnabled = Preference.getBoolean(context, context.getResources().getString(R.string.firmware_upgrade_automatic_retry));
            if (getBatteryLevel(context) >= Constants.REQUIRED_BATTERY_LEVEL_TO_FIRMWARE_UPGRADE) {
                Log.d(TAG, "Installing upgrade package");
                if (isAutomaticRetryEnabled || Constants.SILENT_FIRMWARE_INSTALLATION) {
                    CommonUtils.callAgentApp(context, Constants.Operation.FIRMWARE_UPGRADE_COMPLETE, Preference.getInt(
                            context, context.getResources().getString(R.string.operation_id)), "Starting firmware upgrade");
                    Log.d(TAG, "Starting firmware upgrade");
                    RecoverySystem.installPackage(context, recoveryFile);
                } else {
                    setNotification(context, context.getResources().getString(R.string.ask_from_user_to_install_firmware), true);
                }
            } else if (isAutomaticRetryEnabled) {
                Preference.putString(context, context.getResources().getString(R.string.upgrade_install_status),
                                     Constants.Status.BATTERY_LEVEL_INSUFFICIENT_TO_INSTALL);
                Log.e(TAG, "Upgrade installation differed due to insufficient battery level.");
                setNotification(context, context.getResources().getString(R.string.upgrade_differed_due_to_battery), false);
            } else {
                Preference.putString(context, context.getResources().getString(R.string.upgrade_install_status),
                        Constants.Status.BATTERY_LEVEL_INSUFFICIENT_TO_INSTALL);
                Log.e(TAG, "Upgrade installation failed due to insufficient battery level.");
                setNotification(context, context.getResources().getString(R.string.upgrade_failed_due_to_battery), false);
            }
        } catch (IOException e) {
            reportInstallError(OTAStateChangeListener.ERROR_PACKAGE_INSTALL_FAILED);
            String message = "Update installation failed due to file error.";
            Log.e(TAG, message + e);
            CommonUtils.sendBroadcast(context, Constants.Operation.UPGRADE_FIRMWARE, Constants.Code.FAILURE,
                    Constants.Status.OTA_IMAGE_VERIFICATION_FAILED, message);
            CommonUtils.callAgentApp(context, Constants.Operation.FAILED_FIRMWARE_UPGRADE_NOTIFICATION, Preference.getInt(
                    context, context.getResources().getString(R.string.operation_id)), message);
        } catch (SecurityException e) {
            reportInstallError(OTAStateChangeListener.ERROR_PACKAGE_INSTALL_FAILED);
            String message = "Update installation failure due to security check failure.";
            Log.e(TAG, message + e);
            CommonUtils.sendBroadcast(context, Constants.Operation.UPGRADE_FIRMWARE, Constants.Code.FAILURE,
                    Constants.Status.OTA_IMAGE_VERIFICATION_FAILED, message);
            CommonUtils.callAgentApp(context, Constants.Operation.FAILED_FIRMWARE_UPGRADE_NOTIFICATION, Preference.getInt(
                    context, context.getResources().getString(R.string.operation_id)), message);
        } finally {
            wakeLock.release();
        }

    }

    private void setNotification(Context context, String notificationMessage, boolean isUserInput) {
        int requestID = (int) System.currentTimeMillis();
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent notificationIntent = new Intent(context, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, requestID,notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle("Message from EMM")
                .setStyle(new NotificationCompat.BigTextStyle()
                                  .bigText(notificationMessage))
                .setContentText(notificationMessage).setAutoCancel(true);

        if (isUserInput) {
            Intent installReceive = new Intent(context, NotificationActionReceiver.class);
            installReceive.setAction(Constants.FIRMWARE_INSTALL_CONFIRM_ACTION);
            PendingIntent installIntent = PendingIntent.getBroadcast(context, requestID, installReceive, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.addAction(R.drawable.ic_done_black_24dp, "Install", installIntent);

            Intent cancelReceive = new Intent(context, NotificationActionReceiver.class);
            cancelReceive.setAction(Constants.FIRMWARE_INSTALL_CANCEL_ACTION);
            PendingIntent cancelIntent = PendingIntent.getBroadcast(context, requestID, cancelReceive, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.addAction(R.drawable.ic_block_black_24dp, "Cancel", cancelIntent);
        }

        mBuilder.setContentIntent(contentIntent);
        mNotificationManager.notify(Constants.DEFAULT_NOTIFICATION_CODE, mBuilder.build());
    }

    private int getBatteryLevel(Context context) {
        Intent batteryIntent = context.registerReceiver(null,
                                                        new IntentFilter(
                                                                Intent.ACTION_BATTERY_CHANGED));
        int level = 0;
        if (batteryIntent != null) {
            level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        }

        return level;
    }

    /**
     * Downloads the property list from remote site, and parse it to property list.
     * The caller can parse this list and get information.
     */
    public void getTargetPackagePropertyList(final URL url) {

        final String  operation = Preference.getBoolean(context, context.getResources().getString(R.string.
                firmware_status_check_in_progress)) ? Constants.Operation.GET_FIRMWARE_UPGRADE_PACKAGE_STATUS : Constants.Operation.UPGRADE_FIRMWARE;

        if (asyncTask != null){
            asyncTask.cancel(true);
        }
        asyncTask = new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... param) {
                InputStream reader = null;
                ByteArrayOutputStream writer = null;
                BuildPropParser parser = null;
                final int bufSize = 1024;

                // First, trying to download the property list file. the build.prop of target image.
                try {
                    URLConnection urlConnection;

                    /* Use the URL configuration to open a connection
                       to the OTA server */
                    urlConnection = url.openConnection();
                    urlConnection.setConnectTimeout(Constants.FIRMWARE_UPGRADE_CONNECTIVITY_TIMEOUT);
                    urlConnection.setReadTimeout(Constants.FIRMWARE_UPGRADE_READ_TIMEOUT);
                    /* Since you get a URLConnection, use it to get the
                                   InputStream */
                    reader = urlConnection.getInputStream();

                    /* Now that the InputStream is open, get the content
                                   length */
                    final int contentLength = urlConnection.getContentLength();
                    byte[] buffer = new byte[bufSize];

                    if (contentLength != -1) {
                        writer = new ByteArrayOutputStream(contentLength);
                    } else {
                        writer = new ByteArrayOutputStream(DEFAULT_STREAM_LENGTH);
                    }

                    int totalBufRead = 0;
                    int bytesRead;
                    Timer timer = new Timer();
                    Log.d(TAG, "Start download: " + url.toString() + " to buffer");

                    while ((bytesRead = reader.read(buffer)) > 0) {
                        // Write current segment into byte output stream
                        writer.write(buffer, 0, bytesRead);
                        Log.d(TAG, "wrote " + bytesRead + " into byte output stream");
                        totalBufRead += bytesRead;
                        buffer = new byte[bufSize];
                        timer.cancel();
                        timer = new Timer();
                        timer.schedule(new Timeout(this), Constants.FIRMWARE_UPGRADE_READ_TIMEOUT);
                    }

                    Log.d(TAG, "Download finished: " + (Integer.toString(totalBufRead)) + " bytes downloaded");

                    parser = new BuildPropParser(writer, context);
                    timer.cancel();
                } catch (SocketTimeoutException e) {
                    String message = "Connection failure (Socket timeout) when retrieving update package size.";
                    Log.e(TAG, message + e);
                    CommonUtils.sendBroadcast(context, operation, Constants.Code.FAILURE, Constants.Status.CONNECTION_FAILED, message);
                    CommonUtils.callAgentApp(context, Constants.Operation.FAILED_FIRMWARE_UPGRADE_NOTIFICATION, Preference.getInt(
                            context, context.getResources().getString(R.string.operation_id)), message);
                } catch (IOException e) {
                    String message = "Property list (build.prop) not found in the server.";
                    Log.e(TAG, message + e);
                    CommonUtils.sendBroadcast(context, operation, Constants.Code.FAILURE, Constants.Status.FILE_NOT_FOUND, message);
                    CommonUtils.callAgentApp(context, Constants.Operation.FAILED_FIRMWARE_UPGRADE_NOTIFICATION, Preference.getInt(
                            context, context.getResources().getString(R.string.operation_id)), message);
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to close buffer reader." + e);
                        }
                    }
                    if (writer != null) {
                        try {
                            writer.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to close buffer writer." + e);
                        }
                    }
                    if (parser != null) {
                        if (stateChangeListener != null) {
                            stateChangeListener.onStateOrProgress(OTAStateChangeListener.STATE_IN_CHECKED,
                                    OTAStateChangeListener.NO_ERROR, parser, DEFAULT_STATE_INFO_CODE);
                        }
                    } else {
                        reportCheckingError(OTAStateChangeListener.ERROR_CANNOT_FIND_SERVER);
                    }
                }
                return null;
            }
        }.executeOnExecutor(threadPoolExecutor);
    }


    public interface OTAStateChangeListener {

        int STATE_IN_CHECKED = 1;
        int STATE_IN_DOWNLOADING = 2;
        int STATE_IN_UPGRADING = 3;
        int MESSAGE_DOWNLOAD_PROGRESS = 4;
        int MESSAGE_VERIFY_PROGRESS = 5;
        int NO_ERROR = 0;
        int ERROR_WIFI_NOT_AVAILABLE = 1;
        int ERROR_CANNOT_FIND_SERVER = 2;
        int ERROR_PACKAGE_VERIFY_FAILED = 3;
        int ERROR_WRITE_FILE_ERROR = 4;
        int ERROR_PACKAGE_INSTALL_FAILED = 6;

        void onStateOrProgress(int message, int error, BuildPropParser parser, long info);

    }

}
