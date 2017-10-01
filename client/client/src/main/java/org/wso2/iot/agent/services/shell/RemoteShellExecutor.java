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

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Class for manage remote shell tasks
 */
public class RemoteShellExecutor {

    private static RemoteShellExecutor remoteShellExecutor;
    private static final String TAG = RemoteShellExecutor.class.getSimpleName();
    private static Object instance_lock = new Object();
    private RemoteShellTask remoteShellTask;
    private Context context;
    Executor executor = Executors.newSingleThreadExecutor();

    /**
     * Default constructor for the RemoteShellExecutor.
     */
    private RemoteShellExecutor(Context context) {
        this.context = context;
    }

    /**
     * return a singleton Instance
     *
     * @param context is the android context object.
     * @return RemoteShellExecutor.
     */
    public static RemoteShellExecutor getInstance(Context context) {
        if (remoteShellExecutor == null) {
            synchronized (instance_lock) {
                if (remoteShellExecutor == null) {
                    remoteShellExecutor = new RemoteShellExecutor(context);
                }
            }
        }
        return remoteShellExecutor;
    }

    /**
     * Execute remote shell command
     * @param operation remote shell operation
     */
    public void executeCommand(Operation operation) {
        if (remoteShellTask != null && remoteShellTask.isAlive()) {
            remoteShellTask.close();
        }
        remoteShellTask = new RemoteShellTask(context, operation);
        remoteShellTask.start();
    }
}

