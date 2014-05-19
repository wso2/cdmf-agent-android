package org.wso2.emm.agent.utils;

import org.wso2.mobile.idp.proxy.APIController;
import org.wso2.mobile.idp.proxy.APIResultCallBack;
import org.wso2.mobile.idp.proxy.APIUtilities;

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
	public static void callSecuredAPI(String endpoint, String methodType, APIResultCallBack apiResultCallBack, int requestCode) {
		APIUtilities apiUtilities = new APIUtilities();
		Log.e("CommonUtilities.SERVER_URL + endpoint + CommonUtilities.API_VERSION",CommonUtilities.SERVER_URL + endpoint + CommonUtilities.API_VERSION);
		apiUtilities.setEndPoint(CommonUtilities.SERVER_URL + endpoint + CommonUtilities.API_VERSION);
		apiUtilities.setHttpMethod(methodType);
		APIController apiController = new APIController();
		apiController.invokeAPI(apiUtilities, apiResultCallBack, requestCode);
	}
	

}
