/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.iot.agent.services.operation;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Path;
import android.location.Location;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gson.Gson;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPSClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.iot.agent.AlertActivity;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.api.ApplicationManager;
import org.wso2.iot.agent.api.DeviceInfo;
import org.wso2.iot.agent.api.RootChecker;
import org.wso2.iot.agent.api.RuntimeInfo;
import org.wso2.iot.agent.api.WiFiConfig;
import org.wso2.iot.agent.beans.Address;
import org.wso2.iot.agent.beans.AppRestriction;
import org.wso2.iot.agent.beans.Application;
import org.wso2.iot.agent.beans.ComplianceFeature;
import org.wso2.iot.agent.beans.DeviceAppInfo;
import org.wso2.iot.agent.beans.Notification;
import org.wso2.iot.agent.beans.Operation;
import org.wso2.iot.agent.beans.WifiProfile;
import org.wso2.iot.agent.events.beans.EventPayload;
import org.wso2.iot.agent.events.listeners.WifiConfigCreationListener;
import org.wso2.iot.agent.proxy.interfaces.APIResultCallBack;
import org.wso2.iot.agent.services.AgentDeviceAdminReceiver;
import org.wso2.iot.agent.services.DeviceInfoPayload;
import org.wso2.iot.agent.services.LogPublisherFactory;
import org.wso2.iot.agent.services.NotificationService;
import org.wso2.iot.agent.services.PolicyComplianceChecker;
import org.wso2.iot.agent.services.PolicyOperationsMapper;
import org.wso2.iot.agent.services.ResultPayload;
import org.wso2.iot.agent.services.location.DeviceLocation;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;


public abstract class OperationManager implements APIResultCallBack, VersionBasedOperations {

    private Context context;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName cdmDeviceAdmin;
    private ApplicationManager appList;
    private Resources resources;
    private ResultPayload resultBuilder;

    private ApplicationManager applicationManager;
    private NotificationService notificationService;

    private static final String TAG = OperationManager.class.getSimpleName();

    private static final String APP_INFO_TAG_NAME = "name";
    private static final String APP_INFO_TAG_PACKAGE = "package";
    private static final String APP_INFO_TAG_VERSION = "version";
    private static final String APP_INFO_TAG_SYSTEM = "isSystemApp";
    private static final String APP_INFO_TAG_RUNNING = "isActive";
    private static final String STATUS = "status";
    private static final String TIMESTAMP = "timestamp";

    private static final int DEFAULT_PASSWORD_LENGTH = 0;
    private static final int DEFAULT_VOLUME = 0;
    private static final int DEFAULT_FLAG = 0;
    private static final int DEFAULT_PASSWORD_MIN_LENGTH = 4;
    private static final long DAY_MILLISECONDS_MULTIPLIER = 24 * 60 * 60 * 1000;
    private static String[] AUTHORIZED_PINNING_APPS;
    private static String AGENT_PACKAGE_NAME;

