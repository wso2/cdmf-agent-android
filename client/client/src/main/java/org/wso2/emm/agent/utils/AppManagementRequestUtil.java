/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.emm.agent.utils;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.wso2.emm.agent.beans.AppInstallRequest;
import org.wso2.emm.agent.beans.AppUninstallRequest;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to hold App installation queue handling methods.
 */
public class AppManagementRequestUtil {

    private static final Object INSTALL_LOCK = new Object();
    private static final Object UNINSTALL_LOCK = new Object();

    private static Type appInstallListType = new TypeToken<ArrayList<AppInstallRequest>>() {
    }.getType();
    private static Type appUninstallListType = new TypeToken<ArrayList<AppUninstallRequest>>() {
    }.getType();
    private static Gson appInstallationsGson = new Gson();
    private static Gson appUninstallationsGson = new Gson();

    private AppManagementRequestUtil() {
    }

    private static List<AppInstallRequest> getPendingInstallations(Context context) {
        String pendingAppInstallationsString = Preference.getString(context,
                Constants.PENDING_APP_INSTALLATIONS);
        if (pendingAppInstallationsString == null) {
            pendingAppInstallationsString = "[]";
        }
        return appInstallationsGson.fromJson(pendingAppInstallationsString, appInstallListType);
    }

    private static List<AppUninstallRequest> getPendingUninstallations(Context context) {
        String pendingAppUninstallationsString = Preference.getString(context,
                Constants.PENDING_APP_UNINSTALLATIONS);
        if (pendingAppUninstallationsString == null) {
            pendingAppUninstallationsString = "[]";
        }
        return appInstallationsGson.fromJson(pendingAppUninstallationsString, appUninstallListType);
    }

    public static void addPendingInstall(Context context, AppInstallRequest newRequest) {
        synchronized (INSTALL_LOCK) {
            List<AppInstallRequest> installRequests = getPendingInstallations(context);
            List<AppInstallRequest> updatedInstallRequests = new ArrayList<>();
            boolean isAlreadyExists = false;
            for (AppInstallRequest request : installRequests) {
                if (request.getApplicationOperationId() == newRequest.getApplicationOperationId()
                        && request.getAppUrl() != null
                        && request.getAppUrl().equals(newRequest.getAppUrl())) {
                    request = newRequest;
                    isAlreadyExists = true;
                }
                updatedInstallRequests.add(request);
            }
            if (!isAlreadyExists) {
                updatedInstallRequests.add(newRequest);
            }
            Preference.putString(context, Constants.PENDING_APP_INSTALLATIONS,
                    appInstallationsGson.toJson(updatedInstallRequests));
        }
    }


    public static AppInstallRequest getPendingInstall(Context context) {
        synchronized (INSTALL_LOCK) {
            List<AppInstallRequest> installRequests = getPendingInstallations(context);
            if (installRequests.isEmpty()) {
                return null;
            }
            AppInstallRequest request = installRequests.get(0);
            installRequests.remove(0);
            Preference.putString(context, Constants.PENDING_APP_INSTALLATIONS,
                    appInstallationsGson.toJson(installRequests));
            return request;
        }
    }

    public static void addPendingUninstall(Context context, AppUninstallRequest newRequest) {
        synchronized (UNINSTALL_LOCK) {
            List<AppUninstallRequest> uninstallRequests = getPendingUninstallations(context);
            List<AppUninstallRequest> updatedUninstallRequests = new ArrayList<>();
            boolean isAlreadyExists = false;
            for (AppUninstallRequest request : uninstallRequests) {
                if (request.getApplicationOperationId() == newRequest.getApplicationOperationId()) {
                    request = newRequest;
                    isAlreadyExists = true;
                }
                updatedUninstallRequests.add(request);
            }
            if (!isAlreadyExists) {
                updatedUninstallRequests.add(newRequest);
            }
            Preference.putString(context, Constants.PENDING_APP_UNINSTALLATIONS,
                    appUninstallationsGson.toJson(updatedUninstallRequests));
        }
    }


    public static AppUninstallRequest getPendingUninstall(Context context) {
        synchronized (UNINSTALL_LOCK) {
            List<AppUninstallRequest> uninstallRequests = getPendingUninstallations(context);
            if (uninstallRequests.isEmpty()) {
                return null;
            }
            AppUninstallRequest request = uninstallRequests.get(0);
            uninstallRequests.remove(0);
            Preference.putString(context, Constants.PENDING_APP_UNINSTALLATIONS,
                    appUninstallationsGson.toJson(uninstallRequests));
            return request;
        }
    }
}
