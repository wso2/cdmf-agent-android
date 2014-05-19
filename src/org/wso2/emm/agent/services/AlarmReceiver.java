package org.wso2.emm.agent.services;

import java.util.HashMap;
import java.util.Map;

import org.wso2.emm.agent.AuthenticationActivity;
import org.wso2.emm.agent.R;
import org.wso2.emm.agent.utils.CommonUtilities;
import org.wso2.emm.agent.utils.ServerUtilities;
import org.wso2.mobile.idp.proxy.APIController;
import org.wso2.mobile.idp.proxy.APIResultCallBack;
import org.wso2.mobile.idp.proxy.APIUtilities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

public class AlarmReceiver extends BroadcastReceiver implements APIResultCallBack {
	 
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
			getOperations();
		}
    }
     
    public String getOperations(){
		String server_res = null;
		try {
			SharedPreferences mainPref = context
					.getSharedPreferences(context.getResources().getString(R.string.shared_pref_package),
							Context.MODE_PRIVATE);
			String regId=mainPref.getString(context.getResources().getString(R.string.shared_pref_regId), "");
			Map<String, String> params = new HashMap<String, String>();
			params.put("regId", regId);
			Log.e("regId",regId+"");
			
			APIUtilities apiUtilities = new APIUtilities();
			apiUtilities.setEndPoint(CommonUtilities.SERVER_URL
					+ CommonUtilities.NOTIFICATION_ENDPOINT	
					+ CommonUtilities.API_VERSION);

			apiUtilities.setHttpMethod("POST");
			Log.e("endpoint", apiUtilities.getEndPoint());
			APIController apiController = new APIController();
			apiController.invokeAPI(apiUtilities, this,
					CommonUtilities.NOTIFICATION_REQUEST_CODE);
//			server_res = "[{\"feature_code\": \"500P\",\"data\": ["+
//			                 " {\"mesage_id\": \"2\", \"data\": {\"feature_code\": \"506A\",\"data\": {\"notification\":\"sdfsdf\"}}}"+
//			             " ]},{\"feature_code\": \"513A\",\"data\": [{\"mesage_id\": \"2\",\"data\": {\"function\": \"disabled\"}}]}]";
			//Log.e("server_res",server_res);
			//server_res=ServerUtilities.readJson(context);
			return server_res;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public void onReceiveAPIResult(Map<String, String> result, int requestCode) {
		String responseStatus = "";
		String response = "";
		if (requestCode == CommonUtilities.NOTIFICATION_REQUEST_CODE) { 
			if (result != null) {
				responseStatus = result.get(CommonUtilities.STATUS_KEY);
				
				if (responseStatus.equals(CommonUtilities.REQUEST_SUCCESSFUL)) {
					response = result.get("response");
					processMsg = new ProcessMessage(context, CommonUtilities.MESSAGE_MODE_LOCAL, response);
				}
			}
		}
		
		
		
	}
 
}
