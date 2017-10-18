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
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Base64;
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

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;

import static org.wso2.iot.agent.utils.FileTransferUtils.cleanupStreams;
import static org.wso2.iot.agent.utils.FileTransferUtils.fileTransferExceptionCause;
import static org.wso2.iot.agent.utils.FileTransferUtils.handleOperationError;
import static org.wso2.iot.agent.utils.FileTransferUtils.urlSplitter;


/**
 * The service which is responsible for uploading files.
 */
public class FileUploadService extends IntentService {

    private static final String TAG = FileUploadService.class.getSimpleName();
    private Resources resources;
    SharedPreferences.Editor editor;

    public FileUploadService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Log.d(TAG, "Starting File upload service");
        resources = getResources();
        if (intent != null) {
            Operation operation = (Operation) intent.getExtras().getSerializable("operation");
            try {
                uploadFile(operation);
            } catch (AndroidAgentException e) {
                Log.d(TAG, e.getLocalizedMessage());
            }
        }
        this.stopSelf();
    }

    /**
     * Upload file to the FTP server from the device.
     *
     * @param operation - Operation object.
     */
    public void uploadFile(Operation operation) throws AndroidAgentException {

        String fileName = "Unknown";
        editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        validateOperation(operation);
        try {
            JSONObject inputData = new JSONObject(operation.getPayLoad().toString());
            final String fileURL = inputData.getString(Constants.FileTransfer.FILE_URL);
            final String userName = inputData.getString(Constants.FileTransfer.USER_NAME);
            final String password = inputData.getString(Constants.FileTransfer.PASSWORD);
            final String fileLocation = inputData.getString(Constants.FileTransfer.FILE_LOCATION);

            if (fileURL.startsWith(Constants.FileTransfer.HTTP)) {
                uploadFileUsingHTTPClient(operation, fileURL, userName, password,
                        fileLocation);
            } else {
                String[] userInfo = urlSplitter(operation, fileURL, true, resources);
                String ftpUserName;
                if (userName.isEmpty()) {
                    ftpUserName = userInfo[0];
                } else {
                    ftpUserName = userName;
                }
                String uploadDirectory = userInfo[1];
                String host = userInfo[2];
                int serverPort = 0;
                if (userInfo[3] != null) {
                    serverPort = Integer.parseInt(userInfo[3]);
                }
                String protocol = userInfo[4];
                File file = new File(fileLocation);
                fileName = file.getName();
                if (Constants.DEBUG_MODE_ENABLED) {
                    printLogs(ftpUserName, host, fileName, file.getParent(), uploadDirectory, serverPort);
                }
                selectUploadClient(protocol, operation, host, ftpUserName, password,
                        uploadDirectory, fileLocation, serverPort);
            }
        } catch (JSONException | URISyntaxException e) {
            handleOperationError(operation, fileTransferExceptionCause(e, fileName), e, resources);
        } finally {
            Log.d(TAG, operation.getStatus());
            setResponse(operation);
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

    private void setResponse(Operation operation) {
        operation.setEnabled(true);
        editor.putInt(resources.getString(R.string.FILE_UPLOAD_ID), operation.getId());
        editor.putString(resources.getString(R.string.FILE_UPLOAD_STATUS), operation.getStatus());
        editor.putString(resources.getString(R.string.FILE_UPLOAD_RESPONSE), operation.getOperationResponse());
        editor.apply();
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
     * This method is used to upload the using HTTP client , this supports BASIC authentication,
     * if user name is provided.
     *
     * @param operation    - Operation object
     * @param uploadURL    - HTTP POST endpoint url
     * @param userName     - username (can be empty)
     * @param password     - password (can be empty)
     * @param fileLocation - local location of the file in device
     * @throws AndroidAgentException - Android agent exception
     */
    private void uploadFileUsingHTTPClient(Operation operation, String uploadURL, final String userName,
                                           final String password,
                                           String fileLocation) throws AndroidAgentException {

        int serverResponseCode;
        HttpURLConnection connection = null;
        DataOutputStream dataOutputStream = null;
        final String lineEnd = "\r\n";
        final String twoHyphens = "--";
        final String boundary = "*****";

        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1024 * 1024; // 1 MB
        File selectedFile = new File(fileLocation);
        final String fileName = selectedFile.getName();
        FileInputStream fileInputStream = null;

        try {
            fileInputStream = new FileInputStream(selectedFile);
            URL url = new URL(uploadURL);
            connection = getHttpConnection(url, userName, password);
            connection.setRequestProperty("uploaded_file", fileLocation);

            dataOutputStream = new DataOutputStream(connection.getOutputStream());
            dataOutputStream.writeBytes(twoHyphens + boundary + lineEnd);
            dataOutputStream.writeBytes("Content-Disposition: form-data; name=\"file\";filename=\""
                    + fileName + "\"" + lineEnd);
            dataOutputStream.writeBytes(lineEnd);
            bytesAvailable = fileInputStream.available();
            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            buffer = new byte[bufferSize];
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);

            //loop repeats till bytesRead = -1, i.e., no bytes are left to read
            while (bytesRead > 0) {
                //write the bytes read from inputStream
                dataOutputStream.write(buffer, 0, bufferSize);
                bytesAvailable = fileInputStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);
            }
            dataOutputStream.writeBytes(lineEnd);
            dataOutputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            serverResponseCode = connection.getResponseCode();
            Log.i(TAG, "Server Response is: " + connection.getResponseMessage() + ": " + serverResponseCode);
            if (serverResponseCode == 200) {
                operation.setStatus(resources.getString(R.string.operation_value_completed));
                operation.setOperationResponse("File uploaded from the device completed ( " +
                        fileName + " ).");
            }
        } catch (IOException e) {
            handleOperationError(operation, fileTransferExceptionCause(e, fileName), e, resources);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            cleanupStreams(null, null, fileInputStream, null, null, null, null, dataOutputStream);
        }
    }

    /**
     * This methods
     *
     * @param url      - the url to make connection
     * @param userName - user name if authenticated.
     * @param password - password if authenticated.
     * @return HTTPURLConnection
     * @throws IOException - IOException
     */
    private HttpURLConnection getHttpConnection(URL url, String userName, String password) throws IOException {
        final String boundary = "*****";
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (!userName.isEmpty()) {
            String encoded = Base64.encodeToString((userName + ":" + password).getBytes("UTF-8"), Base64.DEFAULT);  //Java 8
            connection.setRequestProperty("Authorization", "Basic " + encoded);
        }
        connection.setDoInput(true);//Allow Inputs
        connection.setDoOutput(true);//Allow Outputs
        connection.setUseCaches(false);//Don't use a cached Copy
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Connection", "Keep-Alive");
        connection.setRequestProperty("ENCTYPE", "multipart/form-data");
        connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
        return connection;
    }

    private void selectUploadClient(String protocol, Operation operation, String host, String ftpUserName,
                                    String ftpPassword, String uploadDirectory, String fileLocation,
                                    int serverPort) throws AndroidAgentException {
        switch (protocol) {
            case Constants.FileTransfer.SFTP:
                uploadFileUsingSFTPClient(operation, host, ftpUserName, ftpPassword,
                        uploadDirectory, fileLocation, serverPort);
                break;
            case Constants.FileTransfer.FTP:
                uploadFileUsingFTPClient(operation, host, ftpUserName, ftpPassword,
                        uploadDirectory, fileLocation, serverPort);
                break;
            default:
                handleOperationError(operation, "Protocol ( " + protocol + " ) not supported.", null, resources);
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
                response = "File uploaded from the device completed ( " + fileName + " ).";
                operation.setStatus(resources.getString(R.string.operation_value_completed));
            } else {
                response = "File uploaded from the device not completed ( " + fileName + " ).";
                operation.setStatus(resources.getString(R.string.operation_value_error));
            }
            operation.setOperationResponse(response);
        } catch (FTPConnectionClosedException e) {
            uploadFileUsingFTPSClient(operation, host, ftpUserName, ftpPassword,
                    uploadDirectory, fileLocation, serverPort);
        } catch (IOException e) {
            handleOperationError(operation, fileTransferExceptionCause(e, fileName), e, resources);
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
            cleanupStreams(inputStream, null, null, null, null, null, null, null);
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
                response = "File uploaded from the device completed ( " + fileName + " ).";
                operation.setStatus(resources.getString(R.string.operation_value_completed));
            } else {
                response = "File uploaded from the device not completed ( " + fileName + " ).";
                operation.setStatus(resources.getString(R.string.operation_value_error));
            }
            operation.setOperationResponse(response);
        } catch (IOException e) {
            if (!loginResult) {
                response = ftpUserName + " - FTP login failed.";
            } else {
                response = fileTransferExceptionCause(e, fileName);
            }
            handleOperationError(operation, response, e, resources);
        } finally {
            try {
                if (ftpsClient.isConnected()) {
                    ftpsClient.logout();
                    ftpsClient.disconnect();
                }
            } catch (IOException ignored) {
            }
            cleanupStreams(inputStream, null, null, null, null, null, null, null);
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
        FileInputStream fileInputStream = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(ftpUserName, host, serverPort);
            session.setPassword(ftpPassword);
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            channel = session.openChannel(Constants.FileTransfer.SFTP);
            channel.connect();
            channelSftp = (ChannelSftp) channel;
            channelSftp.cd(uploadDirectory);
            File file = new File(fileLocation);
            fileName = file.getName();
            fileInputStream = new FileInputStream(file);
            channelSftp.put(fileInputStream, fileName);
            operation.setStatus(resources.getString(R.string.operation_value_completed));
            operation.setOperationResponse("File uploaded from the device successfully ( " + fileName + " ).");
        } catch (JSchException | FileNotFoundException | SftpException e) {
            handleOperationError(operation, fileTransferExceptionCause(e, fileName), e, resources);
        } finally {
            cleanupStreams(null, null, fileInputStream, null, null, null, null, null);
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

}
