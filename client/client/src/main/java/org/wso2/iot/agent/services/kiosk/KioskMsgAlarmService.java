package org.wso2.iot.agent.services.kiosk;

import android.app.IntentService;
import android.content.Intent;
import org.wso2.iot.agent.KioskActivity;
/*
    IntentService used to update kiosk ui for alarm and message operations
 */
public class KioskMsgAlarmService extends IntentService {

    public static final String ACTIVITY_TYPE = "type";
    public static final String ACTIVITY_MSG = "msg";
    public static final String ACTIVITY_TITLE = "title";

    public KioskMsgAlarmService(String s) {
        super("KioskMsgAlarmService");
    }
    public KioskMsgAlarmService() {
        super("KioskMsgAlarmService");
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        String msg = intent.getStringExtra(ACTIVITY_MSG);
        String type = intent.getStringExtra(ACTIVITY_TYPE);
        String title = intent.getStringExtra(ACTIVITY_TITLE);
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(KioskActivity.ResponseReceiver.ACTION_RESP);
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
        broadcastIntent.putExtra(ACTIVITY_MSG, msg);
        broadcastIntent.putExtra(ACTIVITY_TYPE, type);
        broadcastIntent.putExtra(ACTIVITY_TITLE, title);
        sendBroadcast(broadcastIntent);
    }
}
