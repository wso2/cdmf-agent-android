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
package org.wso2.iot.agent.transport.websocket;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.ServiceManager;
import android.support.annotation.RequiresApi;
import android.util.Log;

import org.java_websocket.drafts.Draft_17;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.iot.agent.api.DeviceInfo;
import org.wso2.iot.agent.beans.Operation;
import org.wso2.iot.agent.beans.ServerConfig;
import org.wso2.iot.agent.services.operation.OperationManager;
import org.wso2.iot.agent.services.operation.OperationManagerFactory;
import org.wso2.iot.agent.services.screenshare.ScreenSharingService;
import org.wso2.iot.agent.transport.exception.TransportHandlerException;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

import java.net.URI;
import java.net.URISyntaxException;

import static android.content.Context.MEDIA_PROJECTION_SERVICE;

/**
 * Class for handling web socket sessions
 */
public class WebSocketSessionHandler {

    private static WebSocketSessionHandler wsInstance;
    private Context context;
    private String serverUrl;
    private int operationId;
    private AndroidWebSocketClient androidWebSocketClient;
    private static final String TAG = WebSocketSessionHandler.class.getSimpleName();
    private static Object instance_lock = new Object();
    private OperationManager operationManager;
    private final Object writeLockObject = new Object();

    /**
     * Default constructor for the WebSocketSessionHandler.
     */
    private WebSocketSessionHandler(Context context) {
        this.context = context;
        OperationManagerFactory operationManagerFactory = new OperationManagerFactory(context);
        operationManager = operationManagerFactory.getOperationManager();
    }

    /**
     * return a singleton Instance
     *
     * @param context is the android context object.
     * @return WebSocketSessionHandler.
     */
    public static WebSocketSessionHandler getInstance(Context context) {
        if (wsInstance == null) {
            synchronized (instance_lock) {
                if (wsInstance == null) {
                    wsInstance = new WebSocketSessionHandler(context);
                }
            }
        }
        return wsInstance;
    }

