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
import android.os.Environment;
import android.support.test.InstrumentationRegistry;
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

/**
 * Test class for OperationManager.java
 */
@RunWith(AndroidJUnit4.class)
public class OperationManagerTest {

    private static final String TAG = OperationManagerTest.class.getSimpleName();
    private String DEVICE_DOWNLOAD_DIRECTORY = Environment.getExternalStoragePublicDirectory
            (Environment.DIRECTORY_DOWNLOADS).toString();
    private String DOWNLOAD_FILE_NAME = "DownloadTestFile.txt";
    private String UPLOAD_FILE_NAME = "UploadTestFile.txt";
    private Context CONTEXT = InstrumentationRegistry.getTargetContext();
    private String FTP_DIRECTORY = CONTEXT.getFilesDir().toString();
    private String SFTP_DIRECTORY = CONTEXT.getFilesDir().toString();
    private String USER_NAME = "test";
    private String PASSWORD = "test";
    private String STATUS_COMPLETED = "COMPLETED";
    private FTPServer TEST_FTP_SERVER;
    private SFTPServer TEST_SFTP_SERVER;
    private int FTP_PORT = 2221;
    private int SFTP_PORT = 2223;

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
            Log.d(TAG, "Test file creation in failed.");
            Assert.fail("Test file creation in device failed due to IO exception.");
        }
    }

    @Before
    public void setupFTPServer() {
        createNewFile(FTP_DIRECTORY + File.separator + DOWNLOAD_FILE_NAME);
        createNewFile(DEVICE_DOWNLOAD_DIRECTORY + File.separator + UPLOAD_FILE_NAME);
        TEST_FTP_SERVER = new FTPServer(USER_NAME, PASSWORD, FTP_DIRECTORY,FTP_PORT);
        TEST_FTP_SERVER.startFTP();
        TEST_SFTP_SERVER = new SFTPServer(USER_NAME, PASSWORD, SFTP_DIRECTORY,SFTP_PORT);
        TEST_SFTP_SERVER.startSFTP();
    }

    public JSONObject preparePayload(String fileUrl, String password, String fileLocation) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("fileURL", fileUrl);
            payload.put("ftpPassword", password);
            payload.put("fileLocation", fileLocation);
        } catch (JSONException e) {
            Log.e(TAG, "JSON Exception in preparing payload.");
        }
        return payload;
    }

    @Test
    public void testDownloadFileFTPClientFileLocationSpecified() {
        String fileUrl = "ftp://" + USER_NAME + "@localhost:" + FTP_PORT + "/" + DOWNLOAD_FILE_NAME;
        testDownloadFile(preparePayload(fileUrl, PASSWORD, DEVICE_DOWNLOAD_DIRECTORY));
    }

    @Test
    public void testDownloadFileFTPClientFileLocationNotSpecified() {
        String fileUrl = "ftp://" + USER_NAME + "@localhost:" + FTP_PORT + "/" + DOWNLOAD_FILE_NAME;
        testDownloadFile(preparePayload(fileUrl, PASSWORD, ""));
    }

    @Test
    public void testDownloadFileSFTPClientFileLocationSpecified() {
        String fileUrl = "sftp://" + USER_NAME + "@localhost:" + SFTP_PORT + "/" + DOWNLOAD_FILE_NAME;
        testDownloadFile(preparePayload(fileUrl, PASSWORD, DEVICE_DOWNLOAD_DIRECTORY));
    }

    @Test
    public void testDownloadFileSFTPClientFileLocationNotSpecified() {
        String fileUrl = "sftp://" + USER_NAME + "@localhost:" + SFTP_PORT + "/" + DOWNLOAD_FILE_NAME;
        testDownloadFile(preparePayload(fileUrl, PASSWORD, ""));
    }

    public void testDownloadFile(JSONObject payload) {
        Log.d(TAG, "Testing for payload: " + payload);
        OperationManager operationManager = new MockOperationManager(CONTEXT);
        Operation operation = new Operation();
        operation.setPayLoad(payload);
        try {
            operationManager.downloadFile(operation);
        } catch (AndroidAgentException e) {
            Log.e(TAG, "Android agent exception.");
        }
        Log.d(TAG, "Operation status: \"" + operation.getStatus() + "\". Operation response: "
                + operation.getOperationResponse());
        Assert.assertEquals(operation.getOperationResponse(), STATUS_COMPLETED, operation.getStatus());
    }

    @Test
    public void testUploadFileFTPClient() {
        String fileUrl = "ftp://" + USER_NAME + "@localhost:" + FTP_PORT + "/";
        String fileLocation = DEVICE_DOWNLOAD_DIRECTORY + File.separator + UPLOAD_FILE_NAME;
        testUploadFile(preparePayload(fileUrl, PASSWORD, fileLocation));
    }

    @Test
    public void testUploadFileSFTPClient() {
        String fileUrl = "sftp://" + USER_NAME + "@localhost:" + SFTP_PORT + "/";
        String fileLocation = DEVICE_DOWNLOAD_DIRECTORY + File.separator + UPLOAD_FILE_NAME;
        testUploadFile(preparePayload(fileUrl, PASSWORD, fileLocation));
    }


    public void testUploadFile(JSONObject payload) {
        Log.d(TAG, "Testing for payload: " + payload);
        OperationManager operationManager = new MockOperationManager(CONTEXT);

        Operation operation = new Operation();
        operation.setPayLoad(payload);

        try {
            operationManager.uploadFile(operation);
        } catch (AndroidAgentException e) {
            Log.e(TAG, "Android agent exception.");
        }
        Log.d(TAG, "Operation status: \"" + operation.getStatus() + "\". Operation response: "
                + operation.getOperationResponse());
        Assert.assertEquals(operation.getOperationResponse(), STATUS_COMPLETED, operation.getStatus());
    }

    @After
    public void stopServers() {
        TEST_FTP_SERVER.stopFTP();
        TEST_SFTP_SERVER.stopSFTP();
    }
}
