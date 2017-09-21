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

import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.command.ScpCommandFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.subsystem.SftpSubsystem;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 * Creates a SFTP server for testing.
 */
public class SFTPTestServer {

    private static final String TAG = SFTPTestServer.class.getSimpleName();
    private SshServer sftpServer;
    private String sftpUserName;
    private String sftpPassword;
    private String sftpHomeDirectory;
    private int port;

    public SFTPTestServer(String userName, String password, String homeDirectory, int port) {
        this.sftpUserName = userName;
        this.sftpPassword = password;
        this.sftpHomeDirectory = homeDirectory;
        this.port = port;
    }

    public void startSFTP() {

        Log.d(TAG, "Starting SFTP server.");
        sftpServer = SshServer.setUpDefaultServer();
        sftpServer.setFileSystemFactory(new VirtualFileSystemFactory(sftpHomeDirectory));
        sftpServer.setPort(port);
        sftpServer.setSubsystemFactories(Collections.<NamedFactory<Command>>singletonList(new SftpSubsystem.Factory()));
        sftpServer.setCommandFactory(new ScpCommandFactory());
        sftpServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(new File(sftpHomeDirectory).getAbsolutePath()));
        sftpServer.setPasswordAuthenticator(new PasswordAuthenticator() {
            public boolean authenticate(final String username, final String password, final ServerSession session) {
                return username.equals(sftpUserName) && password.equals(sftpPassword);
            }
        });
        try {
            sftpServer.start();
        } catch (IOException e) {
            Log.e(TAG, "SFTP sever starting exception " + e.getLocalizedMessage());
        }
    }

    public void stopSFTP() {
        try {
            if (sftpServer != null) {
                sftpServer.stop();
                Log.d(TAG, "Test SFTP Server stopped.");
            }
        } catch (InterruptedException ignored) {
        }
    }
}
