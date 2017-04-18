/*
 *
 *  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.wso2.iot.agent.services;

import android.content.Context;
import android.util.Log;
import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.beans.ServerConfig;
import org.wso2.iot.agent.beans.UnregisterProfile;
import org.wso2.iot.agent.proxy.IDPTokenManagerException;
import org.wso2.iot.agent.proxy.IdentityProxy;
import org.wso2.iot.agent.proxy.beans.EndPointInfo;
import org.wso2.iot.agent.proxy.interfaces.APIResultCallBack;
import org.wso2.iot.agent.proxy.utils.ServerUtilities;
import org.wso2.iot.agent.utils.CommonUtils;
import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.beans.ApiRegistrationProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * This class is used to register and unregister oauth application.
 */
public class DynamicClientManager implements APIResultCallBack {

    private static final String TAG = DynamicClientManager.class.getSimpleName();
    private static final String USER_ID = "userId";
    private static final String CONSUMER_KEY = "consumerKey";
    private static final String APPLICATION_NAME = "applicationName";
    private static final int MAX_RETRIES = 3;

    /**
     * This method is used to register an oauth application in the backend.
     *
     * @param apiRegistrationProfile Payload of the register request.
     * @param utils Server configurations.
     *
     * @return returns consumer key and consumer secret if success. Else returns null
     *         if it fails to register.
     * @throws AndroidAgentException
     */
    public void getClientCredentials(String username, String password, ServerConfig utils,
                                     Context context, APIResultCallBack apiResultCallback,
                                     ApiRegistrationProfile apiRegistrationProfile)
            throws AndroidAgentException {
        IdentityProxy.getInstance().setContext(context);
        EndPointInfo endPointInfo = new EndPointInfo();
        String endPoint = utils.getAPIServerURL(context) +
                org.wso2.iot.agent.utils.Constants.API_APPLICATION_REGISTRATION_CONTEXT;
        endPointInfo.setHttpMethod(org.wso2.iot.agent.proxy.utils.Constants.HTTP_METHODS.POST);
        endPointInfo.setEndPoint(endPoint);
        endPointInfo.setRequestParams(apiRegistrationProfile.toJSON());
        sendRequest(endPointInfo, apiResultCallback, Constants.DYNAMIC_CLIENT_REGISTER_REQUEST_CODE,
                username, password);
    }

    /**
     * This method is used to unregister the oauth application that has been
     * registered at the device authentication.
     *
     * @param profile Payload of the unregister request.
     * @param utils Server configurations
     *
     * @return true if unregistration success, else false.
     * @throws AndroidAgentException
     */
    public boolean unregisterClient(UnregisterProfile profile, ServerConfig utils, Context context,
                                 APIResultCallBack apiResultCallback)
            throws AndroidAgentException {
        StringBuilder endPoint = new StringBuilder();
        endPoint.append(utils.getAPIServerURL(context));
        endPoint.append(Constants.API_APPLICATION_UNREGISTRATION_CONTEXT);
        endPoint.append("?" + APPLICATION_NAME + "=" + profile.getApplicationName());
        CommonUtils.callSecuredAPI(context, endPoint.toString(),
                org.wso2.iot.agent.proxy.utils.Constants.HTTP_METHODS.DELETE,
                null, DynamicClientManager.this, Constants.UNREGISTER_REQUEST_CODE);
        return true;
    }

    /**
     * This method is used to send requests to backend.
     * The reason to use this method because the function which is already
     * available for sending requests is secured with token. Therefor this can be used
     * to send requests without tokens.
     */
    private void sendRequest(final EndPointInfo endPointInfo,
                             final APIResultCallBack apiResultCallback, final int requestCode,
                             final String username, final String password) {
        RequestQueue queue =  null;
        int requestMethod = 0;
        org.wso2.iot.agent.proxy.utils.Constants.HTTP_METHODS httpMethod = endPointInfo.getHttpMethod();
        switch (httpMethod) {
            case POST:
                requestMethod = Request.Method.POST;
                break;
            case DELETE:
                requestMethod = Request.Method.DELETE;
                break;
        }

        try {
            queue = ServerUtilities.getCertifiedHttpClient();
        } catch (IDPTokenManagerException e) {
            Log.e(TAG, "Failed to retrieve HTTP client", e);
        }

        JsonObjectRequest request = null;
        try {
            request = new JsonObjectRequest(requestMethod, endPointInfo.getEndPoint(),
                                            (endPointInfo.getRequestParams() != null) ?
                                            new JSONObject(endPointInfo.getRequestParams()) : null,
                                                      new Response.Listener<JSONObject>() {
                                                          @Override
                                                          public void onResponse(JSONObject response) {
                                                              Log.d(TAG, response.toString());
                                                          }
                                                      },
                                                      new Response.ErrorListener() {
                                                          @Override
                                                          public void onErrorResponse(VolleyError error) {
                                                              Log.d(TAG, error.toString());
                                                              Map<String, String> responseParams = new HashMap<>();
                                                              String statusCode = "500";
                                                              if (error.networkResponse != null) {
                                                                  statusCode = String.valueOf(error.networkResponse.statusCode);
                                                              }
                                                              responseParams.put(
                                                                      org.wso2.iot.agent.proxy.utils.Constants.SERVER_RESPONSE_STATUS,
                                                                      statusCode
                                                              );
                                                              if (com.android.volley.ParseError.class.isInstance(error)) {
                                                                  responseParams.put(
                                                                          org.wso2.iot.agent.proxy.utils.Constants.SERVER_RESPONSE_BODY,
                                                                          "Invalid tenant domain"
                                                                  );
                                                              } else if (error.getMessage() != null) {
                                                                  responseParams.put(
                                                                          org.wso2.iot.agent.proxy.utils.Constants.SERVER_RESPONSE_BODY,
                                                                          error.getMessage()
                                                                  );
                                                              }
                                                              apiResultCallback.onReceiveAPIResult(responseParams, requestCode);
                                                          }
                                                      })

            {
                @Override
                protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
                    String result = new String(response.data);
                    if(org.wso2.iot.agent.proxy.utils.Constants.DEBUG_ENABLED) {
                        if(result != null && !result.isEmpty()) {
                            Log.d(TAG, "Result :" + result);
                        }
                    }
                    Map<String, String> responseParams = new HashMap<>();
                    responseParams.put(org.wso2.iot.agent.proxy.utils.Constants.SERVER_RESPONSE_BODY, result);
                    responseParams.put(org.wso2.iot.agent.proxy.utils.Constants.SERVER_RESPONSE_STATUS, String.valueOf(
                            response.statusCode));
                    apiResultCallback.onReceiveAPIResult(responseParams, requestCode);
                    return super.parseNetworkResponse(response);
                }

                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json");
                    headers.put("Accept", "application/json");
                    headers.put("User-Agent", Constants.USER_AGENT);
                    if (username != null && password != null) {
                        String basicAuthValue = basicAuthValue = "Basic " +
                                new String(Base64.encodeBase64((username + ":" + password)
                                        .getBytes()));
                        headers.put("Authorization", basicAuthValue);
                    }
                    return headers;
                }
            };
            request.setRetryPolicy(new DefaultRetryPolicy(
                    org.wso2.iot.agent.proxy.utils.Constants.HttpClient.DEFAULT_TIME_OUT,
                    MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse request JSON", e);
        }
        request.setRetryPolicy(new DefaultRetryPolicy(
                org.wso2.iot.agent.proxy.utils.Constants.HttpClient.DEFAULT_TIME_OUT,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(request);
    }

    @Override
    public void onReceiveAPIResult(Map<String, String> result, int requestCode) {}
}
