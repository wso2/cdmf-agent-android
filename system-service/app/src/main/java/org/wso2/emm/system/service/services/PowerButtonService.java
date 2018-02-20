package org.wso2.emm.system.service.services;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import org.wso2.emm.system.service.R;

/**
 * Created by madhawa on 2/20/18.
 */

public class PowerButtonService extends Service {

    public PowerButtonService() {

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        LinearLayout mLinear = new LinearLayout(getApplicationContext()) {

            //home or recent button
            public void onCloseSystemDialogs(String reason) {
                if ("globalactions".equals(reason)) {
                    Log.e("Key", "---------------------------------Long press on power button");
                } else if ("homekey".equals(reason)) {
                    //home key pressed
                } else if ("recentapps".equals(reason)) {
                    // recent apps button clicked
                }
            }

            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                        || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP
                        || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN
                        || event.getKeyCode() == KeyEvent.KEYCODE_CAMERA
                        || event.getKeyCode() == KeyEvent.KEYCODE_POWER) {
                    Log.e("Key", "---------------keycode " + event.getKeyCode());
                }
                return super.dispatchKeyEvent(event);
            }
        };

        mLinear.setFocusable(true);

        View mView = LayoutInflater.from(this).inflate(R.layout.service_layout, mLinear);
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        //params
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(100,
                100, WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        wm.addView(mView, params);

        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
