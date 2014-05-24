package org.wso2.emm.agent.proxy;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import android.os.AsyncTask;

public class APIController {
	private static String TAG = "APIController";
	public  void invokeAPI(APIUtilities apiUtilities, APIResultCallBack apiResultCallBack,int requestCode){
		IdentityProxy.getInstance().setRequestCode(requestCode);
		new NetworkCallTask(apiResultCallBack).execute(apiUtilities);
	}
	 private class NetworkCallTask extends AsyncTask<APIUtilities, Void, Map<String,String>> {
		 	APIResultCallBack apiResultCallBack;
	        public NetworkCallTask(APIResultCallBack apiResultCallBack) {
	        	this.apiResultCallBack = apiResultCallBack;
	        }

	        @Override
	        protected Map<String,String> doInBackground(APIUtilities... params) {
	            APIUtilities apiUtilities = params[0];
	            Token token;
                try {
	                token = IdentityProxy.getInstance().getToken();
		    		String accessToken = token.getAccessToken();
		    		Map<String, String> headers = new HashMap<String, String>();
		    		headers.put("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
		    		headers.put("Accept","*/*");
		    		headers.put("User-Agent","Mozilla/5.0 ( compatible ), Android");
		    		headers.put("Authorization","Bearer "+accessToken);
		            Map<String, String> response_params = ServerUtilitiesTemp.postData( apiUtilities,headers);
		            return response_params;
                } catch (InterruptedException e) {
	                // TODO Auto-generated catch block
	                e.printStackTrace();
                } catch (ExecutionException e) {
	                // TODO Auto-generated catch block
	                e.printStackTrace();
                } catch (TimeoutException e) {
	                // TODO Auto-generated catch block
	                e.printStackTrace();
                } catch (Exception e) {
	                // TODO Auto-generated catch block
	                e.printStackTrace();
                }
                return null;
	        }
	        @Override
	        protected void onPostExecute(Map<String,String> result) {
	        	apiResultCallBack.onReceiveAPIResult(result,IdentityProxy.getInstance().getRequestCode());
	        }
	    }
}
