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
package org.wso2.iot.agent.utils;

import android.content.res.Resources;

import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.R;
import org.wso2.iot.agent.beans.Operation;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

public class FileTransferUtils {


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
    public static String[] urlSplitter(Operation operation, String fileURL, boolean isUpload,Resources resources)
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
            handleOperationError(operation, "Invalid URL", null,resources);
        }
        if (isUpload) {
            return new String[]{ftpUserName, url.getPath(), host, serverPort, protocol};
        } else {
            File file = new File(url.getPath());
            return new String[]{ftpUserName, file.getParent(), host, serverPort, protocol, file.getName()};
        }
    }

    public static void handleOperationError(Operation operation, String message, Exception exception,Resources resources)
            throws AndroidAgentException {
        operation.setStatus(resources.getString(R.string.operation_value_error));
        operation.setOperationResponse(message);
        if (exception != null) {
            throw new AndroidAgentException(message, exception);
        } else {
            throw new AndroidAgentException(message);
        }
    }

    /**
     * This method returns the cause of the exception.
     *
     * @param ex       - Exception object.
     * @param fileName - name of the file which caused exception.
     * @return - Exception cause.
     */
    public static String fileTransferExceptionCause(Exception ex, String fileName) {
        if (ex.getCause() != null) {
            return fileName + " upload failed. Error :- " + ex.getCause().getMessage();
        } else {
            return fileName + " upload failed. Error :- " + ex.getLocalizedMessage();
        }
    }

    public static void   cleanupStreams(InputStream inputStream, OutputStream outputStream,
                                                      FileInputStream fileInputStream, FileOutputStream fileOutputStream,
                                                      BufferedInputStream bufferedInputStream,
                                                      BufferedOutputStream bufferedOutputStream, DataInputStream
                                                              dataInputStream, DataOutputStream dataOutputstream) {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException ignored) {
            }
        }
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
        if (fileInputStream != null) {
            try {
                fileInputStream.close();
            } catch (IOException ignored) {
            }
        }
        if (dataInputStream != null) {
            try {
                dataInputStream.close();
            } catch (IOException ignored) {
            }
        }
        if (dataOutputstream != null) {
            try {
                dataOutputstream.flush();
                dataOutputstream.close();
            } catch (IOException ignored) {

            }
        }
    }

}
