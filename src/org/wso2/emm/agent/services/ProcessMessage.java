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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.wso2.emm.agent.AlreadyRegisteredActivity;
import org.wso2.emm.agent.AuthenticationErrorActivity;
import org.wso2.emm.agent.MainActivity;
import org.wso2.emm.agent.R;
import org.wso2.emm.agent.parser.PayloadParser;
import org.wso2.emm.agent.utils.CommonUtilities;
import org.wso2.emm.agent.utils.ServerUtilities;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.util.Log;

public class ProcessMessage {
	Operation operation;
	Map<String, String> params;
	AsyncTask<Void, Void, String> sendReply;
	Map<String, String> responsePayload;
	Context c;
	String replyPayload;
	
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
	
	//local notification message handler
	public ProcessMessage(Context context, int mode, String msg) {
		c=context;
		if(msg!=null && !msg.equals("")){
    		Log.e("", " "+msg);
    		messageExecute(msg);
		}
		
	}

	private void messageExecute(String msg) {
		JSONArray repArray =new JSONArray();
		JSONObject jsReply=null;
		String msgId="";
		
		
		JSONArray dataReply=null;
        
		
		try {
	        JSONArray jArr=new JSONArray(msg.trim());
	        for(int i=0;i<jArr.length();i++){
	        	JSONArray innerArr=new JSONArray(jArr.getJSONObject(i).getString("data"));
	        	String featureCode=jArr.getJSONObject(i).getString("code");
	        	dataReply=new JSONArray();
	        	jsReply=new JSONObject();
	        	jsReply.put("code",featureCode);
	        	
	        	
	        	for(int x=0;x<innerArr.length();x++){
    	        		msgId=innerArr.getJSONObject(x).getString("messageId");
    	        		jsReply.put("messageId", msgId);
    	        		
//    	        		if (featureCode.equals(CommonUtilities.OPERATION_POLICY_BUNDLE)) {
//    						SharedPreferences mainPrefp =
//    						                              c.getSharedPreferences("com.mdm",
//    						                                                    Context.MODE_PRIVATE);
//    						Editor editorp = mainPrefp.edit();
//    						editorp.putString("policy", "");
//    						editorp.commit();
//
//    						SharedPreferences mainPref =
//    						                             c.getSharedPreferences("com.mdm",
//    						                                                    Context.MODE_PRIVATE);
//    						Editor editor = mainPref.edit();
//    						editor.putString("policy", innerArr.getJSONObject(x).getJSONObject("data")
//    						                                   .getJSONArray("policies").toString());
//    						editor.commit();
//    		        	}
    	        		
    	        		String msgData=innerArr.getJSONObject(x).getString("data");
    	        		JSONObject dataObj=new JSONObject("{}");
    	        		operation = new Operation(c);
    	        		if(featureCode.equalsIgnoreCase(CommonUtilities.OPERATION_POLICY_REVOKE)){
    	        			 operation.operate(featureCode,jsReply);
    	        			 jsReply.put("status", msgId);
    	        		}else{
        	        		if(msgData.charAt(0)=='['){
        	        			JSONArray dataArr=new JSONArray(msgData);
        	        			for(int a=0;a<dataArr.length();a++){
        	        				JSONObject innterDataObj=dataArr.getJSONObject(a);
        	        				featureCode=innterDataObj.getString("code");
        	        				String dataTemp=innterDataObj.getString("data");
        	        				if(!dataTemp.isEmpty() && dataTemp!=null && !dataTemp.equalsIgnoreCase("null"))
        	        					dataObj =innterDataObj.getJSONObject("data");
        	        				
        	        				dataReply= operation.operate(featureCode,dataObj);
        	        				//dataReply.put(resultJson);
        	        			}
        	        		}else {
        	        			if(!msgData.isEmpty() && msgData!=null && !msgData.equalsIgnoreCase("null"))
        	        				dataObj =new JSONObject(msgData);
        	        			dataReply= operation.operate(featureCode,dataObj);
        	        			//dataReply.put(resultJson);
        	        		}
    	        		}
	        		
	        	}
	        	jsReply.put("data", dataReply);
        		repArray.put(jsReply);
	        }
        } catch (JSONException e) {
	        // TODO Auto-generated catch block
	        e.printStackTrace();
        }
		catch (Exception e) {
	        // TODO Auto-generated catch block
	        e.printStackTrace();
        }
		finally{
			SharedPreferences mainPref = c.getSharedPreferences( c.getResources().getString(R.string.shared_pref_package), Context.MODE_PRIVATE);
			String regId=mainPref.getString(c.getResources().getString(R.string.shared_pref_regId), "");
			PayloadParser ps=new PayloadParser();
			
			replyPayload=ps.generateReply(repArray,regId);
			Log.e("reply",replyPayload);
			sendReply = new AsyncTask<Void, Void, String>() {
				
		        @Override
		        protected String doInBackground(Void... params) {
		        	try{
		        		SharedPreferences mainPref = c
		        				.getSharedPreferences(c.getResources().getString(R.string.shared_pref_package),
		        						Context.MODE_PRIVATE);
		        		String regId=mainPref.getString(c.getResources().getString(R.string.shared_pref_regId), "");

		        		
		        		
		        		Map<String, String> paramsReply = new HashMap<String, String>();
		        		responsePayload = new HashMap<String, String>();
		        		paramsReply.put("regId", regId);
		        		paramsReply.put("data", replyPayload);
		        		responsePayload=ServerUtilities.sendWithTimeWait("notifications/pendingOperations", paramsReply,
		        		 				"POST", c);
		        		
		        		
		    			
		        	}catch(Exception e){
		        		e.printStackTrace();
		        	}
		        	return null;
		        }
		
		       
		        @Override
		        protected void onPreExecute()
		        {
		           
		            //do initialization of required objects objects here                
		        }; 
		        @Override
		        protected void onPostExecute(String result) {
		        	try{
		        	String status = responsePayload.get("status");
	        		String res =  responsePayload.get("response");
	        		if(status!=null && status.equalsIgnoreCase("200")){
	        			if(res!=null){
	        				messageExecute(res);
	        			}    			
	        		}
	        		
	        		
	    			Log.v("OPERATION RESPONSE", responsePayload.get("response"));
		        	}catch(Exception e){
		        		e.printStackTrace();
		        	}
		        	sendReply = null;
		           
		        }
		
		    };
		    sendReply.execute(null, null, null);
			
		}
	    
    }

	
}
