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
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;
import org.wso2.iot.agent.transport.exception.TransportHandlerException;

import java.net.URI;

/**
 * Class for web socket client connection
 */
public class AndroidWebSocketClient extends WebSocketClient {

    private Context context;
    private String TAG = "wsClient";
    private int operationId;

    /**
     * Create new web socket client connection
     * @param context device context
     * @param serverUri server uri
     * @param draft draft
     * @param operationId operation id for remote session request
     */
    public AndroidWebSocketClient(Context context, URI serverUri, Draft draft, int operationId) {
        super(serverUri, draft);
        this.context = context;
        this.operationId = operationId;

    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        if (this.operationId != WebSocketSessionHandler.getInstance(context).getOperationId()) {
            this.close();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onMessage(String message) {
        try {
            WebSocketSessionHandler.getInstance(context).handleSessionMessage(message);
        } catch (TransportHandlerException e) {
            Log.e(TAG, "Erroc occurred while handling incoming web socket message");
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        Log.e(TAG, "Close the web socket connection due to  " + reason);
        WebSocketSessionHandler.getInstance(context).endSession();
    }

    @Override
    public void onError(Exception ex) {
        Log.e(TAG, "Error occurred while handling web socket session ", ex);
    }
}
