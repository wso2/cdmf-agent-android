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
package org.wso2.iot.agent.services;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPSClient;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.beans.Operation;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.HTTPAuthenticator;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import static org.wso2.iot.agent.utils.FileTransferUtils.cleanupStreams;

/**
 * Service to download files.
 */
public class FileDownloadService extends IntentService {

    private static final String TAG = FileDownloadService.class.getSimpleName();
    private Resources resources;
    private SharedPreferences.Editor editor;

    public FileDownloadService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Log.d(TAG, "Starting File upload service");
        resources = getResources();
        editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        if (intent != null) {
            Operation operation = (Operation) intent.getExtras().getSerializable(resources.
                    getString(R.string.intent_extra_operation_object));
            try {
                downloadFile(operation);
            } catch (AndroidAgentException e) {
                Log.d(TAG, e.getLocalizedMessage());
            }
        }
        this.stopSelf();
    }

    /**
     * Download the file to the device from the FTP server.
     *
     * @param operation - Operation object.
     */
    public void downloadFile(Operation operation) throws AndroidAgentException {
        String fileName = null;
        validateOperation(operation);
        try {
            JSONObject inputData = new JSONObject(operation.getPayLoad().toString());
            final String fileURL = inputData.getString(Constants.FileTransfer.FILE_URL);
            final String userName = inputData.getString(Constants.FileTransfer.USER_NAME);
            final String password = inputData.getString(Constants.FileTransfer.PASSWORD);
            final String location = inputData.getString(Constants.FileTransfer.FILE_LOCATION);
            String savingLocation = getSavingLocation(operation, location);
            if (fileURL.startsWith(Constants.FileTransfer.HTTP)) {
                downloadFileUsingHTTPClient(operation, fileURL, userName, password,
                        savingLocation);
            } else {
                String[] userInfo = urlSplitter(operation, fileURL, false);
                String ftpUserName;
                if (userName.isEmpty()) {
                    ftpUserName = userInfo[0];
                } else {
                    ftpUserName = userName;
                }
                String fileDirectory = userInfo[1];
                String host = userInfo[2];
                int serverPort = 0;
                if (userInfo[3] != null) {
                    serverPort = Integer.parseInt(userInfo[3]);
                }
                String protocol = userInfo[4];
                fileName = userInfo[5];
                if (Constants.DEBUG_MODE_ENABLED) {
                    printLogs(ftpUserName, host, fileName, fileDirectory, savingLocation, serverPort);
                }
                selectDownloadClient(protocol, operation, host, ftpUserName, password,
                        savingLocation, fileName, serverPort, fileDirectory);
            }
        } catch (ArrayIndexOutOfBoundsException | JSONException | URISyntaxException e) {
            handleOperationError(operation, fileTransferExceptionCause(e, fileName), e);
        } finally {
            Log.d(TAG, operation.getStatus());
            setResponse(operation);
        }
    }

    private void setResponse(Operation operation) {
        editor.putInt(resources.getString(R.string.FILE_DOWNLOAD_ID), operation.getId());
        editor.putString(resources.getString(R.string.FILE_DOWNLOAD_STATUS), operation.getStatus());
        editor.putString(resources.getString(R.string.FILE_DOWNLOAD_RESPONSE), operation.getOperationResponse());
        editor.apply();
    }

    private void selectDownloadClient(String protocol, Operation operation, String host, String ftpUserName,
                                      String ftpPassword, String savingLocation, String fileName,
                                      int serverPort, String fileDirectory) throws AndroidAgentException {
        switch (protocol) {
            case Constants.FileTransfer.SFTP:
                downloadFileUsingSFTPClient(operation, host, ftpUserName, ftpPassword,
                        savingLocation, fileName, serverPort, fileDirectory);
                break;
            case Constants.FileTransfer.FTP:
                downloadFileUsingFTPClient(operation, host, ftpUserName, ftpPassword,
                        savingLocation, fileName, serverPort, fileDirectory);
                break;
            default:
                handleOperationError(operation, "Protocol(" + protocol + ") not supported.", null);
        }

    }

    private void validateOperation(Operation operation) throws AndroidAgentException {
        if (operation == null) {
            throw new AndroidAgentException("Null operation object");
        } else if (operation.getPayLoad() == null) {
            String response = "Operation payload null.";
            operation.setOperationResponse(response);
            operation.setStatus(resources.getString(R.string.operation_value_error));
            setResponse(operation);
            throw new AndroidAgentException(response);
        }
    }

    public void handleOperationError(Operation operation, String message, Exception exception)
            throws AndroidAgentException {
        operation.setStatus(resources.getString(R.string.operation_value_error));
        operation.setOperationResponse(message);
        if (exception != null) {
            throw new AndroidAgentException(message, exception);
        } else {
            throw new AndroidAgentException(message);
        }
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
                                             String userName, String password,
                                             String savingLocation) throws AndroidAgentException {
        checkHTTPAuthentication(userName, password);
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
            operation.setOperationResponse("File uploaded to the device successfully ( " + fileName + " ).");
        } catch (IOException e) {
            handleOperationError(operation, fileTransferExceptionCause(e, fileName), e);
        } finally {
            cleanupStreams(inputStream, null, null, fileOutputStream, null, null, dataInputStream, null);
        }
    }

    private void checkHTTPAuthentication(String userName, String password) {
        if (!userName.isEmpty()) {
            HTTPAuthenticator.setPasswordAuthentication(userName, password);
            Authenticator.setDefault(new HTTPAuthenticator());
        }
    }

    private String getSavingLocation(Operation operation, String location) throws AndroidAgentException {
        String savingLocation;
        if (location.isEmpty()) {
            savingLocation = getSavingLocation();
            if (savingLocation == null) {
                handleOperationError(operation, "Error in default saving location.", null);
            }
        } else {
            savingLocation = location;
        }
        return savingLocation;
    }

    private String getSavingLocation() {
        if (!Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).
                exists() && !Environment.getExternalStoragePublicDirectory
                (Environment.DIRECTORY_DOWNLOADS).mkdirs()) {
            return null;
        }
        return Environment.getExternalStoragePublicDirectory(Environment.
                DIRECTORY_DOWNLOADS).toString();
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
            channel = session.openChannel(Constants.FileTransfer.SFTP);
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
            operation.setOperationResponse("File uploaded to the device successfully ( " + fileName + " ).");
        } catch (IOException | JSchException | SftpException e) {
            handleOperationError(operation, fileTransferExceptionCause(e, fileName), e);
        } finally {
            if (sftpChannel != null) {
                sftpChannel.exit();
            }
            if (session != null) {
                session.disconnect();
            }
            cleanupStreams(null, null, null, null, bufferedInputStream, bufferedOutputStream, null, null);
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
                    response = "File uploaded to the device successfully ( " + fileName + " ).";
                    operation.setStatus(resources.getString(R.string.operation_value_completed));
                } else {
                    response = "File uploaded to the device not completed ( " + fileName + " ).";
                    operation.setStatus(resources.getString(R.string.operation_value_error));
                }
                operation.setOperationResponse(response);
            } else {
                downloadFileUsingFTPSClient(operation, host, ftpUserName, ftpPassword,
                        savingLocation, fileName, serverPort, fileDirectory);
            }
        } catch (FTPConnectionClosedException | ConnectException e) {
            downloadFileUsingFTPSClient(operation, host, ftpUserName, ftpPassword,
                    savingLocation, fileName, serverPort, fileDirectory);
        } catch (IOException e) {
            handleOperationError(operation, fileTransferExceptionCause(e, fileName), e);
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (IOException ignored) {
            }
            cleanupStreams(null, outputStream, null, fileOutputStream, null, null, null, null);
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
                    response = "File uploaded to the device successfully ( " + fileName + " ).";
                    operation.setStatus(resources.getString(R.string.operation_value_completed));
                } else {
                    response = "File uploaded to the device not completed ( " + fileName + " ).";
                    operation.setStatus(resources.getString(R.string.operation_value_error));
                }
            } else {
                response = ftpUserName + " - FTP login failed.";
                operation.setStatus(resources.getString(R.string.operation_value_error));
            }
            operation.setOperationResponse(response);
        } catch (IOException e) {
            handleOperationError(operation, fileTransferExceptionCause(e, fileName), e);
        } finally {
            try {
                if (ftpsClient.isConnected()) {
                    ftpsClient.logout();
                    ftpsClient.disconnect();
                }
            } catch (IOException ignored) {
            }
            cleanupStreams(null, outputStream, null, fileOutputStream, null, null, null, null);
        }
    }


    /**
     * This method returns the cause of the exception.
     *
     * @param ex       - Exception object.
     * @param fileName - name of the file which caused exception.
     * @return - Exception cause.
     */
    public String fileTransferExceptionCause(Exception ex, String fileName) {
        if (ex.getCause() != null) {
            return fileName + " upload failed. Error :- " + ex.getCause().getMessage();
        } else {
            return fileName + " upload failed. Error :- " + ex.getLocalizedMessage();
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
    public String[] urlSplitter(Operation operation, String fileURL, boolean isUpload)
            throws URISyntaxException, AndroidAgentException {
        String serverPort = null;
        URI url = new URI(fileURL);
        String protocol = url.getScheme();
        String host = null;
        String ftpUserName = null;  // for anonymous FTP login.
        if (protocol != null) {
            if (protocol.equals(Constants.FileTransfer.FTP)) {
                serverPort = String.valueOf(21);
            } else if (protocol.equals(Constants.FileTransfer.SFTP)) {
                serverPort = String.valueOf(22);
            }
            if (url.getAuthority() != null) {
                String[] authority = url.getAuthority().split("@"); //provides username@hostname
                // Since hostname cannot contain any '@' signs, it should be last element.
                host = authority[authority.length - 1];
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
        } else {
            handleOperationError(operation, "Invalid URL", null);
        }
        if (isUpload) {
            return new String[]{ftpUserName, url.getPath(), host, serverPort, protocol};
        } else {
            File file = new File(url.getPath());
            return new String[]{ftpUserName, file.getParent(), host, serverPort, protocol, file.getName()};
        }
    }
}
