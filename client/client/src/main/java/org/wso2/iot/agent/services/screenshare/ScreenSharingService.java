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

package org.wso2.iot.agent.services.screenshare;

import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.WindowManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.iot.agent.transport.websocket.WebSocketSessionHandler;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Class for service which used to share the android screen
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ScreenSharingService extends Service {

    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_INTENT = "resultIntent";
    public static final String MAX_WIDTH = "maxWidth";
    public static final String MAX_HEIGHT = "maxHeight";
    static final int VIRT_DISPLAY_FLAGS =
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private MediaProjection projection;
    private VirtualDisplay vdisplay;
    final private HandlerThread handlerThread = new HandlerThread(getClass().getSimpleName(),
            android.os.Process.THREAD_PRIORITY_BACKGROUND);
    private Handler handler;
    private MediaProjectionManager mgr;
    private WindowManager wmgr;
    private ScreenImageReader imageTransmogrifier;
    private int maxWidth = 0;
    private int maxHeight = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        wmgr = (WindowManager) getSystemService(WINDOW_SERVICE);

        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {

        projection =
                mgr.getMediaProjection(i.getIntExtra(EXTRA_RESULT_CODE, -1),
                        (Intent) i.getParcelableExtra(EXTRA_RESULT_INTENT));

        this.maxWidth = i.getIntExtra(MAX_WIDTH, 0);
        this.maxHeight = i.getIntExtra(MAX_HEIGHT, 0);
        imageTransmogrifier = new ScreenImageReader(this, maxWidth, maxHeight);

        MediaProjection.Callback cb = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                vdisplay.release();
            }
        };

        vdisplay = projection.createVirtualDisplay("wso2agent",
                imageTransmogrifier.getWidth(), imageTransmogrifier.getHeight(),
                getResources().getDisplayMetrics().densityDpi,
                VIRT_DISPLAY_FLAGS, imageTransmogrifier.getSurface(), null, handler);
        projection.registerCallback(cb, handler);

        return (START_NOT_STICKY);
    }

    @Override
    public void onDestroy() {
        projection.stop();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        ScreenImageReader newIt = new ScreenImageReader(this, maxWidth, maxHeight);

        if (newIt.getWidth() != imageTransmogrifier.getWidth() ||
                newIt.getHeight() != imageTransmogrifier.getHeight()) {
            ScreenImageReader oldIt = imageTransmogrifier;

            imageTransmogrifier = newIt;
            vdisplay.resize(imageTransmogrifier.getWidth(), imageTransmogrifier.getHeight(),
                    getResources().getDisplayMetrics().densityDpi);
            vdisplay.setSurface(imageTransmogrifier.getSurface());

            oldIt.close();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    WindowManager getWindowManager() {
        return (wmgr);
    }

    Handler getHandler() {
        return (handler);
    }

    void updateImage(byte[] bytes) {

        WebSocketSessionHandler.getInstance(getBaseContext()).sendMessage(bytes);
    }

    void updateImageSize(int width, int height) {

        JSONObject jsonObject = new JSONObject();

        try {
            jsonObject.put("width", width);
            jsonObject.put("height", height);
        } catch (JSONException e) {
            Log.e("Send", e.getMessage());
        }
        WebSocketSessionHandler.getInstance(getBaseContext()).sendMessage(jsonObject.toString());
    }
}

