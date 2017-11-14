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
package org.wso2.iot.agent.adapters;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.wso2.iot.agent.R;
import org.wso2.iot.agent.activities.RegistrationActivity;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

import java.util.ArrayList;
import java.util.Arrays;

public class AppDrawerAdapter extends BaseAdapter {

    private String TAG = AppDrawerAdapter.class.getSimpleName();
    private static final String ACTION_INSTALL_COMPLETE = "INSTALL_COMPLETED";
    private static final String APP_STATE_DOWNLOAD_STARTED = "DOWNLOAD_STARTED";
    private static final String APP_STATE_DOWNLOAD_COMPLETED = "DOWNLOAD_COMPLETED";
    private static final String APP_STATE_DOWNLOAD_FAILED = "DOWNLOAD_FAILED";
    private static final String APP_STATE_INSTALL_FAILED = "INSTALL_FAILED";
    private static final String APP_STATE_INSTALLED = "INSTALLED";
    private static final String NEW_APP = "New App";

    private Context context;
    private LayoutInflater inflater;
    private ArrayList<String> appList;

    public AppDrawerAdapter(Context context) {
        this.context = context;
        inflater = (LayoutInflater) context.
                getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return appList != null ? appList.size() : 0;
    }

    @Override
    public Object getItem(int position) {
        return appList != null ? appList.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (appList != null) {
            View view;
            if (convertView == null) {
                // inflating a new view only when necessary
                view = inflater.inflate(R.layout.kiosk_app_drawer, null);
            } else {
                view = convertView;
            }

            Holder holder = new Holder();
            holder.appName = (TextView) view.findViewById(R.id.name);
            holder.appIcon = (ImageView) view.findViewById(R.id.icon);
            PackageManager pm = context.getPackageManager();

            String packageName = appList.get(position);
            if (packageName.equals(NEW_APP)) {
                holder.appName.setText(NEW_APP);
                holder.appIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_download));
            } else {
                ApplicationInfo applicationInfo = null;
                try {
                    applicationInfo = pm.getApplicationInfo(packageName, 0);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(TAG,"Package name not found", e);
                }
                holder.appName.setText(pm.getApplicationLabel(applicationInfo).toString());
                try {
                    holder.appIcon.setImageDrawable(pm.getApplicationIcon(packageName));
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(TAG,"Package name not found", e);
                }
            }
            return view;
        } else {
            return null;
        }
    }

    private class Holder {
        TextView appName;
        ImageView appIcon;
    }

    public void setAppList() {
        String appList = Preference.getString(context, Constants.KIOSK_APP_PACKAGE_NAME);
        String temp = Preference.getString(context, context.getResources().getString(R.string.app_install_status));
        if (temp != null && temp != "") {
            switch (temp) {
                case APP_STATE_DOWNLOAD_STARTED:
                case APP_STATE_DOWNLOAD_COMPLETED:
                case APP_STATE_INSTALLED:
                    if (appList != null && appList != "") {
                        appList += "_" + NEW_APP;
                    } else {
                        appList = NEW_APP;
                    }
                    break;
            }
        }
        String[] appArr = appList.split(context.getString(R.string.kiosk_application_package_split_regex));
        this.appList = new ArrayList<>(Arrays.asList(appArr));
    }
}
