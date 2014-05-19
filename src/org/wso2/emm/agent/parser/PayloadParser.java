package org.wso2.emm.agent.parser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class PayloadParser {

	public String generateReply(JSONArray inputArray, String regId) {
		Log.e("data",inputArray.toString());
		JSONObject outerJson = new JSONObject();		
		JSONArray outerArr = new JSONArray();
		try {
			outerJson.put("regId", regId);
	        outerJson.put("data",outerArr);
        } catch (JSONException e1) {
	        // TODO Auto-generated catch block
	        e1.printStackTrace();
        }
		
		
		
		for (int i = 0; i < inputArray.length(); i++) {
			try {
	            String code=inputArray.getJSONObject(i).getString("code");
	            String messageId=inputArray.getJSONObject(i).getString("messageId");
	            JSONArray data=inputArray.getJSONObject(i).getJSONArray("data");
Log.e("data",data.toString());
	            
	            JSONObject dataArrContents=new JSONObject();
	            
	            dataArrContents.put("code", code);
	            JSONArray innerDataArr = new JSONArray();
	            
	            JSONObject innerDataOb=new JSONObject(); 
	            innerDataOb.put("messageId", messageId);
	            innerDataOb.put("data", data);
	            innerDataArr.put(innerDataOb);
	            
	            
	            dataArrContents.put("data", innerDataArr);
	            
	            outerArr.put(dataArrContents);
	            
	            
	            
            } catch (JSONException e) {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
            }
					
			

		}

		return outerJson.toString();

	}
}