    /**
     * Create new web socket session
     *
     * @param serverURL   Server URL
     * @param operationId operation id for initialized session
     * @throws TransportHandlerException throws when error occur with web socket session
     */
    public void initializeSession(String serverURL, int operationId, String uuidToValidateDevice)
            throws TransportHandlerException {
        if (this.operationId == operationId) {
            Log.w(TAG, "operation id : " + operationId + " is already connected");
            return;
        }
        DeviceInfo deviceInfo = new DeviceInfo(context);
        if (serverURL.contains("localhost")) {
            ServerConfig serverConfig = new ServerConfig();
            serverURL = serverURL.replace("localhost", serverConfig.getHostFromPreferences(context));
        }
        this.serverUrl = serverURL;
        this.operationId = operationId;
        if (androidWebSocketClient != null && androidWebSocketClient.getConnection() != null) {
            if (androidWebSocketClient.getConnection().isConnecting() || androidWebSocketClient.getConnection().isOpen()) {
                androidWebSocketClient.close();
            }
        }
        URI uri;
        String remoteEndpoint = serverURL + Constants.REMOTE_SESSION_DEVICE_ENDPOINT_CONTEXT + "/" +
                deviceInfo.getDeviceId() + "/" + operationId + "?websocketToken=" + uuidToValidateDevice;
        try {
            uri = new URI(remoteEndpoint);
            Log.i(TAG, "web socket session connected for operation id:" + operationId);
        } catch (URISyntaxException e) {
            throw new TransportHandlerException("Invalid Url : " + remoteEndpoint, e);
        }
        androidWebSocketClient = new AndroidWebSocketClient(context, uri, new Draft_17(), operationId);
        try {
            androidWebSocketClient.connectBlocking();
        } catch (InterruptedException e) {
            Log.e(TAG, "Web socket connection failed");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    /*
     * Use to handle web socket message
     */
    public void handleSessionMessage(String message) throws TransportHandlerException {
        Operation operation = new Operation();
        try {
            JSONObject request = new JSONObject(message);
            Object operationCode = request.get("code");
            if (operationCode != null) {
                operation.setCode(operationCode.toString());
                if (request.has("payload")) {
                    operation.setPayLoad(request.get("payload").toString());
                }
                operation.setId(this.operationId);
                switch (operation.getCode()) {
                    case Constants.Operation.REMOTE_INPUT:
                        operationManager.processInputInject(operation);
                        break;
                    case Constants.Operation.REMOTE_SHELL:
                        operationManager.processRemoteShell(operation);
                        break;
                    case Constants.Operation.REMOTE_LOGCAT:
                        operationManager.processRemoteShell(operation);
                        break;
                    case Constants.Operation.REMOTE_SCREEN:
                        operationManager.screenCapture(operation);
                        break;
                    default:
                        operation.setOperationResponse("operation code is not supported");
                        WebSocketSessionHandler.getInstance(context).sendMessage(operation);
                        break;
                }
            } else {
                throw new TransportHandlerException("WebSocket message is missing operation code");
            }
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            operation.setOperationResponse("Message payload cannot be parsed");
            WebSocketSessionHandler.getInstance(context).sendMessage(operation);
        }
    }

    /**
     * Close the web socket session
     */
    public void endSession() {
        context.stopService(new Intent(context, ScreenSharingService.class));
        if (androidWebSocketClient != null && androidWebSocketClient.getConnection() != null &&
                androidWebSocketClient.getConnection().isOpen()) {
            androidWebSocketClient.close();
        }
        context.stopService(new Intent(context, ScreenSharingService.class));
    }

    /**
     * Send String message to remote client using web socket session
     *
     * @param message String message
     */
    public void sendMessage(String message) {
        if (message != null && androidWebSocketClient != null && androidWebSocketClient.getConnection().isOpen()) {
            synchronized (writeLockObject) {
                androidWebSocketClient.send(message);
            }
        } else if (message == null) {
            Log.w(TAG, "Message cannot be null for operation id " + operationId);
        } else {
            Log.w(TAG, "android web service client already closed for operation id " + operationId);
        }
    }

    /**
     * Send byte message to remote client using web socket session
     *
     * @param message Byte message
     */
    public void sendMessage(byte[] message) {
        if (message != null && androidWebSocketClient != null && androidWebSocketClient.getConnection().isOpen()) {
            synchronized (writeLockObject) {
                androidWebSocketClient.send(message);
            }
        } else if (message == null) {
            Log.w(TAG, "Message cannot be null for operation id " + operationId);
        } else {
            Log.w(TAG, "android web service client already closed for operation id " + operationId);
        }
    }

    /**
     * Send message to remote client using web socket session
     *
     * @param operation Operation info for given message
     * @throws TransportHandlerException
     */
    public void sendMessage(Operation operation) throws TransportHandlerException {
        try {
            if (operation != null && operation.getId() == operationId) {
                JSONObject payload = new JSONObject();
                payload.put("id", operation.getId());
                payload.put("code", operation.getCode());
                payload.put("operationResponse", operation.getOperationResponse());
                payload.put("status", operation.getStatus());
                if (androidWebSocketClient != null && androidWebSocketClient.getConnection().isOpen()) {
                    synchronized (writeLockObject) {
                        androidWebSocketClient.send(payload.toString());
                    }
                }
            } else if (operation == null) {
                throw new TransportHandlerException("Operation cannot be null");
            } else {
                throw new TransportHandlerException("client session related to operation id is already closed");
            }
        } catch (JSONException e) {
            throw new TransportHandlerException("Message send failed due to JSON error ", e);
        }
    }

    /**
     * Gets operation id for currently connected session
     *
     * @return operation id
     */
    public int getOperationId() {
        return operationId;
    }

    public void setOperationId(int operationId) {
        this.operationId = operationId;
    }
}
