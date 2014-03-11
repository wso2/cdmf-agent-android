/*
 ~ Copyright (c) 2014, WSO2 Inc. (http://wso2.com/) All Rights Reserved.
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~      http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
*/
package org.wso2.emm.agent.services;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.json.simple.parser.JSONParser;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ProcessMessage {
	Operation operation;
	Map<String, String> params;
	public ProcessMessage(Context context, int mode, String message, String recepient) {
		// TODO Auto-generated constructor stub
		JSONParser jp = new JSONParser();
		params = new HashMap<String, String>();
    	try {
    		
			JSONObject jobj = new JSONObject(message);
			Log.v("JSON OUTPUT : ", jobj.toString());
            params.put("code", (String)jobj.get("message"));
            if(jobj.has("data")){
            	params.put("data", ((JSONObject)jobj.get("data")).toString());
            }
            
            operation = new Operation(context, mode, params, recepient);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
	}
	
	public ProcessMessage(Context context, int mode, Intent intent) {
		// TODO Auto-generated constructor stub
		operation = new Operation(context, mode, intent);
	}
}
