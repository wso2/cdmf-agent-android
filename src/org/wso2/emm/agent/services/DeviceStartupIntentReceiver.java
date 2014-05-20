package org.wso2.emm.agent.services;

import org.wso2.emm.agent.R;
import org.wso2.emm.agent.utils.CommonUtilities;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

public class DeviceStartupIntentReceiver extends BroadcastReceiver{

    @Override
    public void onReceive(final Context context, Intent intent1) {
    	setRecurringAlarm(context);
    }
    
    private void setRecurringAlarm(Context context) {
    	String mode=CommonUtilities.getPref(context, context.getResources().getString(R.string.shared_pref_message_mode));
    	SharedPreferences mainPref = context.getSharedPreferences(
    	                                         		         context.getResources().getString(R.string.shared_pref_package), Context.MODE_PRIVATE);
    	long interval=mainPref.getLong(context.getResources().getString(R.string.shared_pref_interval), 1);
		if(mode.trim().toUpperCase().equals("LOCAL")){
		    long firstTime = SystemClock.elapsedRealtime();
		    firstTime += 3 * 1000;
		    
		    Intent downloader = new Intent(context, AlarmReceiver.class);
		    PendingIntent recurringDownload = PendingIntent.getBroadcast(context,
		            0, downloader, PendingIntent.FLAG_CANCEL_CURRENT);
		    AlarmManager alarms = (AlarmManager) context.getSystemService(
		            Context.ALARM_SERVICE);
		    alarms.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, firstTime,
		                        interval*20000, recurringDownload);
    	}
	}
}