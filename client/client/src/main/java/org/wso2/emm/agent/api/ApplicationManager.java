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

package org.wso2.emm.agent.api;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.provider.Browser;
import android.util.Base64;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

import org.wso2.emm.agent.AndroidAgentException;
import org.wso2.emm.agent.R;
import org.wso2.emm.agent.beans.AppInstallRequest;
import org.wso2.emm.agent.beans.DeviceAppInfo;
import org.wso2.emm.agent.beans.Operation;
import org.wso2.emm.agent.beans.ServerConfig;
import org.wso2.emm.agent.proxy.IDPTokenManagerException;
import org.wso2.emm.agent.proxy.utils.ServerUtilities;
import org.wso2.emm.agent.utils.AlarmUtils;
import org.wso2.emm.agent.utils.AppInstallRequestUtil;
import org.wso2.emm.agent.utils.CommonUtils;
import org.wso2.emm.agent.utils.Constants;
import org.wso2.emm.agent.utils.Preference;
import org.wso2.emm.agent.utils.StreamHandler;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT;

/**
 * This class handles all the functionalities required for managing application
 * installation and un-installation.
 */
public class ApplicationManager {
    private static final int SYSTEM_APPS_DISABLED_FLAG = 0;
    private static final int MAX_URL_HASH = 32;
    private static final int COMPRESSION_LEVEL = 100;
    private static final int BUFFER_SIZE = 1024;
    private static final int READ_FAILED = -1;
    private static final int BUFFER_OFFSET = 0;
    private static final String TAG = ApplicationManager.class.getName();

    private Context context;
    private Resources resources;
    private PackageManager packageManager;
    private static long downloadReference = -1;
    private String appUrl;
    private static InputStreamVolleyRequest volleyDownloadRequest;

    private BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long referenceId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

