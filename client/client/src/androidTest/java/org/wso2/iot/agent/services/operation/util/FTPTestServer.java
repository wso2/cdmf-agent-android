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
package org.wso2.iot.agent.services.operation.util;

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

import java.util.ArrayList;
import java.util.List;

/**
 * Creates an FTP server for testing.
 */
public class FTPTestServer {

    private static final String TAG = FTPTestServer.class.getSimpleName();
    private String ftpDirectory;
    private String userName;
    private String password;
    private FtpServer ftpServer;
    private int port;

    public FTPTestServer(String userName, String password, String homeDirectory, int port) {
        this.userName = userName;
        this.password = password;
        this.ftpDirectory = homeDirectory;
        this.port = port;
    }

    public void startFTP() {
        Log.d(TAG, "Starting FTP server.");
        PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
        UserManager userManager = userManagerFactory.createUserManager();
        BaseUser user = new BaseUser();
        user.setName(userName);
        user.setPassword(password);
        List<Authority> authorities = new ArrayList<>();
        authorities.add(new WritePermission());
        user.setAuthorities(authorities);
        user.setHomeDirectory(ftpDirectory);
        try {
            userManager.save(user);
            ListenerFactory listenerFactory = new ListenerFactory();
            listenerFactory.setPort(port);
            FtpServerFactory factory = new FtpServerFactory();
            factory.setUserManager(userManager);
            factory.addListener("default", listenerFactory.createListener());
            ftpServer = factory.createServer();
            ftpServer.start();
            Log.d(TAG, "Test FTP Server started.");
        } catch (FtpException e) {
            Log.e(TAG, "FTP server starting exception " + e.getLocalizedMessage());
        }
    }

    public void stopFTP() {
        if (ftpServer != null) {
            ftpServer.stop();
            Log.d(TAG, "Test FTP Server stopped.");
        }
    }
}

