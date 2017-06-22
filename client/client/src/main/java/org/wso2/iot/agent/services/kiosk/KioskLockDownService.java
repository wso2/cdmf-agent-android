/*
 * Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.iot.agent.services.kiosk;

import android.app.IntentService;
import android.content.Intent;
import org.wso2.iot.agent.activities.KioskRestrictionActivity;

/**
 * This class connects the LockActivity to the intent that is get passed in.
 */
public class KioskLockDownService extends IntentService {

    public KioskLockDownService() {
        super(KioskLockDownService.class.getName());
    }

    @Override
    protected void onHandleIntent(Intent lockIntent) {
        lockIntent = new Intent(this, KioskRestrictionActivity.class);
        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(lockIntent);
    }
}
