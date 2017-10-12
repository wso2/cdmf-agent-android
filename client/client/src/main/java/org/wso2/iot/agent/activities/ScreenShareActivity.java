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
package org.wso2.iot.agent.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;

import org.wso2.iot.agent.services.screenshare.ScreenSharingService;
import org.wso2.iot.agent.utils.Constants;

/**
 * Activity which is used to show alerts throughout the application.
 */
public class ScreenShareActivity extends Activity {

    private Context context;
    private Resources resources;
    private static final String TAG = ScreenShareActivity.class.getSimpleName();
    private MediaProjectionManager mgr;
    private static final int REQUEST_SCREENSHOT = 59706;
    private int maxWidth = 0;
    private int maxHeight = 0;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = ScreenShareActivity.this.getApplicationContext();
        this.resources = context.getResources();
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            this.maxWidth = extras.getInt(Constants.MAX_WIDTH, Constants.DEFAULT_SCREEN_CAPTURE_IMAGE_WIDTH);
            this.maxHeight = extras.getInt(Constants.MAX_HEIGHT, Constants.DEFAULT_SCREEN_CAPTURE_IMAGE_HEIGHT);
        }
        mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mgr.createScreenCaptureIntent(), REQUEST_SCREENSHOT);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == REQUEST_SCREENSHOT) {
            if (resultCode == RESULT_OK) {
                Intent i =
                        new Intent(this, ScreenSharingService.class)
                                .putExtra(ScreenSharingService.EXTRA_RESULT_CODE, resultCode)
                                .putExtra(ScreenSharingService.EXTRA_RESULT_INTENT, data)
                                .putExtra(Constants.MAX_WIDTH, maxWidth)
                                .putExtra(Constants.MAX_HEIGHT, maxHeight);
                startService(i);
            }
        }
        ScreenShareActivity.this.finish();
    }

}
