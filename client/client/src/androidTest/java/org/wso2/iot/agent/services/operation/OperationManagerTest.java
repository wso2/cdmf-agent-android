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
package org.wso2.iot.agent.services.operation;

import android.content.Context;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.beans.Operation;
import org.wso2.iot.agent.services.operation.util.FTPServer;
import org.wso2.iot.agent.services.operation.util.MockOperationManager;
import org.wso2.iot.agent.services.operation.util.SFTPServer;

import java.io.File;
import java.io.IOException;

import static android.support.test.InstrumentationRegistry.getTargetContext;

/**
 * Test class for OperationManager.java
 */
@RunWith(AndroidJUnit4.class)
public class OperationManagerTest {

    private static final String TAG = OperationManagerTest.class.getSimpleName();
    private Context context = getTargetContext();
    private String ftpDirectory = context.getFilesDir().toString();
    private String sftpDirectory = context.getFilesDir().toString();
    private FTPServer testFtpServer;
    private SFTPServer testSftpServer;

    public void createNewFile(String location) {
        Log.d(TAG, "Creating new file (" + location + ") for testing .");
        File file = new File(location);
        try {
            if (file.createNewFile()) { //Create  a new file.
                if (!file.exists()) {
                    Assert.fail("Test file creation in device failed.");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Test file creation failed. " + e.getLocalizedMessage());
            Assert.fail("Test file creation in device failed due to IO exception. " + e.getLocalizedMessage());
        }
    }

    public void deleteFile(String location) {
        Log.d(TAG, "Deleting file (" + location + ").");
        File file = new File(location);
        if (file.delete()) {
            Log.d(TAG, "File deleted successfully.");
        }
    }

    @Before
    public void createFiles() {
        createNewFile(ftpDirectory + File.separator + Constants.DOWNLOAD_FILE_NAME);
        createNewFile(Constants.DEVICE_DOWNLOAD_DIRECTORY + File.separator + Constants.UPLOAD_FILE_NAME);
    }

    @Before
    public void setupFTPServer() {
        testFtpServer = new FTPServer(Constants.USER_NAME, Constants.PASSWORD, ftpDirectory, Constants.FTP_PORT);
        testFtpServer.startFTP();
        testSftpServer = new SFTPServer(Constants.USER_NAME, Constants.PASSWORD, sftpDirectory, Constants.SFTP_PORT);
        testSftpServer.startSFTP();
    }

    public JSONObject generatePayload(String fileUrl, String password, String fileLocation) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("fileURL", fileUrl);
            payload.put("ftpPassword", password);
            payload.put("fileLocation", fileLocation);
        } catch (JSONException e) {
            Log.e(TAG, "JSON Exception in preparing payload. " + e.getLocalizedMessage());
        }
        return payload;
    }

    @Test
    public void testDownloadFileFTPClientFileLocationSpecified() {
        String fileUrl = Constants.FTP_SCHEME + Constants.USER_NAME + Constants.LOCAL_HOST +
                Constants.FTP_PORT + File.separator + Constants.DOWNLOAD_FILE_NAME;
        testDownloadFile(generatePayload(fileUrl, Constants.PASSWORD, Constants.DEVICE_DOWNLOAD_DIRECTORY));
    }

    @Test
    public void testDownloadFileFTPClientFileLocationNotSpecified() {
        String fileUrl = Constants.FTP_SCHEME + Constants.USER_NAME + Constants.LOCAL_HOST + Constants.FTP_PORT
                + File.separator + Constants.DOWNLOAD_FILE_NAME;
        testDownloadFile(generatePayload(fileUrl, Constants.PASSWORD, ""));
    }

    @Test
    public void testDownloadFileSFTPClientFileLocationSpecified() {
        String fileUrl = Constants.SFTP_SCHEME + Constants.USER_NAME + Constants.LOCAL_HOST
                + Constants.SFTP_PORT + File.separator + Constants.DOWNLOAD_FILE_NAME;
        testDownloadFile(generatePayload(fileUrl, Constants.PASSWORD, Constants.DEVICE_DOWNLOAD_DIRECTORY));
    }

    @Test
    public void testDownloadFileSFTPClientFileLocationNotSpecified() {
        String fileUrl = Constants.SFTP_SCHEME + Constants.USER_NAME + Constants.LOCAL_HOST +
                Constants.SFTP_PORT + File.separator + Constants.DOWNLOAD_FILE_NAME;
        testDownloadFile(generatePayload(fileUrl, Constants.PASSWORD, ""));
    }

    public void testDownloadFile(JSONObject payload) {
        Log.d(TAG, "Testing for payload: " + payload);
        OperationManager operationManager = new MockOperationManager(context);
        Operation operation = new Operation();
        operation.setPayLoad(payload);
        try {
            operationManager.downloadFile(operation);
        } catch (AndroidAgentException e) {
            Log.e(TAG, "Android agent exception." + e.getLocalizedMessage());
        }
        Log.d(TAG, "Operation status: \"" + operation.getStatus() + "\". Operation response: "
                + operation.getOperationResponse());
        Assert.assertEquals(operation.getOperationResponse(), Constants.STATUS_COMPLETED, operation.getStatus());
    }

    @Test
    public void testUploadFileFTPClient() {
        String fileUrl = Constants.FTP_SCHEME + Constants.USER_NAME + Constants.LOCAL_HOST +
                Constants.FTP_PORT + File.separator;
        String fileLocation = Constants.DEVICE_DOWNLOAD_DIRECTORY + File.separator + Constants.UPLOAD_FILE_NAME;
        testUploadFile(generatePayload(fileUrl, Constants.PASSWORD, fileLocation));
    }

    @Test
    public void testUploadFileSFTPClient() {
        String fileUrl = Constants.SFTP_SCHEME + Constants.USER_NAME + Constants.LOCAL_HOST +
                Constants.SFTP_PORT + File.separator;
        String fileLocation = Constants.DEVICE_DOWNLOAD_DIRECTORY + File.separator + Constants.UPLOAD_FILE_NAME;
        testUploadFile(generatePayload(fileUrl, Constants.PASSWORD, fileLocation));
    }


    public void testUploadFile(JSONObject payload) {
        Log.d(TAG, "Testing for payload: " + payload);
        OperationManager operationManager = new MockOperationManager(context);
        Operation operation = new Operation();
        operation.setPayLoad(payload);
        try {
            operationManager.uploadFile(operation);
        } catch (AndroidAgentException e) {
            Log.e(TAG, "Android agent exception. " + e.getLocalizedMessage());
        }
        Log.d(TAG, "Operation status: \"" + operation.getStatus() + "\". Operation response: "
                + operation.getOperationResponse());
        Assert.assertEquals(operation.getOperationResponse(), Constants.STATUS_COMPLETED, operation.getStatus());
    }

    @After
    public void stopServers() {
        if (testFtpServer != null) {
            testFtpServer.stopFTP();
        }
        if (testSftpServer != null) {
            testSftpServer.stopSFTP();
        }
        deleteFile(ftpDirectory + File.separator + Constants.DOWNLOAD_FILE_NAME);
        deleteFile(Constants.DEVICE_DOWNLOAD_DIRECTORY + File.separator + Constants.UPLOAD_FILE_NAME);
    }
}
