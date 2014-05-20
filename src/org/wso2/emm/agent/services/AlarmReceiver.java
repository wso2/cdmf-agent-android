package org.wso2.emm.agent.services;

import java.util.HashMap;
import java.util.Map;

import org.wso2.emm.agent.R;
import org.wso2.emm.agent.utils.CommonUtilities;
import org.wso2.emm.agent.utils.ServerUtils;
import org.wso2.mobile.idp.proxy.APIResultCallBack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {
	 
    private static final String DEBUG_TAG = "AlarmReceiver";
    Map<String, String> server_res = null;
    Context context;
    ProcessMessage processMsg = null;
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(DEBUG_TAG, "Recurring alarm; requesting download service.");
        this.context = context;
        String mode=CommonUtilities.getPref(context, context.getResources().getString(R.string.shared_pref_message_mode));
		if(mode.trim().toUpperCase().equals("LOCAL")){
			ProcessMessage msg=new ProcessMessage(context);
			msg.getOperations(null);
		}
    }
 
}
