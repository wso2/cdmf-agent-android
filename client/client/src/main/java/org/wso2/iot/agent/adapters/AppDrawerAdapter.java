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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.wso2.iot.agent.R;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

public class AppDrawerAdapter extends BaseAdapter {

    private Context context;
    private LayoutInflater inflater;
    private String[] appList;

    public AppDrawerAdapter(Context context) {
        this.context = context;
        inflater = (LayoutInflater) context.
                getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return appList.length;
    }

    @Override
    public Object getItem(int position) {
        return appList[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder = new Holder();
        View view = inflater.inflate(R.layout.kiosk_app_drawer, null);
        holder.appName = (TextView) view.findViewById(R.id.name);
        holder.appIcon = (ImageView) view.findViewById(R.id.icon);
        PackageManager pm = context.getPackageManager();

        String packageName = appList[position];
        ApplicationInfo applicationInfo = null;
        try {
            applicationInfo = pm.getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        holder.appName.setText(pm.getApplicationLabel(applicationInfo));
        try {
            holder.appIcon.setImageDrawable(pm.getApplicationIcon(packageName));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return view;
    }

    private class Holder {
        TextView appName;
        ImageView appIcon;
    }

    public void setAppList() {
        String appList = Preference.getString(context, Constants.KIOSK_APP_PACKAGE_NAME);
        this.appList = appList.split("_");
    }
}