    public OperationManager(Context context) {
        this.context = context;
        this.resources = context.getResources();
        this.devicePolicyManager =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        this.cdmDeviceAdmin = new ComponentName(context, AgentDeviceAdminReceiver.class);
        this.appList = new ApplicationManager(context.getApplicationContext());
        this.resultBuilder = new ResultPayload();
        AGENT_PACKAGE_NAME = context.getPackageName();
        AUTHORIZED_PINNING_APPS = new String[]{AGENT_PACKAGE_NAME};
        applicationManager = new ApplicationManager(context);
        notificationService = NotificationService.getInstance(context.getApplicationContext());
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "New OperationManager created.");
        }
    }

    /**
     * Methods used by child classes to retrieve values of private variables in Parent Class
     */

    /* Retrieve context resources. */
    public Resources getContextResources() {
        return resources;
    }

    /* Retrieve resultBuilder. */
    public ResultPayload getResultBuilder() {
        return resultBuilder;
    }

    /* Retrieve devicePolicyManager. */
    public DevicePolicyManager getDevicePolicyManager() {
        return devicePolicyManager;
    }

    /* Retrieve cdmDeviceAdmin */
    public ComponentName getCdmDeviceAdmin() {
        return cdmDeviceAdmin;
    }

    /* Retrieve default password length */
    public int getDefaultPasswordLength() {
        return DEFAULT_PASSWORD_LENGTH;
    }

    /* Retrieve appList */
    public ApplicationManager getAppList() {
        return appList;
    }

    /* Retrieve Default Password Minimum Length */
    public int getDefaultPasswordMinLength() {
        return DEFAULT_PASSWORD_MIN_LENGTH;
    }

    /* Retrieve Day Milliseconds Multiplier */
    public long getDayMillisecondsMultiplier() {
        return DAY_MILLISECONDS_MULTIPLIER;
    }

    /* Retrieve Context */
    public Context getContext() {
        return this.context;
    }

    /* Retrieve applicationManager */
    public ApplicationManager getApplicationManager() {
        return applicationManager;
    }

    /* Retrieve notification service instance*/
    public NotificationService getNotificationService() {
        return notificationService;
    }
    /* End of methods used by child classes to access private variables in Parent Class */

    /**
     * Retrieve device information.
     *
     * @param operation - Operation object.
     */
    public void getDeviceInfo(org.wso2.iot.agent.beans.Operation operation) throws AndroidAgentException {
        DeviceInfoPayload deviceInfoPayload = new DeviceInfoPayload(context);
        deviceInfoPayload.build();
        String replyPayload = deviceInfoPayload.getDeviceInfoPayload();
        operation.setOperationResponse(replyPayload);
        operation.setStatus(resources.getString(R.string.operation_value_completed));
        resultBuilder.build(operation);
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "getDeviceInfo executed.");
        }
    }

    /**
     * Retrieve location device information.
     *
     * @param operation - Operation object.
     */
    public void getLocationInfo(org.wso2.iot.agent.beans.Operation operation) throws AndroidAgentException {
        DeviceLocation deviceLocation = new DeviceLocation(context);
        Location currentLocation = deviceLocation.getCurrentLocation();
        Address currentAddress = deviceLocation.getCurrentAddress();

        JSONObject result = new JSONObject();
        try {
            if (currentLocation != null) {
                result.put(Constants.LocationInfo.LATITUDE, currentLocation.getLatitude());
                result.put(Constants.LocationInfo.LONGITUDE, currentLocation.getLongitude());

                if (currentAddress != null) {
                    result.put(Constants.LocationInfo.CITY, currentAddress.getCity());
                    result.put(Constants.LocationInfo.COUNTRY, currentAddress.getCountry());
                    result.put(Constants.LocationInfo.STATE, currentAddress.getState());
                    result.put(Constants.LocationInfo.STREET1, currentAddress.getStreet1());
                    result.put(Constants.LocationInfo.STREET2, currentAddress.getStreet2());
                    result.put(Constants.LocationInfo.ZIP, currentAddress.getZip());
                } else {
                    Log.e(TAG, "Address is not available for the given coordinates");
                }

                operation.setOperationResponse(result.toString());
                operation.setStatus(resources.getString(R.string.operation_value_completed));
                resultBuilder.build(operation);

                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, "Device location sent");
                }

            } else {
                operation.setStatus(resources.getString(R.string.operation_value_error));
                String errorMessage = "Location service is not enabled in the device";
                JSONObject errorResult = new JSONObject();
                errorResult.put(STATUS, errorMessage);
                errorResult.put(TIMESTAMP, Calendar.getInstance().getTime().toString());
                operation.setOperationResponse(errorMessage);
                resultBuilder.build(operation);
                Log.e(TAG, errorMessage);
            }
        } catch (JSONException e) {
            operation.setStatus(resources.getString(R.string.operation_value_error));
            resultBuilder.build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "getLocationInfo executed.");
        }
    }

    /**
     * Retrieve device application information.
     *
     * @param operation - Operation object.
     */
    public void getApplicationList(org.wso2.iot.agent.beans.Operation operation) throws AndroidAgentException {
        ArrayList<DeviceAppInfo> apps = new ArrayList<>(appList.getInstalledApps().values());
        JSONArray result = new JSONArray();
        RuntimeInfo runtimeInfo = new RuntimeInfo(context);
        Map<String, Application> applications = runtimeInfo.getAppMemory();
        for (DeviceAppInfo infoApp : apps) {
            JSONObject app = new JSONObject();
            try {
                Application application = applications.get(infoApp.getPackagename());
                app.put(APP_INFO_TAG_NAME, Uri.encode(infoApp.getAppname()));
                app.put(APP_INFO_TAG_PACKAGE, infoApp.getPackagename());
                app.put(APP_INFO_TAG_VERSION, infoApp.getVersionName());
                app.put(APP_INFO_TAG_SYSTEM, infoApp.isSystemApp());
                app.put(APP_INFO_TAG_RUNNING, infoApp.isRunning());
                if (application != null) {
                    app.put(Constants.Device.USS, application.getUss());
                }
                result.put(app);
            } catch (JSONException e) {
                operation.setStatus(resources.getString(R.string.operation_value_error));
                operation.setOperationResponse("Error in parsing application list.");
                resultBuilder.build(operation);
                throw new AndroidAgentException("Invalid JSON format.", e);
            }
        }
        operation.setOperationResponse(result.toString());
        operation.setStatus(resources.getString(R.string.operation_value_completed));
        resultBuilder.build(operation);

        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Application list sent");
        }
    }

    /**
     * Ring the device.
     *
     * @param operation - Operation object.
     */
    public void ringDevice(org.wso2.iot.agent.beans.Operation operation) {
        operation.setStatus(resources.getString(R.string.operation_value_completed));
        resultBuilder.build(operation);
        Intent intent = new Intent(context, AlertActivity.class);
        intent.putExtra(resources.getString(R.string.intent_extra_type),
                resources.getString(R.string.intent_extra_ring));
        intent.putExtra(resources.getString(R.string.intent_extra_message_text),
                resources.getString(R.string.intent_extra_stop_ringing));
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Ringing is activated on the device");
        }
    }

    private enum Protocol {
        HTTP("http"),
        FTP("ftp"),
        SFTP("sftp");
        private final String type;

        Protocol(String type) {
            this.type = type;
        }

        public String getValue() {
            return this.type;
        }
    }

    /**
     * Download the file to the device from the FTP server.
     *
     * @param operation - Operation object.
     */
    public void downloadFile(Operation operation) throws AndroidAgentException {
        String fileName = null;
        if (operation.getPayLoad() == null) {
            payloadNullError(operation);
        } else {
            try {
                JSONObject inputData = new JSONObject(operation.getPayLoad().toString());
                final String fileURL = inputData.getString("fileURL");
                final String ftpPassword = inputData.getString("ftpPassword");
                final String location = inputData.getString("fileLocation");
                String savingLocation;
                if (location.isEmpty()) {
                    savingLocation = getSavingLocation();
                    if (savingLocation == null) {
                        handleOperationError(operation, "Error in default saving location.");
                    }
                } else {
                    savingLocation = location;
                }
                if (fileURL.startsWith(Protocol.HTTP.getValue())) {
                    downloadFileUsingHTTPClient(operation, fileURL, savingLocation);
                } else {
                    String[] userInfo = urlSplitter(fileURL, false);
                    String ftpUserName = userInfo[0];
                    String fileDirectory = userInfo[1];
                    String host = userInfo[2];
                    int serverPort = Integer.parseInt(userInfo[3]);
                    String protocol = userInfo[4];
                    fileName = userInfo[5];
                    if (Constants.DEBUG_MODE_ENABLED) {
                        printLogs(ftpUserName, host, fileName, fileDirectory, savingLocation, serverPort);
                    }
                    if (protocol != null) {
                        selectDownloadClient(protocol, operation, host, ftpUserName, ftpPassword,
                                savingLocation, fileName, serverPort, fileDirectory);
                    } else {
                        handleOperationError(operation, "Invalid URL");
                    }
                }
            } catch (ArrayIndexOutOfBoundsException | JSONException | URISyntaxException e) {
                handleOperationError(operation, fileTransferExceptionHandler(e, fileName), e);
            } finally {
                Log.d(TAG, operation.getStatus());
                resultBuilder.build(operation);
            }
        }
    }

    private void selectDownloadClient(String protocol, Operation operation, String host, String ftpUserName,
                                      String ftpPassword, String savingLocation, String fileName,
                                      int serverPort, String fileDirectory) throws AndroidAgentException {
        switch (protocol) {
            case "sftp":
                downloadFileUsingSFTPClient(operation, host, ftpUserName, ftpPassword,
                        savingLocation, fileName, serverPort, fileDirectory);
                break;
            case "ftp":
                downloadFileUsingFTPClient(operation, host, ftpUserName, ftpPassword,
                        savingLocation, fileName, serverPort, fileDirectory);
                break;
            default:
                handleOperationError(operation, "Protocol(" + protocol + ") not supported.");
        }

    }

    private void payloadNullError(Operation operation) throws AndroidAgentException {
        String response = "Operation payload null.";
        operation.setOperationResponse(response);
        operation.setStatus(resources.getString(R.string.operation_value_error));
        resultBuilder.build(operation);
        throw new AndroidAgentException(response);
    }

    private void handleOperationError(Operation operation, String message)
            throws AndroidAgentException {
        operation.setStatus(resources.getString(R.string.operation_value_error));
        operation.setOperationResponse(message);
        throw new AndroidAgentException(message);
    }

    private void handleOperationError(Operation operation, String message, Exception exception)
            throws AndroidAgentException {
        operation.setStatus(resources.getString(R.string.operation_value_error));
        operation.setOperationResponse(message);
        throw new AndroidAgentException(message, exception);
    }

    private void printLogs(String ftpUserName, String host, String fileName,
                           String fileDirectory, String savingLocation, int serverPort) {
        Log.d(TAG, "FTP User Name: " + ftpUserName);
        Log.d(TAG, "FTP host address: " + host);
        Log.d(TAG, "FTP server port: " + serverPort);
        Log.d(TAG, "File name : " + fileName);
        Log.d(TAG, "File directory: " + fileDirectory);
        Log.d(TAG, "File upload directory: " + savingLocation);
    }

    /**
     * This method is used to download the file using HTTP client.
     *
     * @param operation      - Operation object.
     * @param url            - file url to download file.
     * @param savingLocation - location to save file in device.
     * @throws AndroidAgentException - Android agent exception.
     */
    private void downloadFileUsingHTTPClient(Operation operation, String url,
                                             String savingLocation) throws AndroidAgentException {
        InputStream inputStream = null;
        DataInputStream dataInputStream = null;
        FileOutputStream fileOutputStream = null;
        String fileName = "Unknown";
        try {
            fileName = url.substring(url.lastIndexOf('/') + 1);
            URL link = new URL(url);
            inputStream = link.openStream();
            dataInputStream = new DataInputStream(inputStream);
            byte[] buffer = new byte[1024];
            int length;
            fileOutputStream = new FileOutputStream(new File(savingLocation + File.separator + fileName));
            while ((length = dataInputStream.read(buffer)) > 0) {
                fileOutputStream.write(buffer, 0, length);
            }
            operation.setStatus(resources.getString(R.string.operation_value_completed));
            operation.setOperationResponse("File uploaded to the device successfully!");
        } catch (IOException e) {
            handleOperationError(operation, fileTransferExceptionHandler(e, fileName), e);
        } finally {
            cleanupStreams(inputStream);
            cleanupStreams(fileOutputStream, dataInputStream);
        }
    }

    private void cleanupStreams(FileOutputStream fileOutputStream, DataInputStream dataInputStream) {
        if (fileOutputStream != null) {
            try {
                fileOutputStream.close();
            } catch (IOException ignored) {
            }
        }
        if (dataInputStream != null) {
            try {
                dataInputStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void cleanupStreams(BufferedInputStream bufferedInputStream,
                                BufferedOutputStream bufferedOutputStream) {
        if (bufferedInputStream != null) {
            try {
                bufferedInputStream.close();
            } catch (IOException ignored) {
            }
        }
        if (bufferedOutputStream != null) {
            try {
                bufferedOutputStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void cleanupStreams(OutputStream outputStream, FileOutputStream fileOutputStream) {
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException ignored) {
            }
        }
        if (fileOutputStream != null) {
            try {
                fileOutputStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void cleanupStreams(InputStream inputStream) {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException ignored) {
            }
        }
    }


    private String getSavingLocation() {
        if (Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).exists()) {
            return Environment.getExternalStoragePublicDirectory(Environment.
                    DIRECTORY_DOWNLOADS).toString();
        }
        return null;
    }

    /**
     * This method downloads the file using sftp client.
     *
     * @param operation      - operation object.
     * @param host           - host address.
     * @param ftpUserName    - ftp user name.
     * @param ftpPassword    - ftp password.
     * @param savingLocation - location in the device to save the file.
     * @param fileName       - name of the file to download.
     * @param serverPort     - ftp server port.
     * @param fileDirectory  - the directory of the file in FTP server.
     * @throws AndroidAgentException - Android agent exception.
     */
    private void downloadFileUsingSFTPClient(Operation operation, String host,
                                             String ftpUserName, String ftpPassword,
                                             String savingLocation, String fileName, int serverPort,
                                             String fileDirectory) throws AndroidAgentException {
        Channel channel;
        Session session = null;
        ChannelSftp sftpChannel = null;
        BufferedInputStream bufferedInputStream = null;
        BufferedOutputStream bufferedOutputStream = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(ftpUserName, host, serverPort);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setPassword(ftpPassword);
            session.connect();
            channel = session.openChannel(Protocol.SFTP.getValue());
            channel.connect();
            sftpChannel = (ChannelSftp) channel;
            if (!fileDirectory.equals(File.separator)) {
                sftpChannel.cd(fileDirectory); //cd to dir that contains file
            }
            byte[] buffer = new byte[1024];
            bufferedInputStream = new BufferedInputStream(sftpChannel.get(fileName));
            File newFile = new File(savingLocation + File.separator + fileName);
            OutputStream outputStream = new FileOutputStream(newFile);
            bufferedOutputStream = new BufferedOutputStream(outputStream);
            int readCount;
            while ((readCount = bufferedInputStream.read(buffer)) > 0) {
                bufferedOutputStream.write(buffer, 0, readCount);
            }
            operation.setStatus(resources.getString(R.string.operation_value_completed));
            operation.setOperationResponse("File uploaded to the device successfully!");
        } catch (IOException | JSchException | SftpException e) {
            handleOperationError(operation, fileTransferExceptionHandler(e, fileName), e);
        } finally {
            if (sftpChannel != null) {
                sftpChannel.exit();
            }
            if (session != null) {
                session.disconnect();
            }
            cleanupStreams(bufferedInputStream, bufferedOutputStream);
        }
    }

    /**
     * This method downloads the file using sftp client.
     *
     * @param operation      - operation object.
     * @param host           - host address.
     * @param ftpUserName    - ftp user name.
     * @param ftpPassword    - ftp password.
     * @param savingLocation - location in the device to save the file.
     * @param fileName       - name of the file to download.
     * @param serverPort     - ftp server port.
     * @param fileDirectory  - the directory of the file in FTP server.
     * @throws AndroidAgentException - Android agent exception.
     */
    private void downloadFileUsingFTPClient(Operation operation, String host,
                                            String ftpUserName, String ftpPassword,
                                            String savingLocation, String fileName, int serverPort,
                                            String fileDirectory) throws AndroidAgentException {

        FTPClient ftpClient = new FTPClient();
        FileOutputStream fileOutputStream = null;
        OutputStream outputStream = null;
        String response;
        try {
            ftpClient.connect(host, serverPort);
            if (ftpClient.login(ftpUserName, ftpPassword)) {
                ftpClient.enterLocalPassiveMode();
                fileOutputStream = new FileOutputStream(savingLocation + File.separator + fileName);
                outputStream = new BufferedOutputStream(fileOutputStream);
                ftpClient.changeWorkingDirectory(fileDirectory);
                if (ftpClient.retrieveFile(fileName, outputStream)) {
                    response = "File uploaded to the device successfully!";
                    operation.setStatus(resources.getString(R.string.operation_value_completed));
                } else {
                    response = "File uploaded to the device not completed.";
                    operation.setStatus(resources.getString(R.string.operation_value_error));
                }
                operation.setOperationResponse(response);
            } else {
                downloadFileUsingFTPSClient(operation, host, ftpUserName, ftpPassword,
                        savingLocation, fileName, serverPort, fileDirectory);
            }
        } catch (FTPConnectionClosedException e) {
            downloadFileUsingFTPSClient(operation, host, ftpUserName, ftpPassword,
                    savingLocation, fileName, serverPort, fileDirectory);
        } catch (IOException e) {
            handleOperationError(operation, fileTransferExceptionHandler(e, fileName), e);
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (IOException ignored) {
            }
            cleanupStreams(outputStream, fileOutputStream);
        }
    }

    /**
     * This method downloads the file using sftp client.
     *
     * @param operation      - operation object.
     * @param host           - host address.
     * @param ftpUserName    - ftp user name.
     * @param ftpPassword    - ftp password.
     * @param savingLocation - location in the device to save the file.
     * @param fileName       - name of the file to download.
     * @param serverPort     - ftp server port.
     * @param fileDirectory  - the directory of the file in FTP server.
     * @throws AndroidAgentException - Android agent exception.
     */
    private void downloadFileUsingFTPSClient(Operation operation, String host,
                                             String ftpUserName, String ftpPassword,
                                             String savingLocation, String fileName, int serverPort,
                                             String fileDirectory) throws AndroidAgentException {
        FTPSClient ftpsClient = new FTPSClient();
        FileOutputStream fileOutputStream = null;
        OutputStream outputStream = null;
        String response;
        try {
            ftpsClient.connect(host, serverPort);
            if (ftpsClient.login(ftpUserName, ftpPassword)) {
                ftpsClient.enterLocalPassiveMode();
                ftpsClient.execPROT("P");
                fileOutputStream = new FileOutputStream(savingLocation + File.separator + fileName);
                outputStream = new BufferedOutputStream(fileOutputStream);
                ftpsClient.changeWorkingDirectory(fileDirectory);
                if (ftpsClient.retrieveFile(fileName, outputStream)) {
                    response = "File uploaded to the device successfully!";
                    operation.setStatus(resources.getString(R.string.operation_value_completed));
                } else {
                    response = "File uploaded to the device not completed.";
                    operation.setStatus(resources.getString(R.string.operation_value_error));
                }
            } else {
                response = "FTP login failed.";
                operation.setStatus(resources.getString(R.string.operation_value_error));
            }
            operation.setOperationResponse(response);
        } catch (IOException e) {
            handleOperationError(operation, fileTransferExceptionHandler(e, fileName), e);
        } finally {
            try {
                if (ftpsClient.isConnected()) {
                    ftpsClient.logout();
                    ftpsClient.disconnect();
                }
            } catch (IOException ignored) {
            }
            cleanupStreams(outputStream, fileOutputStream);
        }
    }

    /**
     * This method handles the exceptions formed by downloadFile and uploadFile classes.
     *
     * @param e        - Exception object.
     * @param fileName - name of the file which caused exception.
     */
    private String fileTransferExceptionHandler(Exception e, String fileName) {
        if (e.getCause() != null) {
            return fileName + " upload failed. Error :- " + e.getCause().getMessage();
        } else {
            return fileName + " upload failed. Error :- " + e.getLocalizedMessage();
        }
    }

    /**
     * This method splits the provided URL to get user information for downloadFile
     * and uploadFile methods.
     *
     * @param fileURL  - URL to split.
     * @param isUpload - fileURL corresponds to uploadFile or downloadFile operation.
     * @return - an array of ftpUserName, fileDirectory, host, serverPort, protocol &
     * fileName ( optional for isUpload = false ).
     * @throws URISyntaxException - Malformed URL.
     */
    private String[] urlSplitter(String fileURL, boolean isUpload) throws URISyntaxException {
        String serverPort = null;
        URI url = new URI(fileURL);
        String protocol = url.getScheme();
        String host = null;
        String ftpUserName = null;  // for anonymous FTP login.
        if (protocol != null) {
            if (protocol.equals(Protocol.FTP.getValue())) {
                serverPort = String.valueOf(21);
            } else if (protocol.equals(Protocol.SFTP.getValue())) {
                serverPort = String.valueOf(22);
            }
        }
        if (url.getAuthority() != null) {
            String[] authority = url.getAuthority().split("@"); //provides username@hostname
            host = authority[authority.length - 1];             // Since hostname cannot contain any '@' signs, it should be last element.
            if (authority.length > 1) {
                ftpUserName = url.getAuthority().substring(0, url.getAuthority().lastIndexOf(host) - 1);
            } else {
                ftpUserName = "anonymous"; // for anonymous FTP login.
            }
        }
        if (host != null && host.contains(":")) {
            serverPort = String.valueOf(host.split(":")[1]);
            host = host.split(":")[0];
        }
        if (isUpload) {
            return new String[]{ftpUserName, url.getPath(), host, serverPort, protocol};
        } else {
            File file = new File(url.getPath());
            return new String[]{ftpUserName, file.getParent(), host, serverPort, protocol, file.getName()};
        }
    }

    /**
     * Upload file to the FTP server from the device.
     *
     * @param operation - Operation object.
     */
    public void uploadFile(Operation operation) throws AndroidAgentException {
        String fileName = "Unknown";
        if (operation.getPayLoad() == null) {
            payloadNullError(operation);
        } else {
            try {
                JSONObject inputData = new JSONObject(operation.getPayLoad().toString());
                String fileURL = inputData.getString("fileURL");
                String ftpPassword = inputData.getString("ftpPassword");
                String fileLocation = inputData.getString("fileLocation");
                String[] userInfo = urlSplitter(fileURL, true);
                String ftpUserName = userInfo[0];
                String uploadDirectory = userInfo[1];
                String host = userInfo[2];
                int serverPort = Integer.parseInt(userInfo[3]);
                String protocol = userInfo[4];
                File file = new File(fileLocation);
                fileName = file.getName();
                if (Constants.DEBUG_MODE_ENABLED) {
                    printLogs(ftpUserName, host, fileName, file.getParent(), uploadDirectory, serverPort);
                }
                if (protocol != null) {
                    selectUploadClient(protocol, operation, host, ftpUserName, ftpPassword,
                            uploadDirectory, fileLocation, serverPort);
                } else {
                    handleOperationError(operation, "Invalid URL");
                }
            } catch (JSONException | URISyntaxException e) {
                handleOperationError(operation, fileTransferExceptionHandler(e, fileName), e);
            } finally {
                Log.d(TAG, operation.getStatus());
                resultBuilder.build(operation);
            }
        }
    }

    private void selectUploadClient(String protocol, Operation operation, String host, String ftpUserName,
                                    String ftpPassword, String uploadDirectory, String fileLocation,
                                    int serverPort) throws AndroidAgentException {
        switch (protocol) {
            case "sftp":
                uploadFileUsingSFTPClient(operation, host, ftpUserName, ftpPassword,
                        uploadDirectory, fileLocation, serverPort);
                break;
            case "ftp":
                uploadFileUsingFTPClient(operation, host, ftpUserName, ftpPassword,
                        uploadDirectory, fileLocation, serverPort);
                break;
            default:
                handleOperationError(operation, "Protocol ( " + protocol + " ) not supported.");
        }
    }

    /**
     * File upload operation using an FTP client.
     *
     * @param operation       - operation object.
     * @param host            - host name.
     * @param ftpUserName     - ftp user name.
     * @param ftpPassword     - ftp password.
     * @param uploadDirectory - ftp directory to upload file.
     * @param fileLocation    - local file location.
     * @param serverPort      - ftp port to connect.
     * @throws AndroidAgentException - AndroidAgent exception.
     */
    private void uploadFileUsingFTPClient(Operation operation, String host,
                                          String ftpUserName, String ftpPassword,
                                          String uploadDirectory, String fileLocation, int serverPort)
            throws AndroidAgentException {
        FTPClient ftpClient = new FTPClient();
        String fileName = null;
        InputStream inputStream = null;
        String response;
        try {
            File file = new File(fileLocation);
            fileName = file.getName();
            ftpClient.connect(host, serverPort);
            ftpClient.enterLocalPassiveMode();
            ftpClient.login(ftpUserName, ftpPassword);
            inputStream = new FileInputStream(file);
            ftpClient.changeWorkingDirectory(uploadDirectory);
            if (ftpClient.storeFile(file.getName(), inputStream)) {
                response = "File uploaded from the device completed.";
                operation.setStatus(resources.getString(R.string.operation_value_completed));
            } else {
                response = "File uploaded from the device not completed.";
                operation.setStatus(resources.getString(R.string.operation_value_error));
            }
            operation.setOperationResponse(response);
        } catch (FTPConnectionClosedException e) {
            uploadFileUsingFTPSClient(operation, host, ftpUserName, ftpPassword,
                    uploadDirectory, fileLocation, serverPort);
        } catch (IOException e) {
            handleOperationError(operation, fileTransferExceptionHandler(e, fileName), e);
        } finally {
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.logout();
                } catch (IOException ignored) {
                }
            }
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.disconnect();
                } catch (IOException ignored) {
                }
            }
            cleanupStreams(inputStream);
        }
    }

    /**
     * File upload operation using an FTPS explicit TLS client.
     *
     * @param operation       - operation object.
     * @param host            - host name.
     * @param ftpUserName     - ftp user name.
     * @param ftpPassword     - ftp password.
     * @param uploadDirectory - ftp directory to upload file.
     * @param fileLocation    - local file location.
     * @param serverPort      - ftp port to connect.
     * @throws AndroidAgentException - AndroidAgent exception.
     */
    private void uploadFileUsingFTPSClient(Operation operation, String host,
                                           String ftpUserName, String ftpPassword,
                                           String uploadDirectory, String fileLocation, int serverPort)
            throws AndroidAgentException {
        FTPSClient ftpsClient = new FTPSClient();
        String fileName = null;
        InputStream inputStream = null;
        Boolean loginResult = false;
        String response;
        try {
            File file = new File(fileLocation);
            fileName = file.getName();
            ftpsClient.connect(host, serverPort);
            ftpsClient.enterLocalPassiveMode();
            loginResult = ftpsClient.login(ftpUserName, ftpPassword);
            ftpsClient.execPROT("P");
            inputStream = new FileInputStream(file);
            ftpsClient.changeWorkingDirectory(uploadDirectory);
            if (ftpsClient.storeFile(fileName, inputStream)) {
                response = "File uploaded from the device completed.";
                operation.setStatus(resources.getString(R.string.operation_value_completed));
            } else {
                response = "File uploaded from the device not completed.";
                operation.setStatus(resources.getString(R.string.operation_value_error));
            }
            operation.setOperationResponse(response);
        } catch (IOException e) {
            if (!loginResult) {
                response = "FTP login failed.";
            } else {
                response = fileTransferExceptionHandler(e, fileName);
            }
            handleOperationError(operation, response, e);
        } finally {
            try {
                if (ftpsClient.isConnected()) {
                    ftpsClient.logout();
                    ftpsClient.disconnect();
                }
            } catch (IOException ignored) {
            }
            cleanupStreams(inputStream);
        }
    }

    /**
     * File upload operation using an SFTP client.
     *
     * @param operation       - operation object.
     * @param host            - host name.
     * @param ftpUserName     - ftp user name.
     * @param ftpPassword     - ftp password.
     * @param uploadDirectory - ftp directory to upload file.
     * @param fileLocation    - local file location.
     * @param serverPort      - ftp port to connect.
     * @throws AndroidAgentException - AndroidAgent exception.
     */
    private void uploadFileUsingSFTPClient(Operation operation, String host,
                                           String ftpUserName, String ftpPassword,
                                           String uploadDirectory, String fileLocation, int serverPort)
            throws AndroidAgentException {
        Session session = null;
        Channel channel = null;
        ChannelSftp channelSftp = null;
        String fileName = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(ftpUserName, host, serverPort);
            session.setPassword(ftpPassword);
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            channel = session.openChannel(Protocol.SFTP.getValue());
            channel.connect();
            channelSftp = (ChannelSftp) channel;
            channelSftp.cd(uploadDirectory);
            File file = new File(fileLocation);
            fileName = file.getName();
            channelSftp.put(new FileInputStream(file), fileName);
            operation.setStatus(resources.getString(R.string.operation_value_completed));
            operation.setOperationResponse("File uploaded from the device successfully");
        } catch (JSchException | FileNotFoundException | SftpException e) {
            handleOperationError(operation, fileTransferExceptionHandler(e, fileName), e);
        } finally {
            if (channelSftp != null) {
                channelSftp.exit();
            }
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    /**
     * Configure device WIFI profile.
     *
     * @param operation - Operation object.
     */
    public void configureWifi(final org.wso2.iot.agent.beans.Operation operation) throws AndroidAgentException {
        boolean wifiStatus;
        String ssid = null;
        String password = null;
        WifiProfile wifiProfile = null;
        final JSONObject result = new JSONObject();

        try {
            JSONObject wifiData = new JSONObject(operation.getPayLoad().toString());

            wifiProfile = new WifiProfile();

            if (!wifiData.isNull(WifiProfile.SSID)) {
                wifiProfile.setSsid(wifiData.getString(WifiProfile.SSID));
            }
            if (!wifiData.isNull(WifiProfile.TYPE)) {
                wifiProfile.setType(WifiProfile.Type.getByValue(wifiData.getString(WifiProfile.TYPE)));
            }
            if (!wifiData.isNull(WifiProfile.PASSWORD)) {
                wifiProfile.setPassword(wifiData.getString(WifiProfile.PASSWORD));
            }
            if (!wifiData.isNull(WifiProfile.EAPMETHOD)) {
                wifiProfile.setEapMethod(WifiProfile.EAPMethod.getByValue(wifiData.getString(WifiProfile.EAPMETHOD)));
            }
            if (!wifiData.isNull(WifiProfile.PHASE2)) {
                wifiProfile.setPhase2(WifiProfile.Phase2.getByValue(wifiData.getString(WifiProfile.PHASE2)));
            }
            if (!wifiData.isNull(WifiProfile.PROVISIONING)) {
                wifiProfile.setProvisioning(wifiData.getInt(WifiProfile.PROVISIONING));
            }
            if (!wifiData.isNull(WifiProfile.IDENTITY)) {
                wifiProfile.setIdentity(wifiData.getString(WifiProfile.IDENTITY));
            }
            if (!wifiData.isNull(WifiProfile.ANONYMOUSIDENTITY)) {
                wifiProfile.setAnonymousIdentity(wifiData.getString(WifiProfile.ANONYMOUSIDENTITY));
            }
            if (!wifiData.isNull(WifiProfile.CACERT)) {
                wifiProfile.setCaCert(wifiData.getString(WifiProfile.CACERT));
            }
            if (!wifiData.isNull(WifiProfile.CACERTNAME)) {
                wifiProfile.setCaCertName(wifiData.getString(WifiProfile.CACERTNAME));
            }

        } catch (JSONException e) {
            operation.setStatus(resources.getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing WIFI payload.");
            resultBuilder.build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }

        WiFiConfig config = new WiFiConfig(context.getApplicationContext());
        config.setWifiConfig(wifiProfile, new WifiConfigCreationListener() {
            @Override
            public void onCreateWifiConfig(boolean isSavedWifi) {
                try {
                    String status;
                    if (isSavedWifi) {
                        status = resources.getString(R.string.shared_pref_default_status);
                        result.put(resources.getString(R.string.operation_status), status);
                    } else {
                        status = resources.getString(R.string.shared_pref_false_status);
                        result.put(resources.getString(R.string.operation_status), status);
                    }
                } catch (JSONException e) {
                    operation.setStatus(resources.getString(R.string.operation_value_error));
                    operation.setOperationResponse("Error in parsing WIFI payload.");
                    resultBuilder.build(operation);
                    Log.e(TAG, "Invalid JSON format" + e);
                }
                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, "Wifi configured");
                }
                operation.setStatus(resources.getString(R.string.operation_value_completed));
                operation.setPayLoad(result.toString());
                resultBuilder.build(operation);

            }
        });

        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "configureWifi executed.");
        }
    }

    /**
     * Disable/Enable device camera.
     *
     * @param operation - Operation object.
     */
    public void disableCamera(org.wso2.iot.agent.beans.Operation operation) throws AndroidAgentException {
        boolean camFunc = operation.isEnabled();
        operation.setStatus(resources.getString(R.string.operation_value_completed));
        resultBuilder.build(operation);
        devicePolicyManager.setCameraDisabled(cdmDeviceAdmin, !camFunc);
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Camera enabled: " + camFunc);
        }
    }

    /**
     * Mute the device.
     *
     * @param operation - Operation object.
     */
    public void muteDevice(org.wso2.iot.agent.beans.Operation operation) {
        operation.setStatus(resources.getString(R.string.operation_value_completed));
        resultBuilder.build(operation);

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_RING, DEFAULT_VOLUME, DEFAULT_FLAG);
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Device muted");
        }
    }

    /**
     * Create web clip (Web app shortcut on device home screen).
     *
     * @param operation - Operation object.
     */
    public void manageWebClip(org.wso2.iot.agent.beans.Operation operation) throws AndroidAgentException {
        String appUrl;
        String title;
        String operationType;

        try {
            JSONObject webClipData = new JSONObject(operation.getPayLoad().toString());
            appUrl = webClipData.getString(resources.getString(R.string.intent_extra_identity));
            title = webClipData.getString(resources.getString(R.string.intent_extra_title));
            operationType = webClipData.getString(resources.getString(R.string.operation_type));
        } catch (JSONException e) {
            operation.setStatus(resources.getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing WebClip payload.");
            resultBuilder.build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }

        operation.setStatus(resources.getString(R.string.operation_value_completed));
        resultBuilder.build(operation);

        if (appUrl != null && title != null) {
            appList.manageWebAppBookmark(appUrl, title, operationType);
        }
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Created webclip");
        }
    }

    /**
     * Open Google Play store application with an application given.
     *
     * @param packageName - Application package name.
     */
    public void triggerGooglePlayApp(String packageName) {
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "triggerGooglePlayApp started.");
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.parse(Constants.GOOGLE_PLAY_APP_URI + packageName));
            context.startActivity(intent);
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "triggerGooglePlayApp called app store.");
            }
        } catch (ActivityNotFoundException e) {
            String error = "App store is not installed. Cannot install the app";
            // Handling the exception when the market place is missing in the device
            Log.e(TAG, error, e);
            Preference.putString(context, context.getResources().getString(R.string.app_install_status),
                    context.getResources().getString(R.string.app_status_value_download_failed));
            Preference.putString(context, context.getResources().getString(R.string.app_install_failed_message), error);
        }
    }

    /**
     * Monitor currently enforced policy for compliance.
     *
     * @param operation - Operation object.
     */
    public void monitorPolicy(org.wso2.iot.agent.beans.Operation operation) throws AndroidAgentException {
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "monitorPolicy started.");
        }
        String payload = Preference.getString(context, Constants.PreferenceFlag.APPLIED_POLICY);
        PolicyOperationsMapper operationsMapper = new PolicyOperationsMapper();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        PolicyComplianceChecker policyChecker = new PolicyComplianceChecker(context);
        ArrayList<ComplianceFeature> result = new ArrayList<>();

        try {
            if (payload != null) {
                List<org.wso2.iot.agent.beans.Operation> operations = mapper.readValue(
                        payload,
                        mapper.getTypeFactory().constructCollectionType(List.class,
                                org.wso2.iot.agent.beans.Operation.class));
                for (org.wso2.iot.agent.beans.Operation op : operations) {
                    op = operationsMapper.getOperation(op);
                    result.add(policyChecker.checkPolicyState(op));
                }
                operation.setStatus(resources.getString(R.string.operation_value_completed));
                operation.setPayLoad(mapper.writeValueAsString(result));
                resultBuilder.build(operation);
            }
        } catch (IOException e) {
            operation.setStatus(resources.getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing policy monitor payload stream.");
            resultBuilder.build(operation);
            throw new AndroidAgentException("Error occurred while parsing stream.", e);
        }
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "monitorPolicy completed.");
        }
    }

    /**
     * Revoke currently enforced policy.
     *
     * @param operation - Operation object.
     */
    public void revokePolicy(org.wso2.iot.agent.beans.Operation operation) throws AndroidAgentException {
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "revokePolicy started.");
        }
        CommonUtils.revokePolicy(context);
        operation.setStatus(resources.getString(R.string.operation_value_completed));
        resultBuilder.build(operation);
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "revokePolicy completed.");
        }
    }

    /**
     * Blacklisting apps.
     *
     * @param operation - Operation object.
     */
    public void blacklistApps(org.wso2.iot.agent.beans.Operation operation) throws AndroidAgentException {

    }

    /**
     * Uninstall application.
     *
     * @param operation - Operation object.
     */
    public void uninstallApplication(Operation operation) throws AndroidAgentException {
        String packageName;
        String type;
        String schedule = null;
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "uninstallApplication started. Payload - " +
                    operation.getPayLoad().toString());
        }
        try {
            JSONObject appData = new JSONObject(operation.getPayLoad().toString());
            type = appData.getString(getContextResources().getString(R.string.app_type));

            if (getContextResources().getString(R.string.intent_extra_web).equalsIgnoreCase(type)) {
                String appUrl = appData.getString(getContextResources().getString(R.string.app_url));
                String name = appData.getString(getContextResources().getString(R.string.intent_extra_name));
                String operationType = getContextResources().getString(R.string.operation_uninstall);
                JSONObject payload = new JSONObject();
                payload.put(getContextResources().getString(R.string.intent_extra_identity), appUrl);
                payload.put(getContextResources().getString(R.string.intent_extra_title), name);
                payload.put(getContextResources().getString(R.string.operation_type), operationType);
                operation.setPayLoad(payload.toString());
                manageWebClip(operation);
            } else {
                packageName = appData.getString(getContextResources().getString(R.string.app_identifier));
                if (appData.has(getContextResources().getString(R.string.app_schedule))) {
                    schedule = appData.getString(getContextResources().getString(R.string.app_schedule));
                }
                getAppList().uninstallApplication(packageName, schedule);
                operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
                getResultBuilder().build(operation);
            }

            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Application started to uninstall");
            }
        } catch (JSONException e) {
            operation.setStatus(getContextResources().getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing APPLICATION payload.");
            getResultBuilder().build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
    }

    /**
     * Reboot the device [System app required].
     *
     * @param operation - Operation object.
     */
    public void rebootDevice(org.wso2.iot.agent.beans.Operation operation) throws AndroidAgentException {
        JSONObject result = new JSONObject();

        try {
            boolean isRebootPossible = Constants.SYSTEM_APP_ENABLED
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && this instanceof OperationManagerDeviceOwner)
                    || RootChecker.isDeviceRooted();
            result.put(resources.getString(R.string.operation_status), isRebootPossible);
            operation.setPayLoad(result.toString());

            if (isRebootPossible) {
                int lastRebootOperationId = Preference.getInt(context, resources.getString(R.string.shared_pref_reboot_op_id));
                if (lastRebootOperationId == operation.getId()) {
                    Log.i(TAG, "Ignoring duplicated reboot operation");
                    return; //Ignoring duplicate reboot operation
                } else {
                    Preference.removePreference(context, resources.getString(R.string.shared_pref_reboot_status));
                    Preference.removePreference(context, resources.getString(R.string.shared_pref_reboot_result));
                    Preference.putInt(context, resources.getString(R.string.shared_pref_reboot_op_id), operation.getId());
                }
                operation.setStatus(resources.getString(R.string.operation_value_pending));
                resultBuilder.build(operation);

                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, "Reboot initiated.");
                }
            } else {
                Log.e(TAG, resources.getString(R.string.toast_message_reboot_failed));
                operation.setStatus(resources.getString(R.string.operation_value_error));
                operation.setOperationResponse(resources.getString(R.string.toast_message_reboot_failed));
                resultBuilder.build(operation);
            }
        } catch (JSONException e) {
            operation.setStatus(resources.getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in processing result payload.");
            resultBuilder.build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
    }

    /**
     * Upgrading device firmware from the configured OTA server.
     *
     * @param operation - Operation object.
     */
    public void upgradeFirmware(org.wso2.iot.agent.beans.Operation operation) throws AndroidAgentException {
        JSONObject result = new JSONObject();
        Preference.putString(context, resources.getString(R.string.pref_key_schedule), operation.getPayLoad().toString());
        try {
            String status = resources.getString(R.string.shared_pref_default_status);
            result.put(resources.getString(R.string.operation_status), status);

            operation.setPayLoad(result.toString());

            if (status.equals(resources.getString(R.string.shared_pref_default_status))) {
                operation.setStatus(resources.getString(R.string.operation_value_progress));
                resultBuilder.build(operation);

                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, "Firmware upgrade started.");
                }
            } else {
                operation.setStatus(resources.getString(R.string.operation_value_error));
                operation.setOperationResponse("Firmware upgrade failed due to download failure.");
                resultBuilder.build(operation);
            }
        } catch (JSONException e) {
            operation.setStatus(resources.getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in processing result payload.");
            resultBuilder.build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
    }

    /**
     * Execute shell commands as the super user.
     *
     * @param operation - Operation object.
     */
    public void executeShellCommand(org.wso2.iot.agent.beans.Operation operation) throws AndroidAgentException {
        JSONObject result = new JSONObject();

        try {
            String status = resources.getString(R.string.shared_pref_default_status);
            result.put(resources.getString(R.string.operation_status), status);

            operation.setPayLoad(result.toString());

            if (status.equals(resources.getString(R.string.shared_pref_default_status))) {
                operation.setStatus(resources.getString(R.string.operation_value_completed));
                resultBuilder.build(operation);

                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, "Shell command received.");
                }
            } else {
                operation.setStatus(resources.getString(R.string.operation_value_error));
                operation.setOperationResponse("Device reboot failed due to insufficient privileges.");
                resultBuilder.build(operation);
            }
        } catch (JSONException e) {
            operation.setStatus(resources.getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in processing result payload.");
            resultBuilder.build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
    }

    /**
     * This method returns the completed operations list.
     *
     * @return operation list
     */
    public List<org.wso2.iot.agent.beans.Operation> getResultPayload() {
        return resultBuilder.getResultPayload();
    }

    /**
     * Lock the device.
     *
     * @param operation - Operation object.
     */
    public void lockDevice(org.wso2.iot.agent.beans.Operation operation) throws AndroidAgentException {
        operation.setStatus(resources.getString(R.string.operation_value_completed));
        resultBuilder.build(operation);
        JSONObject inputData;
        String message = null;
        boolean isHardLockEnabled = false;
        try {
            if (operation.getPayLoad() != null) {
                inputData = new JSONObject(operation.getPayLoad().toString());
                message = inputData.getString(Constants.ADMIN_MESSAGE);
                isHardLockEnabled = inputData.getBoolean(Constants.IS_HARD_LOCK_ENABLED);
            }
        } catch (JSONException e) {
            operation.setStatus(resources.getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing LOCK payload.");
            resultBuilder.build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
        if (isHardLockEnabled && Constants.SYSTEM_APP_ENABLED) {
            if (message == null || message.isEmpty()) {
                message = resources.getString(R.string.txt_lock_activity);
            }
            Preference.putBoolean(context, Constants.IS_LOCKED, true);
            Preference.putString(context, Constants.LOCK_MESSAGE, message);
            operation.setStatus(resources.getString(R.string.operation_value_completed));
            resultBuilder.build(operation);
            enableHardLock(message, operation);
        } else {
            operation.setStatus(resources.getString(R.string.operation_value_completed));
            resultBuilder.build(operation);
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(context.getString(R.string.alert_message))
                    .setContentText(message)
                    .setAutoCancel(true)
                    .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(), 0));
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(0, mBuilder.build());
            devicePolicyManager.lockNow();
        }

        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Device locked");
        }
    }

    public void enableHardLock(String message, Operation operation) {
        String payload = "false";
        if (getApplicationManager().isPackageInstalled(Constants.SYSTEM_SERVICE_PACKAGE)) {
            operation.setStatus(resources.getString(R.string.operation_value_completed));
            CommonUtils.callSystemApp(getContext(), Constants.Operation.DEVICE_LOCK, payload, message);
        } else {
            operation.setStatus(resources.getString(R.string.operation_value_error));
            operation.setOperationResponse("System service is not available.");
            Log.e(TAG, "System service is not available");
        }
        resultBuilder.build(operation);
    }

    /**
     * Unlock the device.
     *
     * @param operation - Operabtion object.
     */
    public void unlockDevice(org.wso2.iot.agent.beans.Operation operation) {
        if (getApplicationManager().isPackageInstalled(Constants.SYSTEM_SERVICE_PACKAGE)) {
            boolean isLocked = Preference.getBoolean(context, Constants.IS_LOCKED);
            if (isLocked) {
                Preference.putBoolean(context, Constants.IS_LOCKED, false);
                CommonUtils.callSystemApp(getContext(), Constants.Operation.DEVICE_UNLOCK, null, null);
            }
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Device unlocked");
            }
            operation.setStatus(resources.getString(R.string.operation_value_completed));
        } else {
            operation.setStatus(resources.getString(R.string.operation_value_error));
            operation.setOperationResponse("System service is not available.");
            Log.e(TAG, "System service is not available");
        }
        resultBuilder.build(operation);
    }

    /**
     * Install google play applications.
     *
     * @param operation - Operation object.
     */
    public void installGooglePlayApp(Operation operation) throws AndroidAgentException {
        String packageName;
        try {
            JSONObject appData = new JSONObject(operation.getPayLoad().toString());
            packageName = (String) appData.get(getContextResources().getString(R.string.intent_extra_package));

        } catch (JSONException e) {
            operation.setStatus(getContextResources().getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing APPLICATION payload.");
            getResultBuilder().build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }

        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Started installing GoogleApp");
        }

        operation.setStatus(getContextResources().getString(R.string.operation_value_completed));
        getResultBuilder().build(operation);

        triggerGooglePlayApp(packageName);
    }

    /**
     * Configure device VPN profile.
     *
     * @param operation - Operation object.
     */
    public void configureVPN(org.wso2.iot.agent.beans.Operation operation) throws AndroidAgentException {
        String serverAddress = null;
        JSONObject result = new JSONObject();

        try {
            JSONObject vpnData = new JSONObject(operation.getPayLoad().toString());
            if (!vpnData.isNull(resources.getString(R.string.intent_extra_server))) {
                serverAddress = (String) vpnData.get(resources.getString(R.string.intent_extra_server));
            }

        } catch (JSONException e) {
            operation.setStatus(resources.getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing VPN payload.");
            resultBuilder.build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }

        if (serverAddress != null) {
            Intent intent = new Intent(context, AlertActivity.class);
            intent.putExtra(resources.getString(R.string.intent_extra_message_text), resources.getString(R.string.toast_message_vpn));
            intent.putExtra(resources.getString(R.string.intent_extra_operation_id), operation.getId());
            intent.putExtra(resources.getString(R.string.intent_extra_payload), operation.getPayLoad().toString());
            intent.putExtra(resources.getString(R.string.intent_extra_type),
                    Constants.Operation.VPN);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            context.startActivity(intent);
        }

        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "VPN configured");
        }
        operation.setStatus(resources.getString(R.string.operation_value_completed));
        operation.setPayLoad(result.toString());
        resultBuilder.build(operation);
    }

    /**
     * Get device logcat.
     *
     * @param operation - Operation object.
     */
    public void getLogcat(org.wso2.iot.agent.beans.Operation operation) throws AndroidAgentException {
        String logLevel = Constants.LogPublisher.LOG_LEVEL;
        if (Constants.SYSTEM_APP_ENABLED) {
            try {
                JSONObject commandObj = new JSONObject();
                commandObj.put("operation_id", operation.getId());
                commandObj.put("log_level", logLevel);
                CommonUtils.callSystemApp(context, Constants.Operation.LOGCAT, commandObj.toString(),
                        null);
                operation.setStatus(resources.getString(R.string.operation_value_progress));
            } catch (JSONException e) {
                Log.e(TAG, "Error occurred. " + e.getMessage());
                operation.setOperationResponse(e.getMessage());
                operation.setStatus(context.getResources().getString(R.string.operation_value_error));
                resultBuilder.build(operation);
            }
        } else {
            RuntimeInfo info = new RuntimeInfo(context);
            try {
                operation.setOperationResponse(getOperationResponseFromLogcat(context, info.getLogCat(logLevel)));
                operation.setStatus(context.getResources().getString(R.string.operation_value_completed));
            } catch (IOException e) {
                operation.setOperationResponse("Unable to get logs. " + e.getMessage());
                operation.setStatus(context.getResources().getString(R.string.operation_value_error));
            }
            resultBuilder.build(operation);
        }
    }

    public static String getOperationResponseFromLogcat(Context context, String logcat) throws IOException {
        File logcatFile = new File(logcat);
        if (logcatFile.exists() && logcatFile.canRead()) {
            DeviceInfo deviceInfo = new DeviceInfo(context);
            EventPayload eventPayload = new EventPayload();
            eventPayload.setPayload(logcat);
            eventPayload.setType("LOGCAT");
            eventPayload.setDeviceIdentifier(deviceInfo.getDeviceId());

            StringBuilder emmBuilder = new StringBuilder();
            StringBuilder publisherBuilder = new StringBuilder();
            int index = 0;
            String line;
            ReversedLinesFileReader reversedLinesFileReader = new ReversedLinesFileReader(logcatFile, Charset.forName("US-ASCII"));
            while ((line = reversedLinesFileReader.readLine()) != null) {
                publisherBuilder.insert(0, "\n");
                publisherBuilder.insert(0, line);
                //operationResponse filed in the DM_DEVICE_OPERATION_RESPONSE is declared as a blob and hence can only hold 64Kb.
                //So we don't want to throw exceptions in the server. Limiting the response in here to limit the server traffic also.
                if (emmBuilder.length() < Character.MAX_VALUE - 8192) { //Keeping 8kB for rest of the response payload.
                    emmBuilder.insert(0, "\n");
                    emmBuilder.insert(0, line);
                }
                if (++index >= Constants.LogPublisher.NUMBER_OF_LOG_LINES) {
                    break;
                }
            }
            LogPublisherFactory publisher = new LogPublisherFactory(context);
            if (publisher.getLogPublisher() != null) {
                eventPayload.setPayload(publisherBuilder.toString());
                publisher.getLogPublisher().publish(eventPayload);
                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, "Logcat published size: " + eventPayload.getPayload().length());
                }
            }
            eventPayload.setPayload(emmBuilder.toString());
            Gson logcatResponse = new Gson();
            logcatFile.delete();
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Logcat payload size: " + eventPayload.getPayload().length());
            }
            return logcatResponse.toJson(eventPayload);
        } else {
            throw new IOException("Unable to find or read log file.");
        }
    }

    @Override
    public void passOperationToSystemApp(Operation operation) throws AndroidAgentException {
        if (getApplicationManager().isPackageInstalled(Constants.SYSTEM_SERVICE_PACKAGE)) {
            CommonUtils.callSystemApp(getContext(), operation.getCode(),
                    Boolean.toString(operation.isEnabled()), null);
        } else {
            if (operation.isEnabled()) {
                Log.e(TAG, "Invalid operation code received");
            }
        }
    }

    /**
     * Display notification.
     *
     * @param operation - Operation object.
     */
    public void displayNotification(org.wso2.iot.agent.beans.Operation operation) throws AndroidAgentException {
        try {

            operation.setStatus(getContextResources().getString(R.string.operation_value_progress));
            operation.setOperationResponse(notificationService.buildResponse(Notification.Status.RECEIVED));
            getResultBuilder().build(operation);
            JSONObject inputData = new JSONObject(operation.getPayLoad().toString());
            String messageTitle = inputData.getString(getContextResources().getString(R.string.intent_extra_message_title));
            String messageText = inputData.getString(getContextResources().getString(R.string.intent_extra_message_text));

            if (messageTitle != null && !messageTitle.isEmpty() &&
                    messageText != null && !messageText.isEmpty()) {
                //adding notification to the db
                notificationService.addNotification(operation.getId(), messageTitle, messageText, Notification.Status.RECEIVED);
                notificationService.showNotification(operation.getId(), messageTitle, messageText);
            } else {
                operation.setStatus(getContextResources().getString(R.string.operation_value_error));
                String errorMessage = "Message title/text is empty. Please retry with valid inputs";
                JSONObject errorResult = new JSONObject();
                errorResult.put(STATUS, errorMessage);
                operation.setOperationResponse(errorMessage);
                getResultBuilder().build(operation);
                Log.e(TAG, errorMessage);
            }
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Notification received");
            }
        } catch (JSONException e) {
            operation.setStatus(getContextResources().getString(R.string.operation_value_error));
            operation.setOperationResponse("Error in parsing NOTIFICATION payload.");
            getResultBuilder().build(operation);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
    }

    /**
     * Checks camera policy on the device (camera enabled/disabled).
     *
     * @param operation - Operation object.
     * @return policy - ComplianceFeature object.
     */
    public ComplianceFeature checkCameraPolicy(Operation operation, ComplianceFeature policy) {
        boolean cameraStatus = getDevicePolicyManager().getCameraDisabled(getCdmDeviceAdmin());

        if ((operation.isEnabled() && !cameraStatus) || (!operation.isEnabled() && cameraStatus)) {
            policy.setCompliance(true);
        } else {
            policy.setCompliance(false);
        }

        return policy;
    }

    /**
     * Checks install app policy on the device (Particular app in the policy should be installed).
     *
     * @param operation - Operation object.
     * @return policy - ComplianceFeature object.
     */
    public ComplianceFeature checkInstallAppPolicy(Operation operation, ComplianceFeature policy) throws AndroidAgentException {
        String appIdentifier = null;
        String name = null;

        try {
            JSONObject appData = new JSONObject(operation.getPayLoad().toString());

            if (!appData.isNull(getContextResources().getString(R.string.app_identifier))) {
                appIdentifier = appData.getString(getContextResources().getString(R.string.app_identifier));
            }

            if (!appData.isNull(getContextResources().getString(R.string.app_identifier))) {
                name = appData.getString(getContextResources().getString(R.string.intent_extra_name));
            }

            if (isAppInstalled(appIdentifier)) {
                policy.setCompliance(true);
            } else {
                policy.setCompliance(false);
                policy.setMessage(getContextResources().getString(R.string.error_app_install_policy) + name);
            }

        } catch (JSONException e) {
            policy.setCompliance(false);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
        return policy;
    }

    /**
     * Checks uninstall app policy on the device (Particular app in the policy should be removed).
     *
     * @param operation - Operation object.
     * @return policy - ComplianceFeature object.
     */
    public ComplianceFeature checkUninstallAppPolicy(Operation operation, ComplianceFeature policy) throws AndroidAgentException {
        String appIdentifier = null;
        String name = null;

        try {
            JSONObject appData = new JSONObject(operation.getPayLoad().toString());

            if (!appData.isNull(getContextResources().getString(R.string.app_identifier))) {
                appIdentifier = appData.getString(getContextResources().getString(R.string.app_identifier));
            }

            if (!appData.isNull(getContextResources().getString(R.string.app_identifier))) {
                name = appData.getString(getContextResources().getString(R.string.intent_extra_name));
            }

            if (!isAppInstalled(appIdentifier)) {
                policy.setCompliance(true);
            } else {
                policy.setCompliance(false);
                policy.setMessage(getContextResources().getString(R.string.error_app_uninstall_policy) + name);
            }

        } catch (JSONException e) {
            policy.setCompliance(false);
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
        return policy;
    }

    /**
     * Checks if the app is already installed on the device.
     *
     * @param appIdentifier - App package name.
     * @return appInstalled - App installed status.
     */
    private boolean isAppInstalled(String appIdentifier) {
        boolean appInstalled = false;
        ArrayList<DeviceAppInfo> apps = new ArrayList<>(getApplicationManager().getInstalledApps().values());
        for (DeviceAppInfo appInfo : apps) {
            if (appIdentifier.trim().equals(appInfo.getPackagename())) {
                appInstalled = true;
            }
        }
        return appInstalled;
    }

    /**
     * Checks device encrypt policy on the device (Device external storage encryption).
     *
     * @param operation - Operation object.
     * @return policy - ComplianceFeature object.
     */
    public ComplianceFeature checkEncryptPolicy(Operation operation, ComplianceFeature policy) {
        boolean encryptStatus = (getDevicePolicyManager().getStorageEncryptionStatus() != getDevicePolicyManager().
                ENCRYPTION_STATUS_UNSUPPORTED && getDevicePolicyManager().
                getStorageEncryptionStatus() != getDevicePolicyManager().ENCRYPTION_STATUS_INACTIVE);

        if ((operation.isEnabled() && encryptStatus) || (!operation.isEnabled() && !encryptStatus)) {
            policy.setCompliance(true);
        } else {
            policy.setCompliance(false);
            policy.setMessage(getContextResources().getString(R.string.error_encrypt_policy));
        }

        return policy;
    }

    /**
     * Checks screen lock password policy on the device.
     *
     * @return policy - ComplianceFeature object.
     */
    public ComplianceFeature checkPasswordPolicy(ComplianceFeature policy) {
        if (getDevicePolicyManager().isActivePasswordSufficient()) {
            policy.setCompliance(true);
        } else {
            policy.setCompliance(false);
        }

        return policy;
    }

    /**
     * Checks Wifi policy on the device (Particular wifi configuration in the policy should be enforced).
     *
     * @param operation - Operation object.
     * @return policy - ComplianceFeature object.
     */
    public ComplianceFeature checkWifiPolicy(Operation operation, ComplianceFeature policy) throws AndroidAgentException {
        String ssid = null;

        try {
            JSONObject wifiData = new JSONObject(operation.getPayLoad().toString());
            if (!wifiData.isNull(getContextResources().getString(R.string.intent_extra_ssid))) {
                ssid = (String) wifiData.get(getContextResources().getString(R.string.intent_extra_ssid));
            }

            WiFiConfig config = new WiFiConfig(getContext().getApplicationContext());
            if (config.findWifiConfigurationBySsid(ssid)) {
                policy.setCompliance(true);
            } else {
                policy.setCompliance(false);
                policy.setMessage(getContextResources().getString(R.string.error_wifi_policy));
            }
        } catch (JSONException e) {
            throw new AndroidAgentException("Invalid JSON format.", e);
        }
        return policy;
    }

    /**
     * Check the app restriction policy (black list or white list) for compliance
     *
     * @param operation - Operation object
     * @return - Compliance feature object
     * @throws AndroidAgentException
     */
    public ComplianceFeature checkAppRestrictionPolicy(Operation operation, ComplianceFeature policy) throws AndroidAgentException {

        AppRestriction appRestriction =
                CommonUtils.getAppRestrictionTypeAndList(operation, null, null);
        List<String> installedAppPackages = CommonUtils.getInstalledAppPackages(getContext());

        if (Constants.AppRestriction.BLACK_LIST.equals(appRestriction.getRestrictionType())) {
            List<String> commonApps = new ArrayList<>(installedAppPackages);
            if (commonApps.retainAll(appRestriction.getRestrictedList())) {
                if (commonApps.size() > 0) {
                    policy.setCompliance(false);
                    return policy;
                }
            }
        } else if (Constants.AppRestriction.WHITE_LIST.equals(appRestriction.getRestrictionType())) {
            List<String> remainApps = new ArrayList<>
                    (CommonUtils.getInstalledAppPackagesByUser(getContext()));
            String permittedPackageName;
            JSONObject permittedApp;
            String whiteListAppsPref;
            for (String packageName : remainApps) {
                whiteListAppsPref = Preference.
                        getString(context, Constants.AppRestriction.WHITE_LIST_APPS);
                if (whiteListAppsPref != null) {
                    try {
                        JSONArray whiteListApps = new JSONArray(whiteListAppsPref);
                        for (int i = 0; i < whiteListApps.length(); i++) {
                            permittedApp = new JSONObject(whiteListApps.getString(i));
                            permittedPackageName = permittedApp.
                                    getString(Constants.AppRestriction.PACKAGE_NAME);
                            if (!permittedPackageName.equals(packageName)) {
                                policy.setCompliance(false);
                                return policy;
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Invalid JSON format..");
                    }
                }
            }
        }
        policy.setCompliance(true);
        return policy;
    }

    /**
     * This method is being invoked when get info operation get executed.
     *
     * @param result      response result
     * @param requestCode code of the requested operation
     */
    @Override
    public void onReceiveAPIResult(Map<String, String> result, int requestCode) {
        String responseStatus;
        String response;
        if (requestCode == Constants.DEVICE_INFO_REQUEST_CODE) {
            if (result != null) {
                responseStatus = result.get(Constants.STATUS_KEY);
                if (Constants.Status.SUCCESSFUL.equals(responseStatus)) {
                    response = result.get(Constants.RESPONSE);
                    if (response != null && !response.isEmpty()) {
                        if (Constants.DEBUG_MODE_ENABLED) {
                            Log.d(TAG, "onReceiveAPIResult." + response);
                            Log.d(TAG, "Device information sent");
                        }
                    }
                }
            }
        }
    }

}