            if (downloadReference == referenceId) {
                String downloadDirectoryPath = Environment.getExternalStoragePublicDirectory(Environment.
                                                                                         DIRECTORY_DOWNLOADS).getPath();
                File file = new File(downloadDirectoryPath, resources.getString(R.string.download_mgr_download_file_name));
                if (file.exists()) {
                    Preference.putString(context, context.getResources().getString(
                            R.string.app_install_status), Constants.AppState.DOWNLOAD_COMPLETED);
                    PackageManager pm = context.getPackageManager();
                    PackageInfo info = pm.getPackageArchiveInfo(downloadDirectoryPath + File.separator + resources.
                                                                    getString(R.string.download_mgr_download_file_name),
                                                                PackageManager.GET_ACTIVITIES);
                    if (info != null && info.packageName != null) {
                        Preference.putString(context, context.getResources().getString(R.string.shared_pref_installed_app),
                                             info.packageName);
                    }
                    Preference.putString(context, context.getResources().getString(R.string.shared_pref_installed_file),
                                         resources.getString(R.string.download_mgr_download_file_name));
                    triggerInstallation(Uri.fromFile(new File(downloadDirectoryPath + File.separator +
                                                              resources.getString(R.string.download_mgr_download_file_name))));
                } else {
                    Preference.putString(context, context.getResources().getString(
                            R.string.app_install_status), Constants.AppState.DOWNLOAD_FAILED);
                    Preference.putString(context, context.getResources().getString(
                            R.string.app_install_failed_message), "App file creation failed on the device.");
                }
            }
        }
    };

    public ApplicationManager(Context context) {
        this.context = context;
        this.resources = context.getResources();
        this.packageManager = context.getPackageManager();
    }

    /**
     * Returns a list of all the applications installed on the device.
     *
     * @return - List of applications which installed on the device.
     */
    public Map<String, DeviceAppInfo> getInstalledApps() {
        Map<String, DeviceAppInfo> appList = new HashMap<>();
        List<PackageInfo> packages = packageManager.getInstalledPackages(SYSTEM_APPS_DISABLED_FLAG);
        DeviceAppInfo app;

        for (PackageInfo packageInfo : packages) {
            if (Constants.ALLOW_SYSTEM_APPS_IN_APPS_LIST_RESPONSE) {
                app = new DeviceAppInfo();
                app.setAppname(packageInfo.applicationInfo.
                        loadLabel(packageManager).toString());
                app.setPackagename(packageInfo.packageName);
                app.setVersionName(packageInfo.versionName);
                app.setVersionCode(packageInfo.versionCode);
                app.setIsSystemApp(isSystemPackage(packageInfo));
                app.setIsRunning(isAppRunning(packageInfo.packageName));
                appList.put(packageInfo.packageName, app);
            } else if (!isSystemPackage(packageInfo)) {
                app = new DeviceAppInfo();
                app.setAppname(packageInfo.applicationInfo.
                        loadLabel(packageManager).toString());
                app.setPackagename(packageInfo.packageName);
                app.setVersionName(packageInfo.versionName);
                app.setVersionCode(packageInfo.versionCode);
                app.setIsSystemApp(false);
                app.setIsRunning(isAppRunning(packageInfo.packageName));
                appList.put(packageInfo.packageName, app);
            }
        }
        return appList;
    }

    public boolean isAppRunning(String packageName) {
        boolean isRunning = false;
        try {
            Process process = Runtime.getRuntime().exec("ps");
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            int read;
            char[] buffer = new char[4096];
            StringBuffer output = new StringBuffer();
            while ((read = in.read(buffer)) > 0) {
                output.append(buffer, 0, read);
                if (output.toString().contains(packageName)) {
                    isRunning = true;
                }
            }
            in.close();
            if (output.toString().contains(packageName)) {
                isRunning = true;
            }
        } catch (IOException e) {
            Log.e(TAG, "Running processes shell command failed execution." + e);
        } finally {
            return isRunning;
        }
    }

    /**
     * Returns a list of all the applications installed on the device by user.
     *
     * @return - List of applications which installed on the device by user.
     */
    public List<String> getInstalledAppsByUser() {
        List<String> packagesInstalledByUser = new ArrayList<>();
        int flags = PackageManager.GET_META_DATA;
        List<ApplicationInfo> applications = packageManager.getInstalledApplications(flags);
        for (ApplicationInfo appInfo : applications) {
            if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 1) {
                packagesInstalledByUser.add(appInfo.packageName);
            }
        }
        return packagesInstalledByUser;
    }

    /**
     * Returns the app name for a particular package name.
     *
     * @param packageName - Package name which you need the app name.
     * @return - Application name.
     */
    public String getAppNameFromPackage(String packageName) {
        String appName = null;
        List<PackageInfo> packages = packageManager.
                getInstalledPackages(SYSTEM_APPS_DISABLED_FLAG);
        for (PackageInfo packageInfo : packages) {
            if (packageName.equals(packageInfo.packageName)) {
                appName = packageInfo.applicationInfo.
                        loadLabel(packageManager).toString();
                break;
            }
        }

        return appName;
    }

    public boolean isPackageInstalled(String packagename) {
        try {
            if(packagename.startsWith("package:")) {
                packagename = packagename.replace("package:", "");
            }
            PackageInfo packageInfo = packageManager.
                    getPackageInfo(packagename, PackageManager.GET_ACTIVITIES);
            if (packageInfo != null) {
                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return false;
    }

    private void triggerInstallation(Uri fileUri) {
        if (Constants.SYSTEM_APP_ENABLED) {
            Preference.putLong(context, Constants.PreferenceFlag.INSTALLATION_INITIATED_AT,
                    Calendar.getInstance().getTimeInMillis());
            CommonUtils.callSystemApp(context, Constants.Operation.SILENT_INSTALL_APPLICATION, "",
                    fileUri.toString());
        } else {
            Preference.putString(context, context.getResources().getString(
                    R.string.app_install_status), Constants.AppState.INSTALLED);
            startInstallerIntent(fileUri);
        }
    }

    /**
     * Installs an application to the device.
     *
     * @param fileUri - File URI should be passed in as a String.
     */
    public void startInstallerIntent(Uri fileUri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(fileUri, resources.getString(R.string.application_mgr_mime));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Returns whether the app is a system app.
     *
     * @param packageInfo - Package of the app which you need the status.
     * @return - App status.
     */
    private boolean isSystemPackage(PackageInfo packageInfo) {
        return ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
    }

    /**
     * Returns a base64 encoded string for a particular image.
     *
     * @param drawable - Image as a Drawable object.
     * @return - Base64 encoded value of the drawable.
     */
    public String encodeImage(Drawable drawable) {
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, COMPRESSION_LEVEL, outStream);
        byte[] bitmapContent = outStream.toByteArray();
        String encodedImage = Base64.encodeToString(bitmapContent, Base64.NO_WRAP);
        StreamHandler.closeOutputStream(outStream, TAG);

        return encodedImage;
    }

    /**
     * Installs an application to the device.
     *
     * @param url - APK Url should be passed in as a String.
     * @param schedule - If update/installation is scheduled, schedule information should be passed.
     * @param operation - App installation operation.
     */
    public void installApp(String url, String schedule, Operation operation) {
        if (schedule != null && !schedule.trim().isEmpty() && !schedule.equals("undefined")) {
            try {
                AlarmUtils.setOneTimeAlarm(context, schedule, Constants.Operation.INSTALL_APPLICATION, operation, url, null);
            } catch (ParseException e) {
                Log.e(TAG, "One time alarm time string parsing failed." + e);
            }
            return; //Will call installApp method again upon alarm.
        }

        int operationId = 0;
        String operationCode = Constants.Operation.INSTALL_APPLICATION;

        if (operation != null) {
            // Get ongoing app installation operation details. These preferences are cleared during
            // reply payload creations which followed by application installation complete or error.
            operationId = Preference.getInt(context, context.getResources().getString(
                    R.string.app_install_id));
            operationCode = Preference.getString(context, context.getResources().getString(
                    R.string.app_install_code));

            if (operationId == operation.getId()) {
                Log.w(TAG, "Ignoring received operation as it has the same operation ID with ongoing operation.");
                return; //No point of putting same operation again to the pending queue. Hence ignoring.
            }

            //Check if there any ongoing operations in the state machine.
            if (operationId != 0 && operationCode != null) {
                AppInstallRequest appInstallRequest = new AppInstallRequest();
                appInstallRequest.setApplicationOperationId(operation.getId());
                appInstallRequest.setApplicationOperationCode(operation.getCode());
                appInstallRequest.setAppUrl(url);
                //Add installation operation to pending queue
                AppInstallRequestUtil.addPending(context, appInstallRequest);
                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, "Queued operation Id " + appInstallRequest.getApplicationOperationId());
                    Log.d(TAG, "Added downloadManagerRequest to pending queue as there is another installation ongoing.");
                }
                return; //Will call installApp method again once current installation completed.
            }
            operationId = operation.getId();
            operationCode = operation.getCode();
        }
        setupAppDownload(url, operationId, operationCode);
    }

    /**
     * Cancels ongoing download if there any.
     */
    public void cancelOngoingDownload(){
        if (downloadReference != -1 && isDownloadManagerAvailable(context)) {
            final DownloadManager downloadManager = (DownloadManager) context
                    .getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) {
                return;
            }
            downloadManager.remove(downloadReference);
            downloadReference = -1;
        } else {
            if (volleyDownloadRequest != null){
                volleyDownloadRequest.cancel();
                volleyDownloadRequest = null;
            }
        }
    }

    /**
     * Start app download for install on device.
     *
     * @param url           - APK Url should be passed in as a String.
     * @param operationId   - Id of the operation.
     * @param operationCode - Requested operation code.
     */
    public void setupAppDownload(String url, int operationId, String operationCode) {
        Preference.putInt(context, context.getResources().getString(
                R.string.app_install_id), operationId);
        Preference.putString(context, context.getResources().getString(
                                R.string.app_install_code), operationCode);

        if (url.contains(Constants.APP_DOWNLOAD_ENDPOINT) && Constants.APP_MANAGER_HOST != null) {
            url = url.substring(url.lastIndexOf("/"), url.length());
            this.appUrl = Constants.APP_MANAGER_HOST + Constants.APP_DOWNLOAD_ENDPOINT + url;
        } else if (url.contains(Constants.APP_DOWNLOAD_ENDPOINT)) {
            url = url.substring(url.lastIndexOf("/"), url.length());
            String ipSaved = Constants.DEFAULT_HOST;
            String prefIP = Preference.getString(context, Constants.PreferenceFlag.IP);
            if (prefIP != null) {
                ipSaved = prefIP;
            }
            ServerConfig utils = new ServerConfig();
            if (ipSaved != null && !ipSaved.isEmpty()) {
                utils.setServerIP(ipSaved);
                this.appUrl = utils.getAPIServerURL(context) + Constants.APP_DOWNLOAD_ENDPOINT + url;
            } else {
                String errorText = "There is no valid IP to contact the server";
                Preference.putString(context, context.getResources().getString(
                        R.string.app_install_status), Constants.AppState.DOWNLOAD_FAILED);
                Preference.putString(context, context.getResources().getString(
                        R.string.app_install_failed_message), errorText);
                Log.e(TAG, errorText);
                return;
            }
        } else {
            this.appUrl = url;
        }

        Preference.putLong(context, Constants.PreferenceFlag.DOWNLOAD_INITIATED_AT,
                Calendar.getInstance().getTimeInMillis());
        Preference.putString(context, context.getResources().getString(
                R.string.app_install_status), Constants.AppState.DOWNLOAD_STARTED);
        if (isDownloadManagerAvailable(context)) {
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Using download manager to download the application");
            }
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            context.registerReceiver(downloadReceiver, filter);
            removeExistingFile();
            downloadViaDownloadManager(this.appUrl, resources.getString(R.string.download_mgr_download_file_name));
        } else {
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Using local downloader to download the application");
            }
            downloadApp(this.appUrl);
        }
    }

    /**
     * Removes an application from the device.
     *
     * @param packageName - Application package name should be passed in as a String.
     */
    public void uninstallApplication(String packageName, String schedule) throws AndroidAgentException {
        if (packageName != null &&
                !packageName.contains(resources.getString(R.string.application_package_prefix))) {
            packageName = resources.getString(R.string.application_package_prefix) + packageName;
        }

        if(!this.isPackageInstalled(packageName)){
            String message = "Package is not installed in the device or invalid package name";
            Preference.putString(context, context.getResources().getString(R.string.app_uninstall_status), Constants.AppState.UNINSTALLED_FAILED);
            Preference.putString(context, context.getResources().getString(R.string.app_uninstall_failed_message), message);
            throw new AndroidAgentException("Package is not installed in the device");
        }

        if (schedule != null && !schedule.trim().isEmpty() && !schedule.equals("undefined")) {
            try {
                AlarmUtils.setOneTimeAlarm(context, schedule, Constants.Operation.UNINSTALL_APPLICATION, null, null, packageName);
            } catch (ParseException e) {
                Log.e(TAG, "One time alarm time string parsing failed." + e);
            }
            return; //Will call uninstallApplication method again upon alarm.
        }
        if (Constants.SYSTEM_APP_ENABLED) {
            Preference.putString(context, context.getResources().getString(R.string.app_uninstall_status), Constants.AppState.UNINSTALLED);
            Preference.putString(context, context.getResources().getString(R.string.app_uninstall_failed_message), null);
            CommonUtils.callSystemApp(context, Constants.Operation.SILENT_UNINSTALL_APPLICATION, "", packageName);
        } else {
            Preference.putString(context, context.getResources().getString(R.string.app_uninstall_status), Constants.AppState.UNINSTALLED);
            Preference.putString(context, context.getResources().getString(R.string.app_uninstall_failed_message), null);
            Uri packageURI = Uri.parse(packageName);
            Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageURI);
            uninstallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(uninstallIntent);
        }
    }

    /**
     * Creates a webclip on the device home screen.
     *
     * @param url   - URL should be passed in as a String.
     * @param title - Title(Web app title) should be passed in as a String.
     */
    public void manageWebAppBookmark(String url, String title, String operationType)
            throws AndroidAgentException {
        final Intent bookmarkIntent = new Intent();
        final Intent actionIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        long urlHash = url.hashCode();
        long uniqueId = (urlHash << MAX_URL_HASH) | actionIntent.hashCode();

        actionIntent.putExtra(Browser.EXTRA_APPLICATION_ID, Long.toString(uniqueId));
        bookmarkIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, actionIntent);
        bookmarkIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
        bookmarkIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                                Intent.ShortcutIconResource.fromContext(context,
                                                                        R.drawable.ic_bookmark)
        );
        if (operationType != null) {
            if (resources.getString(R.string.operation_install).equalsIgnoreCase(operationType)) {
                bookmarkIntent.
                        setAction(resources.getString(R.string.application_package_launcher_install_action));
            } else if (resources.getString(R.string.operation_uninstall).equalsIgnoreCase(operationType)) {
                bookmarkIntent.
                        setAction(resources.getString(R.string.application_package_launcher_uninstall_action));
            } else {
                throw new AndroidAgentException("Cannot create webclip due to invalid operation type.");
            }
        } else {
            bookmarkIntent.
                    setAction(resources.getString(R.string.application_package_launcher_install_action));
        }
        context.sendBroadcastAsUser(bookmarkIntent, android.os.Process.myUserHandle());
    }

    public List<ApplicationInfo> getInstalledApplications() {
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
    }

    public Operation getApplicationInstallationStatus(Operation operation, String status, String message) {
        switch (status) {
            case Constants.AppState.DOWNLOAD_STARTED:
                operation.setStatus(context.getResources().getString(R.string.operation_value_progress));
                operation.setOperationResponse("Application download started");
                break;
            case Constants.AppState.DOWNLOAD_RETRY:
                operation.setStatus(context.getResources().getString(R.string.operation_value_progress));
                operation.setOperationResponse(message);
                break;
            case Constants.AppState.DOWNLOAD_COMPLETED:
                operation.setStatus(context.getResources().getString(R.string.operation_value_progress));
                operation.setOperationResponse("Application download completed");
                break;
            case Constants.AppState.DOWNLOAD_FAILED:
                operation.setStatus(context.getResources().getString(R.string.operation_value_error));
                operation.setOperationResponse(message);
                break;
            case Constants.AppState.INSTALL_FAILED:
                operation.setStatus(context.getResources().getString(R.string.operation_value_error));
                operation.setOperationResponse(message);
                break;
            case Constants.AppState.INSTALLED:
                operation.setStatus(context.getResources().getString(R.string.operation_value_completed));
                operation.setOperationResponse("Application installation completed");
                break;
            case Constants.AppState.UNINSTALLED_FAILED:
                operation.setStatus(context.getResources().getString(R.string.operation_value_error));
                operation.setOperationResponse(message);
                break;
            case Constants.AppState.UNINSTALLED:
                operation.setStatus(context.getResources().getString(R.string.operation_value_completed));
                operation.setOperationResponse("Application uninstallation completed");
                break;
            default:
                operation.setStatus(context.getResources().getString(R.string.operation_value_error));
                operation.setOperationResponse(message);
        }
        return operation;
    }

    /**
     * Checks whether the DownloadManager is available on the device.
     *
     * @param context - Context of the calling activity.
     */
    public boolean isDownloadManagerAvailable(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setClassName(resources.getString(R.string.android_download_manager_ui_resolver),
                            resources.getString(R.string.android_download_manager_list_resolver));
        return context.getPackageManager().queryIntentActivities(intent,
                                                                 PackageManager.MATCH_DEFAULT_ONLY).size() > 0;
    }

    private void removeExistingFile() {
        String directory = Environment.getExternalStorageDirectory().getPath() +
                           resources.getString(R.string.application_mgr_download_location);
        File file = new File(directory);
        file.mkdirs();
        File outputFile = new File(file,
                                   resources.getString(R.string.application_mgr_download_file_name));

        if (outputFile.exists()) {
            outputFile.delete();
        }
    }

    /**
     * Initiate downloading via DownloadManager API.
     *
     * @param url     - File URL.
     * @param appName - Name of the application to be downloaded.
     */
    private void downloadViaDownloadManager(String url, String appName) {
        final DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri downloadUri = Uri.parse(url);
        DownloadManager.Request request = new DownloadManager.Request(downloadUri);

        // Restrict the types of networks over which this download may
        // proceed.
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI
                                       | DownloadManager.Request.NETWORK_MOBILE);
        // Set whether this download may proceed over a roaming connection.
        request.setAllowedOverRoaming(true);
        // Set the title of this download, to be displayed in notifications
        // (if enabled).
        request.setTitle(resources.getString(R.string.downloader_message_title));
        request.setVisibleInDownloadsUi(false);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
        // Set the local destination for the downloaded file to a path
        // within the application's external files directory
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, appName);
        // Enqueue a new download and same the referenceId
        downloadReference = downloadManager.enqueue(request);
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean downloading = true;
                while (downloading) {
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadReference);
                    Cursor cursor = downloadManager.query(query);
                    cursor.moveToFirst();
                    int bytesDownloaded = cursor.getInt(cursor.getColumnIndex(DownloadManager.
                                                                                      COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    int bytesTotal = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.
                            STATUS_SUCCESSFUL) {
                        downloading = false;
                    }
                    if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.
                            STATUS_FAILED) {
                        downloading = false;
                        Preference.putString(context, context.getResources().getString(
                                R.string.app_install_status), Constants.AppState.DOWNLOAD_FAILED);
                        Preference.putString(context, context.getResources().getString(
                                R.string.app_install_failed_message), "App download failed due to a connection issue.");
                    }
                    int downloadProgress = 0;
                    if (bytesTotal > 0) {
                        downloadProgress = (int) ((bytesDownloaded * 100l) / bytesTotal);
                    }
                    Preference.putString(context,
                            context.getResources().getString(R.string.app_download_progress),
                            String.valueOf(downloadProgress));
                    cursor.close();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Download manager monitoring interrupted.", e);
                    }
                }
                Preference.putString(context,
                        context.getResources().getString(R.string.app_download_progress), "100");
                Preference.putString(context, context.getResources().getString(
                        R.string.app_install_status), Constants.AppState.DOWNLOAD_COMPLETED);
            }
        }).start();
    }

    /**
     * Installs or updates an application to the device.
     *
     * @param url - APK Url should be passed in as a String.
     */
    private void downloadApp(String url) {
        RequestQueue queue = null;
        try {
            queue = ServerUtilities.getCertifiedHttpClient();
        } catch (IDPTokenManagerException e) {
            Log.e(TAG, "Failed to retrieve HTTP client", e);
        }

        volleyDownloadRequest = new InputStreamVolleyRequest(Request.Method.GET, url,
            new Response.Listener<byte[]>() {
                @Override
                public void onResponse(byte[] response) {
                    if (response != null) {
                        FileOutputStream outStream = null;
                        InputStream inStream = null;
                        try {
                            String directory = Environment.getExternalStorageDirectory().getPath() +
                                               resources.getString(R.string.application_mgr_download_location);
                            File file = new File(directory);
                            file.mkdirs();
                            File outputFile = new File(file,
                                               resources.getString(R.string.application_mgr_download_file_name));

                            if (outputFile.exists()) {
                                outputFile.delete();
                            }

                            outStream = new FileOutputStream(outputFile);
                            inStream = new ByteArrayInputStream(response);

                            byte[] buffer = new byte[BUFFER_SIZE];
                            int lengthFile;

                            while ((lengthFile = inStream.read(buffer)) != READ_FAILED) {
                                outStream.write(buffer, BUFFER_OFFSET, lengthFile);
                            }

                            String filePath = directory + resources.getString(R.string.application_mgr_download_file_name);
                            Preference.putString(context, context.getResources().getString(
                                    R.string.app_install_status), Constants.AppState.DOWNLOAD_COMPLETED);
                            triggerInstallation(Uri.fromFile(new File(filePath)));
                        } catch (IOException e) {
                            String error = "File download/save failure in App download.";
                            Log.e(TAG, error, e);
                            Preference.putString(context, context.getResources().getString(
                                    R.string.app_install_status), Constants.AppState.DOWNLOAD_FAILED);
                            Preference.putString(context, context.getResources().getString(
                                    R.string.app_install_failed_message), error);
                        } catch (IllegalArgumentException e) {
                            String error = "Error occurred while sending 'Get' request due to empty host name";
                            Log.e(TAG, error);
                            Preference.putString(context, context.getResources().getString(
                                    R.string.app_install_status), Constants.AppState.DOWNLOAD_FAILED);
                            Preference.putString(context, context.getResources().getString(
                                    R.string.app_install_failed_message), error);
                        } finally {
                            StreamHandler.closeOutputStream(outStream, TAG);
                            StreamHandler.closeInputStream(inStream, TAG);
                        }
                    } else {
                        Preference.putString(context, context.getResources().getString(
                                R.string.app_install_status), Constants.AppState.DOWNLOAD_FAILED);
                        Preference.putString(context, context.getResources().getString(
                                R.string.app_install_failed_message), "File download failed.");
                    }
                    volleyDownloadRequest = null;
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, error.toString());
                    Preference.putString(context, context.getResources().getString(
                            R.string.app_install_status), Constants.AppState.DOWNLOAD_FAILED);
                    Preference.putString(context, context.getResources().getString(
                            R.string.app_install_failed_message), error.toString());
                    volleyDownloadRequest = null;
                }
            }, null)
        {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Accept", "*/*");
                headers.put("User-Agent", "Mozilla/5.0 ( compatible ), Android");
                return headers;
            }

            @Override
            protected Response<byte[]> parseNetworkResponse(NetworkResponse response) {
                //Initialise local responseHeaders map with response headers received
                responseHeaders = response.headers;
                //Pass the response data here
                if (response.statusCode == 200) {
                    return Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response));
                } else {
                    VolleyError error = new VolleyError("Invalid application file URL.");
                    return Response.error(error);
                }
            }
        };

        volleyDownloadRequest.setRetryPolicy(new DefaultRetryPolicy(10000, 5, DEFAULT_BACKOFF_MULT) {
            public void retry(VolleyError error) throws VolleyError {
                String message = "Download failed due to '" + error.getLocalizedMessage() +
                        "'. Retrying to download again. Attempt: " + getCurrentRetryCount();
                Log.w(TAG, message);
                Preference.putString(context, context.getResources().getString(
                        R.string.app_install_status), Constants.AppState.DOWNLOAD_RETRY);
                Preference.putString(context, context.getResources().getString(
                        R.string.app_install_failed_message), message);
                super.retry(error);
            }
        });

        queue.add(volleyDownloadRequest);
    }

}
