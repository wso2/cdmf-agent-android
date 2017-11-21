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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.IMediaProjection;
import android.media.projection.IMediaProjectionManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.WindowManager;

import org.wso2.iot.agent.transport.websocket.WebSocketSessionHandler;
import org.wso2.iot.agent.utils.Constants;

/**
 * Class for service which used to share the android screen
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ScreenSharingService extends Service {

    private static final String TAG = ScreenSharingService.class.getSimpleName();
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_INTENT = "resultIntent";
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
    private ScreenImageReader screenImageReader;
    private int maxWidth = 0;
    private int maxHeight = 0;
    private IMediaProjectionManager mService;
    public static boolean isScreenShared = false;
    private int screenAllowance = Constants.SCREEN_SHARING_RATE_IMAGES;
    private double screenSharingRate = (double) Constants.SCREEN_SHARING_RATE_IMAGES / Constants.SCREEN_SHARING_RATE_MILLISECONDS;
    private static long screenLastCheck = SystemClock.uptimeMillis();
    private static long sizeLastCheck = SystemClock.uptimeMillis();
    private byte[] lastImage = new byte[0];

    @Override
    public void onCreate() {
        super.onCreate();
        IBinder b = ServiceManager.getService(MEDIA_PROJECTION_SERVICE);
        mService = IMediaProjectionManager.Stub.asInterface(b);
        mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        wmgr = (WindowManager) getSystemService(WINDOW_SERVICE);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {

        try {
            if (Constants.SYSTEM_APP_ENABLED) {
                PackageManager packageManager = getBaseContext().getPackageManager();
                ApplicationInfo aInfo;
                IMediaProjection iMediaProjection;
                aInfo = packageManager.getApplicationInfo(getBaseContext().getBasePackageName(), 0);
                iMediaProjection = mService.createProjection(aInfo.uid, getBaseContext().getBasePackageName(),
                        MediaProjectionManager.TYPE_SCREEN_CAPTURE, true);
                Intent intent = new Intent().putExtra(MediaProjectionManager.EXTRA_MEDIA_PROJECTION,
                        iMediaProjection.asBinder());
                projection =
                        mgr.getMediaProjection(i.getIntExtra(EXTRA_RESULT_CODE, -1), intent);
            } else {
                projection =
                        mgr.getMediaProjection(i.getIntExtra(EXTRA_RESULT_CODE, -1),
                                (Intent) i.getParcelableExtra(EXTRA_RESULT_INTENT));
            }
            this.maxWidth = i.getIntExtra(Constants.MAX_WIDTH, Constants.DEFAULT_SCREEN_CAPTURE_IMAGE_WIDTH);
            this.maxHeight = i.getIntExtra(Constants.MAX_HEIGHT, Constants.DEFAULT_SCREEN_CAPTURE_IMAGE_HEIGHT);
            screenImageReader = new ScreenImageReader(this, maxWidth, maxHeight);

            MediaProjection.Callback cb = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    vdisplay.release();
                }
            };

            vdisplay = projection.createVirtualDisplay("wso2agent",
                    screenImageReader.getWidth(), screenImageReader.getHeight(),
                    getResources().getDisplayMetrics().densityDpi,
                    VIRT_DISPLAY_FLAGS, screenImageReader.getSurface(), null, handler);
            projection.registerCallback(cb, handler);
            isScreenShared = true;
        } catch (RemoteException | PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error occurred while starting screen capture" + e.getMessage());

        }
        return (START_NOT_STICKY);
    }

    @Override
    public void onDestroy() {
        isScreenShared = false;
        projection.stop();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        ScreenImageReader newIt = new ScreenImageReader(this, maxWidth, maxHeight);

        if (newIt.getWidth() != screenImageReader.getWidth() ||
                newIt.getHeight() != screenImageReader.getHeight()) {
            ScreenImageReader oldIt = screenImageReader;
            screenImageReader = newIt;
            vdisplay.resize(screenImageReader.getWidth(), screenImageReader.getHeight(),
                    getResources().getDisplayMetrics().densityDpi);
            vdisplay.setSurface(screenImageReader.getSurface());
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

    void updateImage(byte[] image, int sizeDiff) {

        long currentTime = SystemClock.uptimeMillis();
        screenAllowance += (currentTime - screenLastCheck) * screenSharingRate;
        if (screenAllowance > Constants.SCREEN_SHARING_RATE_IMAGES) {
            screenAllowance = Constants.SCREEN_SHARING_RATE_IMAGES;
        }
        if (screenAllowance >= 1 && lastImage.length != image.length) {
            screenLastCheck = currentTime;
            lastImage = image;
            WebSocketSessionHandler.getInstance(getBaseContext()).sendMessage(image);
            if (sizeDiff > 1 && (currentTime - sizeLastCheck) > Constants.SCREEN_SHARING_RATE_MILLISECONDS) {
                sizeLastCheck = currentTime;
                screenAllowance -= sizeDiff;
                if (screenAllowance < 0) {
                    screenAllowance = 0;
                }
            } else {
                screenAllowance -= 1;
            }
        } else {
            if (Constants.DEBUG_MODE_ENABLED) {
                Log.d(TAG, "Screens shared per second exceeded.");
            }
        }

    }
}

