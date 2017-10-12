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

import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import org.wso2.iot.agent.utils.Constants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.app.ActivityThread.TAG;

/**
 * Class for listen to screen images and transform and send via session
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ScreenImageReader implements ImageReader.OnImageAvailableListener {
    private final int width;
    private final int height;
    private final ImageReader imageReader;
    private final ScreenSharingService svc;
    private Bitmap latestBitmap = null;

    ScreenImageReader(ScreenSharingService svc, int maxWidth, int maxHeight) {
        this.svc = svc;
        Display display = svc.getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;
        int height = size.y;
        while (width * height > (2 << 19)) {
            width = width >> 1;
            height = height >> 1;
        }
        // Calculate width and height based on requested given dimensions
        double diffWidth = 0, diffHeight = 0;
        if (width > 0) {
            diffWidth = (double) (width - maxWidth) / width;
        }
        if (height > 0) {
            diffHeight = (double) (height - maxHeight) / height;
        }
        if (diffWidth > 0 && diffWidth > diffHeight) {
            width = (int) (width * (1 - diffWidth));
            height = (int) (height * (1 - diffWidth));
        } else if (diffHeight > 0 && diffHeight > diffWidth) {
            width = (int) (width * (1 - diffHeight));
            height = (int) (height * (1 - diffHeight));
        }
        this.width = width;
        this.height = height;
        imageReader = ImageReader.newInstance(width, height,
                PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(this, svc.getHandler());
    }

    @Override
    public void onImageAvailable(ImageReader reader) {

        final Image image = imageReader.acquireLatestImage();
        if (image != null) {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            int bitmapWidth = width + rowPadding / pixelStride;
            if (latestBitmap == null || latestBitmap.getWidth() != bitmapWidth || latestBitmap.getHeight() != height) {
                if (latestBitmap != null) {
                    latestBitmap.recycle();
                }
                latestBitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888);
            }
            latestBitmap.copyPixelsFromBuffer(buffer);
            image.close();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            Bitmap cropped = Bitmap.createBitmap(latestBitmap, 0, 0, width, height);
            int quality = Constants.MAX_QUALITY, diff = 0;
            cropped.compress(Bitmap.CompressFormat.JPEG, Constants.MAX_QUALITY, baos);

            byte[] newImage = baos.toByteArray();
            if (Constants.MAX_IMAGE_SIZE_BYTES < newImage.length) {
                int old = newImage.length;
                diff = (int) Math.round((double) (newImage.length) / Constants.MAX_IMAGE_SIZE_BYTES);
                if (diff >= 2) {
                    if (diff > Constants.MAX_QUALITY) {
                        diff = Constants.MAX_QUALITY;
                    }
                    baos.reset();
                    quality = Constants.MAX_QUALITY / diff;
                    cropped.compress(Bitmap.CompressFormat.JPEG, Constants.MAX_QUALITY / diff, baos);
                    newImage = baos.toByteArray();
                }
                Log.e(TAG, "dif" + diff + " Length:" + old + ": new: " + newImage.length + ": Quality: " + quality);
            }

            svc.updateImage(newImage, diff);

            try {
                baos.close();
            } catch (IOException e) {
                Log.w(TAG, "Cannot close the  temporary image byte stream");
            }

        }
    }

    Surface getSurface() {
        return (imageReader.getSurface());
    }

    int getWidth() {
        return (width);
    }

    int getHeight() {
        return (height);
    }

    void close() {
        imageReader.close();
    }
}
