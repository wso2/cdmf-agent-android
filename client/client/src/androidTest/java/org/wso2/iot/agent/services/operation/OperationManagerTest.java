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

import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.command.ScpCommandFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.subsystem.SftpSubsystem;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.beans.Operation;
import org.wso2.iot.agent.services.operation.util.MockOperationManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test class for OperationManager.java
 */
@RunWith(AndroidJUnit4.class)
public class OperationManagerTest {

    private static final String TAG = OperationManagerTest.class.getSimpleName();
    private FtpServer FTP_SERVER;
    private SshServer SFTP_SERVER;
    private String DEVICE_DOWNLOAD_DIRECTORY = Environment.getExternalStoragePublicDirectory
            (Environment.DIRECTORY_DOWNLOADS).toString();
    private String TEST_FILE_NAME = "testFile.txt";
    private Context CONTEXT = InstrumentationRegistry.getTargetContext();
    private String FTP_DIRECTORY = CONTEXT.getFilesDir().toString();
    private String SFTP_DIRECTORY = CONTEXT.getFilesDir().toString();
    private String USER_NAME = "test";
    private String PASSWORD = "test";

    @Before
    public void setupFTPServer() {

        Log.d(TAG, "Starting FTP server.");
        File file = new File(FTP_DIRECTORY + File.separator + TEST_FILE_NAME);

        try {
            if (file.createNewFile()) { //Create the file
                if (!file.exists()) {
                    Assert.fail("Test file creation in device failed.");
                }
            }
        } catch (IOException e) {
            Log.d(TAG, "Test file creation in device exception.");
        }

        PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
        UserManager userManager = userManagerFactory.createUserManager();
        BaseUser user = new BaseUser();
        user.setName(USER_NAME);
        user.setPassword(PASSWORD);
        List<Authority> authorities = new ArrayList<>();
        authorities.add(new WritePermission());
        user.setAuthorities(authorities);
        user.setHomeDirectory(FTP_DIRECTORY);

        try {
            userManager.save(user);
        } catch (FtpException e) {
            e.printStackTrace();
        }

        ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(2221);
        FtpServerFactory factory = new FtpServerFactory();
        factory.setUserManager(userManager);
        factory.addListener("default", listenerFactory.createListener());
        FTP_SERVER = factory.createServer();

        try {
            FTP_SERVER.start();
            Log.d(TAG, "Test FTP Server started.");
        } catch (FtpException e) {
            e.printStackTrace();
        }
    }

    @Before
    public void setupSFTPServer() {

        SFTP_SERVER = SshServer.setUpDefaultServer();
        SFTP_SERVER.setFileSystemFactory(new VirtualFileSystemFactory(SFTP_DIRECTORY));
        SFTP_SERVER.setPort(2223);
        SFTP_SERVER.setSubsystemFactories(Collections.<NamedFactory<Command>>singletonList(new SftpSubsystem.Factory()));
        SFTP_SERVER.setCommandFactory(new ScpCommandFactory());
        SFTP_SERVER.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(new File(SFTP_DIRECTORY).getAbsolutePath()));

        SFTP_SERVER.setPasswordAuthenticator(new PasswordAuthenticator() {
            public boolean authenticate(final String username, final String password, final ServerSession session) {
                return username.equals(USER_NAME) && password.equals(PASSWORD);
            }
        });
        try {
            SFTP_SERVER.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testDownloadFile() {

        OperationManager operationManager = new MockOperationManager(CONTEXT);

        Object[] payloads = new Object[4];

        payloads[0] = "{\"fileURL\":\"ftp://" + USER_NAME + "@localhost:2221/" + TEST_FILE_NAME + "\"," +
                "\"ftpPassword\":\"" + PASSWORD + "\",\"fileLocation\":\"" + DEVICE_DOWNLOAD_DIRECTORY + "\"}";

        payloads[1] = "{\"fileURL\":\"ftp://" + USER_NAME + "@localhost:2221/" + TEST_FILE_NAME + "\"," +
                "\"ftpPassword\":\"" + PASSWORD + "\",\"fileLocation\":\"\"}";

        payloads[2] = "{\"fileURL\":\"sftp://" + USER_NAME + "@localhost:2223/" + TEST_FILE_NAME + "\"," +
                "\"ftpPassword\":\"" + PASSWORD + "\",\"fileLocation\":\"" + DEVICE_DOWNLOAD_DIRECTORY + "\"}";

        payloads[3] = "{\"fileURL\":\"sftp://" + USER_NAME + "@localhost:2223/" + TEST_FILE_NAME + "\"," +
                "\"ftpPassword\":\"" + PASSWORD + "\",\"fileLocation\":\"\"}";

        for (Object payload : payloads) {
            org.wso2.iot.agent.beans.Operation operation = new Operation();
            operation.setPayLoad(payload);
            Log.d(TAG, "Testing for payload: " + payload);
            try {
                operationManager.downloadFile(operation);
            } catch (AndroidAgentException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "Operation status: \"" + operation.getStatus() + "\". Operation response: "
                    + operation.getOperationResponse());
            Assert.assertEquals(operation.getOperationResponse(), "COMPLETED", operation.getStatus());
        }
    }

    @Test
    public void testUploadFile() {

        Context context = InstrumentationRegistry.getTargetContext();
        OperationManager operationManager = new MockOperationManager(context);

        Object[] payloads = new Object[2];

        payloads[0] = "{\"fileURL\":\"ftp://" + USER_NAME + "@localhost:2221/\"," +
                "\"ftpPassword\":\"" + PASSWORD + "\",\"fileLocation\":\"" + DEVICE_DOWNLOAD_DIRECTORY +
                File.separator + TEST_FILE_NAME + "\"}";

        payloads[1] = "{\"fileURL\":\"sftp://" + USER_NAME + "@localhost:2223/\"," +
                "\"ftpPassword\":\"" + PASSWORD + "\",\"fileLocation\":\"" + DEVICE_DOWNLOAD_DIRECTORY +
                File.separator + TEST_FILE_NAME + "\"}";

        for (Object payload : payloads) {
            org.wso2.iot.agent.beans.Operation operation = new Operation();
            operation.setPayLoad(payload);
            Log.d(TAG, "Testing for payload: " + payload);
            try {
                operationManager.uploadFile(operation);
            } catch (AndroidAgentException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "Operation status: \"" + operation.getStatus() + "\". Operation response: "
                    + operation.getOperationResponse());
            Assert.assertEquals(operation.getOperationResponse(), "COMPLETED", operation.getStatus());
        }
    }

    @After
    public void stopFTPServer() {
        if (FTP_SERVER != null) {
            FTP_SERVER.stop();
            Log.d(TAG, "Test FTP Server stopped.");
        }
    }

    @After
    public void stopSFTPServer() {
        try {
            if (SFTP_SERVER != null) {
                SFTP_SERVER.stop();
            }
        } catch (InterruptedException ignored) {
        }
    }
}
