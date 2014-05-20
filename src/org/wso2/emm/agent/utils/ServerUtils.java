package org.wso2.emm.agent.utils;

import java.util.Map;

import org.wso2.emm.agent.R;
import org.wso2.emm.agent.services.WSO2DeviceAdminReceiver;
import org.wso2.mobile.idp.proxy.APIController;
import org.wso2.mobile.idp.proxy.APIResultCallBack;
import org.wso2.mobile.idp.proxy.APIUtilities;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

public class ServerUtils {

	/**
	 * calls the secured API
	 * 
	 * @param serverUrl the server url
	 * @param endpoint the API endpoint
	 * @param apiVersion the API version
	 * @param methodType the method type
	 * @param apiResultCallBack the API result call back object
	 * @param requestCode the request code
	 */
	public static void callSecuredAPI(String endpoint, String methodType, Map<String, String> requestParams, APIResultCallBack apiResultCallBack, int requestCode) {
		
		APIUtilities apiUtilities = new APIUtilities();
		Log.e("CommonUtilities.SERVER_URL + endpoint + CommonUtilities.API_VERSION",CommonUtilities.SERVER_URL + endpoint + CommonUtilities.API_VERSION);
		apiUtilities.setEndPoint(CommonUtilities.SERVER_URL + endpoint + CommonUtilities.API_VERSION);
		apiUtilities.setHttpMethod(methodType);
		if (requestParams != null) {
			apiUtilities.setRequestParams(requestParams);
		}
		APIController apiController = new APIController();
		apiController.invokeAPI(apiUtilities, apiResultCallBack, requestCode);
	}
	
	public static void clearAppData(Context context){
		DevicePolicyManager devicePolicyManager;
		ComponentName demoDeviceAdmin;
		try {
			devicePolicyManager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
			demoDeviceAdmin = new ComponentName(context,
					WSO2DeviceAdminReceiver.class);
			SharedPreferences mainPref = context
					.getSharedPreferences(
							context.getResources().getString(
									R.string.shared_pref_package),
							Context.MODE_PRIVATE);
			Editor editor = mainPref.edit();
			editor.putString(
					context.getResources().getString(
							R.string.shared_pref_policy), "");
			editor.putString(
					context.getResources().getString(
							R.string.shared_pref_isagreed), "0");
			editor.putString(
					context.getResources().getString(R.string.shared_pref_regId), "");
			editor.putString(
					context.getResources().getString(
							R.string.shared_pref_registered), "0");
			editor.putString(
					context.getResources().getString(
							R.string.shared_pref_ip), "");
			editor.putString(
					context.getResources().getString(
							R.string.shared_pref_sender_id), "");
			editor.putString(
					context.getResources().getString(
							R.string.shared_pref_eula), "");						
			editor.commit();
			devicePolicyManager.removeActiveAdmin(demoDeviceAdmin);
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	

}
