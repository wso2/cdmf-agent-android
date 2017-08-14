/*
 * The MIT License

 * Copyright (c) 2010 Peter Ma

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
*/

package com.tedx.logics;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.res.Resources;
import android.os.Bundle;

import com.tedx.alcatraz.R;
import com.tedx.webservices.WebServices;

public class AttendeeLogic {
	public static Bundle GetCurrentDancers(Resources res, String EventUniqueId)
	{
		String Action = "GetAttendeeByUniqueId";
		
		JSONObject requestJSONParameters = new JSONObject();
		try {
			requestJSONParameters.put("EventId", Integer.valueOf(res.getString(R.string.eventId)));
			requestJSONParameters.put("EventUniqueId", EventUniqueId);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			return null;
		}		
		
		String URL = res.getString(R.string.WebServiceAddress) + Action;
		
		JSONObject responseJSON = WebServices.SendHttpPost(URL, requestJSONParameters);
		
		if(responseJSON != null)
		{
			try {
				if(responseJSON.getBoolean("IsSuccessful"))
				{
					Bundle ret = new Bundle();
					ret.putInt("AttendeeId", responseJSON.getInt("AttendeeId"));
					ret.putString("FirstName", responseJSON.getString("FirstName"));
					ret.putString("LastName", responseJSON.getString("LastName"));
					ret.putString("ContactNumber", responseJSON.getString("ContactNumber"));
					ret.putString("Website", responseJSON.getString("Website"));
					ret.putString("Email", responseJSON.getString("Email"));
					ret.putString("Facebook", responseJSON.getString("Facebook"));
					ret.putString("Twitter", responseJSON.getString("Twitter"));
					ret.putString("Description", responseJSON.getString("Description"));

					return ret;
				}
				else return null;
				} catch (JSONException e) {
				// TODO Auto-generated catch block
					return null;
			}
		}
		else return null;
	}
}
