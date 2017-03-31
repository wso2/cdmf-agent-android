/*
 * Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.iot.agent.proxy.utils;

import com.android.volley.RequestQueue;
import org.wso2.iot.agent.proxy.IDPTokenManagerException;
import org.wso2.iot.agent.proxy.clients.CommunicationClient;
import org.wso2.iot.agent.proxy.clients.CommunicationClientFactory;

import java.util.Date;
import java.util.Map;

/**
 * This class represents all the utilities used for network communication between SDK 
 * and authorization server.
 */
public class ServerUtilities {
	private final static String TAG = "ServerUtilities";

	private static CommunicationClient communicationClient;
	private static RequestQueue requestQueue;

	/**
	 * Validate the token expiration date.
	 *
	 * @param expirationDate - Token expiration date.
	 * @return - Token status.
	 */
	public static boolean isExpired(Date expirationDate) {
		Date currentDate = new Date();
		return currentDate.after(expirationDate);
	}

	public static void addHeaders(Map<String, String> headers) {
		if (communicationClient == null) {
			CommunicationClientFactory communicationClientFactory = new CommunicationClientFactory();
			communicationClient = communicationClientFactory.
					getClient(Constants.HttpClient.HTTP_CLIENT_IN_USE);
		}
		if (communicationClient != null) {
			communicationClient.addAdditionalHeader(headers);
		}
	}

	/**
	 * Get HTTP client object according to the calling protocol type.
	 */
	public static RequestQueue getCertifiedHttpClient() throws IDPTokenManagerException {
		if (communicationClient == null) {
			CommunicationClientFactory communicationClientFactory = new CommunicationClientFactory();
			communicationClient = communicationClientFactory.
					getClient(Constants.HttpClient.HTTP_CLIENT_IN_USE);
		}
		if (requestQueue == null) {
			requestQueue = communicationClient.getHttpClient();
		}
		return requestQueue;
	}

}
