package org.wso2.emm.agent.proxy;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.emm.agent.utils.CommonUtilities;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Getting new access token and refresh token after access token expiration
 */
public class RefreshTokenHandler extends Activity {
    private Context context;
    private Token tokens = null;
    private static final String TAG = "RefreshTokenHandler";
    private String clientID = null;
    private String clientSecret = null;
    private Token token;

    public RefreshTokenHandler(Context context, String clientID, String clientSecret, Token token) {
        this.context = context;
        this.clientID = clientID;
        this.clientSecret = clientSecret;
        this.token = token;
        
        
        
        Log.e("refreshToken 99999999999",token.getRefreshToken());
    	Log.e("accessToken 9999999999999",token.getAccessToken());
        
        
    }

    public void obtainNewAccessToken() throws InterruptedException, ExecutionException, TimeoutException {
        new NetworkCallTask().execute().get(1000, TimeUnit.MILLISECONDS);
    }

    private class NetworkCallTask extends AsyncTask<String, Void, String> {

        private String responseCode = null;

        public NetworkCallTask() {

        }

		@Override
		protected String doInBackground(String... params) {
			Log.e("shan","doInBackground");
			String response = "";
			
			Map<String, String> request_params = new HashMap<String, String>();
			request_params.put("grant_type", "refresh_token");
			request_params.put("refresh_token", token.getRefreshToken());
			
			
			
			Map<String, String> response_params =
			                                      ServerUtilities.postData(context,
			                                                               IdentityProxy.getInstance()
			                                                                            .getAccessTokenURL(),
			                                                               request_params,
			                                                               CommonUtilities.CLIENT_ID,
			                                                               CommonUtilities.CLIENT_SECRET);
			
			
			Log.e("11",token.getAccessToken());
			
			response = response_params.get("response");
			responseCode = response_params.get("status");
			Log.d(TAG, response);
			return response;
		}

        @Override
        protected void onPostExecute(String result) {
            String refreshToken = null;
            String accessToken = null;
            try {
                JSONObject response = new JSONObject(result);
                IdentityProxy identityProxy = IdentityProxy.getInstance();
                if (responseCode != null && responseCode.equals("200")) {
                    refreshToken = response.getString("refresh_token");
                    accessToken = response.getString("access_token");
                    
                    token.setRefreshToken(refreshToken);
                    token.setAccessToken(accessToken);
                     
                    SharedPreferences mainPref = IdentityProxy.getInstance().getContext().getSharedPreferences("com.mdm",Context.MODE_PRIVATE);
                    Editor editor = mainPref.edit();
                    editor.putString("refresh_token",refreshToken);
                    editor.commit();
                    
                    Log.d(TAG, refreshToken);
                    Log.d(TAG, accessToken);
                    identityProxy.receiveNewAccessToken(responseCode, "success", token);
                    
                } else if (responseCode != null) {
                    JSONObject mainObject = new JSONObject(result);
                    String error = mainObject.getString("error");
                    String errorDescription = mainObject.getString("error_description");
                    Log.d(TAG, error);
                    Log.d(TAG, errorDescription);
                    identityProxy.receiveNewAccessToken(responseCode, errorDescription, token);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
