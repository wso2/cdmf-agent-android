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

package org.wso2.iot.system.service.api;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import org.wso2.iot.system.service.utils.Constants;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Class @{@link InputEventHandler} for manage input events
 */
public class InputEventHandler {
    private static final String TAG = InputEventHandler.class.getSimpleName();

    private static int INJECT_INPUT_EVENT_MODE_ASYNC;
    private static int INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT;
    private static int INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH;
    private Object mInputManager;
    private PowerManager powerManager;
    private Method mInjectEventMethod;
    private static InputEventHandler injectionManager;
    private static Object instance_lock = new Object();
    private static long lastInjectTimestamp = 0;
    private static int lastEvent = MotionEvent.ACTION_CANCEL;
    private double inputInjectRatePerSecond = (double) Constants.INPUT_THRESHOLD / 1000;
    private int inputAllowance = Constants.INPUT_THRESHOLD;

    private InputEventHandler(Context c) {
        mInputManager = c.getSystemService(Context.INPUT_SERVICE);
        powerManager = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        try {
            //Obtain hidden methods
            mInjectEventMethod = mInputManager.getClass().getDeclaredMethod("injectInputEvent", InputEvent.class, Integer.TYPE);
            mInjectEventMethod.setAccessible(true);
            Field eventAsync = mInputManager.getClass().getDeclaredField("INJECT_INPUT_EVENT_MODE_ASYNC");
            Field eventResult = mInputManager.getClass().getDeclaredField("INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT");
            Field eventFinish = mInputManager.getClass().getDeclaredField("INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH");
            eventAsync.setAccessible(true);
            eventResult.setAccessible(true);
            eventFinish.setAccessible(true);
            INJECT_INPUT_EVENT_MODE_ASYNC = eventAsync.getInt(mInputManager.getClass());
            INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT = eventResult.getInt(mInputManager.getClass());
            INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = eventFinish.getInt(mInputManager.getClass());
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Error occurred while using hidden method" + e);
        } catch (NoSuchFieldException e) {
            Log.e(TAG, "Error occurred while using hidden field" + e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Error occurred while accessing hidden field" + e);
        }

    }

    /**
     * return a singleton Instance
     *
     * @param context is the android context object.
     * @return InputEventHandler.
     */
    public static InputEventHandler getInstance(Context context) throws Exception {
        if (injectionManager == null) {
            synchronized (instance_lock) {
                if (injectionManager == null) {
                    injectionManager = new InputEventHandler(context);
                }
            }
        }
        return injectionManager;
    }

    /**
     * Method to inject touch event
     *
     * @param x        value for x coordinate
     * @param y        value for y coordinate
     * @param action   motion event
     * @param duration event duration
     */
    public synchronized void injectTouchEvent(float x, float y, int action, int duration) {

        /*
        input events should be injected in order as
        down,(move)*,up
        down,up
        any cancel
        otherwise neglect
         */
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                if (lastEvent == MotionEvent.ACTION_UP) {
                    Log.w(TAG, "Input manager does not support action: " + action + ":" + lastEvent);
                    return;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (lastEvent == MotionEvent.ACTION_UP) {
                    Log.w(TAG, "Input manager does not support action: " + action + ":" + lastEvent);
                    return;
                }
                break;
            case MotionEvent.ACTION_DOWN:
                if (lastEvent == MotionEvent.ACTION_DOWN || lastEvent == MotionEvent.ACTION_MOVE) {
                    Log.w(TAG, "Input manager does not support action: " + action + ":" + lastEvent);
                    return;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (lastEvent == MotionEvent.ACTION_CANCEL) {
                    Log.w(TAG, "Input manager does not support action: " + action + ":" + lastEvent);
                    return;
                }
                break;
            default:
                Log.w(TAG, "Input manager does not support action: " + action);
                break;
        }
        // check spam of events
        long currentTime = SystemClock.uptimeMillis();
        inputAllowance += (currentTime - lastInjectTimestamp) * inputInjectRatePerSecond;
        if (inputAllowance > Constants.INPUT_THRESHOLD) {
            inputAllowance = Constants.INPUT_THRESHOLD;
        }

        if (inputAllowance >= 1) {
            lastInjectTimestamp = currentTime;
            inputAllowance -= 1;
        } else {
            //  if (Constants.DEBUG_MODE_ENABLED) {
            Log.w(TAG, "Neglect action : " + action + " due to spam of input injections");
            return;
            // }
        }

        if (!powerManager.isScreenOn()) {
            PowerManager.WakeLock TempWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK |
                    PowerManager.ACQUIRE_CAUSES_WAKEUP, "ScreenLock");
            TempWakeLock.acquire();
            TempWakeLock.release();
        }
        if (duration <= 0) {
            duration = Constants.MIN_DURATION;
        }
        MotionEvent motionEvent = MotionEvent.obtain(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis() + duration,
                action,
                x,
                y,
                0
        );
        motionEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        injectEvent(action, motionEvent, INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT);
        motionEvent.recycle();

    }

    /**
     * Method for inject event to touch screen
     *
     * @param ie   input event
     * @param mode mode
     */
    private void injectEvent(int action, InputEvent ie, int mode) {
        try {
            mInjectEventMethod.invoke(mInputManager, ie, mode);
            lastEvent = action;
        } catch (Exception e) {
            Log.e(TAG, "Error occurred while injecting input: " + e.getLocalizedMessage());
        }
    }
}
