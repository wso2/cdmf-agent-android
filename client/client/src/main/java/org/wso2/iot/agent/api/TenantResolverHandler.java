/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.iot.agent.api;

import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.beans.Tenant;
import org.wso2.iot.agent.proxy.IDPTokenManagerException;
import org.wso2.iot.agent.proxy.utils.ServerUtilities;
import org.wso2.iot.agent.utils.Constants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TenantResolverHandler {

    private static final String TAG = TenantResolverHandler.class.getSimpleName();

    private static final String SET_COOKIE_KEY = "Set-Cookie";
    private static final String COOKIE_KEY = "Cookie";
    private static final String SESSION_COOKIE = "JSESSIONID";
    private static final String CLOUD_MANAGER_LOGIN = Constants.CLOUD_MANAGER
            + "/site/blocks/user/authenticate/ajax/login.jag";
    private static final String CLOUD_MANAGER_TENANTS = Constants.CLOUD_MANAGER
            + "/site/blocks/tenant/manage/list/ajax/list.jag?action=getOrganizations";
    private static final String ACTION_LABEL = "action";
    private static final String ACTION = "login";
    private static final String USERNAME_LABEL = "userName";
    private static final String PASSWORD_LABEL = "password";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String CONTENT_TYPE_FORM_URL_ENCODED = "application/x-www-form-urlencoded";

    private String cookie;
    private TenantResolverCallback callback;
    private RequestQueue queue;

    public TenantResolverHandler(TenantResolverCallback callback) {
        this.callback = callback;
    }

    public void resolveTenantDomain(String userName, String password) {
        try {
            queue = ServerUtilities.getCertifiedHttpClient();
        } catch (IDPTokenManagerException e) {
            Log.e(TAG, "Failed to retrieve HTTP client", e);
        }
        login(userName, password);
    }

    private void login(final String userName, final String password) {
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Login in to cloud manager.");
        }

        StringRequest request = new StringRequest(Request.Method.POST, CLOUD_MANAGER_LOGIN,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        if (Constants.DEBUG_MODE_ENABLED) {
                            Log.d(TAG, "Login Response: " + response);
                            if (response != null && response.trim().equals("true")) {
                                callback.onAuthenticationSuccess();
                                requestTenantDetails();
                            } else {
                                callback.onAuthenticationFail();
                            }
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        AndroidAgentException androidAgentException;
                        if (error.networkResponse != null) {
                            Log.w(TAG, error.toString() + " Status code: " + error.networkResponse.statusCode);
                            androidAgentException
                                    = new AndroidAgentException("Code: " + error.networkResponse.statusCode);
                        } else {
                            Log.e(TAG, error.toString());
                            androidAgentException
                                    = new AndroidAgentException(error.toString());
                        }
                        callback.onFailure(androidAgentException);
                    }
                })

        {
            @Override
            protected Response<String> parseNetworkResponse(NetworkResponse response) {
                setSessionCookie(response.headers);
                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, "Response status: " + String.valueOf(response.statusCode));
                    Log.d(TAG, "Response content: " + new String(response.data));
                }
                return super.parseNetworkResponse(response);
            }

            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> requestParams = new HashMap<>();
                requestParams.put(ACTION_LABEL, ACTION);
                requestParams.put(USERNAME_LABEL, userName);
                requestParams.put(PASSWORD_LABEL, password);
                return requestParams;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put(CONTENT_TYPE_HEADER, CONTENT_TYPE_FORM_URL_ENCODED);
                return headers;
            }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(
                org.wso2.iot.agent.proxy.utils.Constants.HttpClient.DEFAULT_TIME_OUT,
                org.wso2.iot.agent.proxy.utils.Constants.HttpClient.DEFAULT_RETRY_COUNT,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(request);
    }

    private void requestTenantDetails() {
        if (Constants.DEBUG_MODE_ENABLED) {
            Log.d(TAG, "Requesting tenant details from cloud manager.");
        }
        StringRequest request = new StringRequest(Request.Method.GET, CLOUD_MANAGER_TENANTS,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        if (Constants.DEBUG_MODE_ENABLED) {
                            Log.d(TAG, "Tenant details Response: " + response);
                            Gson gson = new Gson();
                            List<Tenant> tenants = gson.fromJson(response, new TypeToken<List<Tenant>>(){}.getType());
                            callback.onTenantResolved(tenants);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        AndroidAgentException androidAgentException;
                        if (error.networkResponse != null) {
                            Log.w(TAG, error.toString() + " Status code: " + error.networkResponse.statusCode);
                            androidAgentException
                                    = new AndroidAgentException("Code: " + error.networkResponse.statusCode);
                        } else {
                            Log.e(TAG, error.toString());
                            androidAgentException
                                    = new AndroidAgentException(error.toString());
                        }
                        callback.onFailure(androidAgentException);
                    }
                })

        {
            @Override
            protected Response<String> parseNetworkResponse(NetworkResponse response) {
                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, "Response status: " + String.valueOf(response.statusCode));
                    Log.d(TAG, "Response content: " + new String(response.data));
                }
                return super.parseNetworkResponse(response);
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put(COOKIE_KEY, cookie);
                return headers;
            }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(
                org.wso2.iot.agent.proxy.utils.Constants.HttpClient.DEFAULT_TIME_OUT,
                org.wso2.iot.agent.proxy.utils.Constants.HttpClient.DEFAULT_RETRY_COUNT,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(request);
    }

    /**
     * Checks the response headers for session cookie and saves it if finds.
     * @param headers Response Headers.
     */
    private void setSessionCookie(Map<String, String> headers) {
        if (headers.containsKey(SET_COOKIE_KEY)
                && headers.get(SET_COOKIE_KEY).startsWith(SESSION_COOKIE)) {
            String _cookie = headers.get(SET_COOKIE_KEY);
            if (_cookie.length() > 0) {
                String[] splitCookie = _cookie.split(";");
                cookie = splitCookie[0];
                if (Constants.DEBUG_MODE_ENABLED) {
                    Log.d(TAG, "Cookie: " + cookie);
                }
            }
        }
    }

}
