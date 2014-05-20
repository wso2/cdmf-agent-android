package org.wso2.emm.agent.utils;

import java.util.Map;

import org.wso2.mobile.idp.proxy.APIController;
import org.wso2.mobile.idp.proxy.APIResultCallBack;
import org.wso2.mobile.idp.proxy.APIUtilities;

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
		apiUtilities.setEndPoint(CommonUtilities.SERVER_URL + endpoint + CommonUtilities.API_VERSION);
		apiUtilities.setHttpMethod(methodType);
		if (requestParams != null) {
			apiUtilities.setRequestParams(requestParams);
		}
		APIController apiController = new APIController();
		apiController.invokeAPI(apiUtilities, apiResultCallBack, requestCode);
	}
	

}
