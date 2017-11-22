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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.iot.agent.services.shell;

import android.content.Context;
import android.util.Log;

import org.wso2.iot.agent.beans.Operation;
import org.wso2.iot.agent.transport.exception.TransportHandlerException;
import org.wso2.iot.agent.transport.websocket.WebSocketSessionHandler;
import org.wso2.iot.agent.utils.Constants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Class {@link RemoteShellTask} for execute remote shell command.
 * This will run as background job
 */
public class RemoteShellTask extends Thread {

    private static final String TAG = RemoteShellTask.class.getSimpleName();

    private InputStream inputStream = null;
    private InputStream errorStream = null;
    ByteArrayOutputStream arrayInputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream arrayErrorStream = new ByteArrayOutputStream();
    private Operation operation;
    private Context context;
    private Process process;
    String[] command = null;

    RemoteShellTask(Context context, Operation operation) {
        this.context = context;
        this.operation = operation;
    }

    public void run() {
        try {
            if (operation.getCode().equals(Constants.Operation.REMOTE_LOGCAT)) {
                command = new String[]{"logcat", "-v", "time", "-t", Constants.DEFAULT_LOGCAT_LINES};
            } else {
                if (operation.getPayLoad() != null) {
                    command = new String[]{"sh", "-c", operation.getPayLoad().toString()};
                } else {
                    operation.setOperationResponse("Message payload is missing");
                    WebSocketSessionHandler.getInstance(context).sendMessage(operation);
                }
            }
            if (command != null) {
                process = Runtime.getRuntime().exec(command);
                inputStream = process.getInputStream();
                errorStream = process.getErrorStream();

                byte[] buffer = new byte[1024];
                int length;
                int sentCount = 0, sizeCount = 0;
                long messageSentTimeMillis = System.currentTimeMillis();
                while ((length = inputStream.read(buffer)) != -1) {
                    arrayInputStream.write(buffer, 0, length);
                    if (sizeCount >= Constants.MAX_MESSAGE_SIZE || (System.currentTimeMillis() - messageSentTimeMillis) >
                            Constants.MESSAGE_RATE_MILLISECONDS) {
                        operation.setOperationResponse(new String(arrayInputStream.toByteArray()));
                        WebSocketSessionHandler.getInstance(context).sendMessage(operation);
                        arrayInputStream.reset();
                        sizeCount = 0;
                        sentCount++;
                        messageSentTimeMillis = System.currentTimeMillis();
                    } else {
                        sizeCount++;
                    }
                    if (sentCount >= Constants.MAX_MESSAGES_PER_OPERATION) {
                        break;
                    }
                }
                if (sentCount < Constants.MAX_MESSAGES_PER_OPERATION) {
                    byte[] bytes = arrayInputStream.toByteArray();
                    if (bytes != null && bytes.length > 0) {
                        operation.setOperationResponse(new String(arrayInputStream.toByteArray()));
                        WebSocketSessionHandler.getInstance(context).sendMessage(operation);
                        sentCount++;
                    }
                    sizeCount = 0;
                    while ((length = errorStream.read(buffer)) != -1) {
                        arrayErrorStream.write(buffer, 0, length);
                        if (sizeCount >= Constants.MAX_MESSAGE_SIZE) {
                            operation.setOperationResponse(new String(arrayErrorStream.toByteArray()));
                            WebSocketSessionHandler.getInstance(context).sendMessage(operation);
                            arrayErrorStream.reset();
                            sizeCount = 0;
                            sentCount++;
                        } else {
                            sizeCount++;
                        }
                        if (sentCount > Constants.MAX_MESSAGES_PER_OPERATION) {
                            break;
                        }
                    }
                    bytes = arrayErrorStream.toByteArray();
                    if (bytes != null && bytes.length > 0) {
                        operation.setOperationResponse(new String(arrayErrorStream.toByteArray()));
                        WebSocketSessionHandler.getInstance(context).sendMessage(operation);
                    }
                }
                operation.setStatus("COMPLETED");
                operation.setOperationResponse("");
                WebSocketSessionHandler.getInstance(context).sendMessage(operation);
            }
        } catch (IOException e) {
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.e(TAG, "IO exception occurred while executing the command :" + command);
            }
        } catch (TransportHandlerException e) {
            Log.e(TAG, "Error occurred while handling incoming web socket message :" + e);
        } finally {
            cleanupResources();
        }
    }

    /**
     * Cleanup the resources and interruppt the task if blocked on io operation
     */
    public void close() {
        cleanupResources();
        this.interrupt();
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Remote shell task interrupted");
        }

    }

    /**
     * Cleanup the resources used for remote shell
     */
    private void cleanupResources() {
        if (process != null) {
            try {
                process.destroy();
            } catch (Exception e) {
                Log.e(TAG, "Error occurred while destroying the process for command " + command + e.getMessage());
            }
        }
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred while closing data input stream");
            }
        }
        if (errorStream != null) {
            try {
                errorStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred while closing data error stream");
            }
        }
        if (arrayInputStream != null) {
            try {
                arrayInputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred while closing buffer input stream");
            }
        }
        if (arrayErrorStream != null) {
            try {
                arrayErrorStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred while closing buffer error stream");
            }
        }
    }
}