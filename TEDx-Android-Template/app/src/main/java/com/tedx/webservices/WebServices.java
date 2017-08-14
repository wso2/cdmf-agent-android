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

package com.tedx.webservices;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.zip.GZIPInputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;
import android.util.Log;

public class WebServices {
	private static final String TAG = "HttpClient";
	
	public static JSONArray SendHttpPostArray(String URL, JSONObject jsonObjSend)
	{
		try {
		   DefaultHttpClient httpclient = new DefaultHttpClient();
		   HttpPost httpPostRequest = new HttpPost(URL);
			
		   StringEntity se;
		   se = new StringEntity(jsonObjSend.toString());
			
		   // Set HTTP parameters
		   httpPostRequest.setEntity(se);
		   httpPostRequest.setHeader("Accept", "application/json");
		   httpPostRequest.setHeader("Content-type", "application/json");
		   //httpPostRequest.setHeader("Accept-Encoding", "gzip"); // only set this parameter if you would like to use gzip compression
			
		   long t = System.currentTimeMillis();
		   HttpResponse response = (HttpResponse) httpclient.execute(httpPostRequest);
		   Log.i(TAG, "HTTPResponse received in [" + (System.currentTimeMillis()-t) + "ms]");
			
		   // Get hold of the response entity (-> the data):
		   HttpEntity entity = response.getEntity();
		
		   if (entity != null) {
		   // Read the content stream
		   InputStream instream = entity.getContent();
		   Header contentEncoding = response.getFirstHeader("Content-Encoding");
		   if (contentEncoding != null && contentEncoding.getValue().equalsIgnoreCase("gzip")) {
			   instream = new GZIPInputStream(instream);
		   }
			
		   // convert content stream to a String
		   String resultString= convertStreamToString(instream);
		   instream.close();			    
			
		   // Transform the String into a JSONObject
		   JSONArray jsonObjRecv = new JSONArray(resultString);
		   // Raw DEBUG output of our received JSON object:
		   Log.i(TAG,"<jsonobject>\n"+jsonObjRecv.toString()+"\n</jsonobject>");	    
			    
		   return jsonObjRecv;
		   } 
		}
		catch (Exception e)
		{
		   // More about HTTP exception handling in another tutorial.
		   // For now we just print the stack trace.
		   e.printStackTrace();
		}
		return null;
	}
	
	public static JSONObject SendHttpPost(String URL, JSONObject jsonObjSend) {
	  try {
	   DefaultHttpClient httpclient = new DefaultHttpClient();
	   HttpPost httpPostRequest = new HttpPost(URL);
	
	   StringEntity se;
	   se = new StringEntity(jsonObjSend.toString());
	
	   // Set HTTP parameters
	   httpPostRequest.setEntity(se);
	   httpPostRequest.setHeader("Accept", "application/json");
	   httpPostRequest.setHeader("Content-type", "application/json");
	   //httpPostRequest.setHeader("Accept-Encoding", "gzip"); // only set this parameter if you would like to use gzip compression
	
	   long t = System.currentTimeMillis();
	   HttpResponse response = (HttpResponse) httpclient.execute(httpPostRequest);
	   Log.i(TAG, "HTTPResponse received in [" + (System.currentTimeMillis()-t) + "ms]");
	
	   // Get hold of the response entity (-> the data):
	   HttpEntity entity = response.getEntity();
	
	   if (entity != null) {
	    // Read the content stream
	    InputStream instream = entity.getContent();
	    Header contentEncoding = response.getFirstHeader("Content-Encoding");
	    if (contentEncoding != null && contentEncoding.getValue().equalsIgnoreCase("gzip")) {
	     instream = new GZIPInputStream(instream);
	    }
	
	    // convert content stream to a String
	    String resultString= convertStreamToString(instream);
	    instream.close();
	    
	    // remove wrapping "[" and "]"
	    if(resultString.substring(0, 1).contains("["))	    	
	    	resultString = resultString.substring(1,resultString.length()-1);
	
	    // Transform the String into a JSONObject
	    JSONObject jsonObjRecv = new JSONObject(resultString);
	    // Raw DEBUG output of our received JSON object:
	    Log.i(TAG,"<jsonobject>\n"+jsonObjRecv.toString()+"\n</jsonobject>");	    
	    
	    return jsonObjRecv;
	   } 
	
	  }
	  catch (Exception e)
	  {
	   // More about HTTP exception handling in another tutorial.
	   // For now we just print the stack trace.
	   e.printStackTrace();
	  }
	  return null;
	 }
	
	
	private static String convertStreamToString(InputStream is) {
	/*
	 * To convert the InputStream to String we use the BufferedReader.readLine()
	 * method. We iterate until the BufferedReader return null which means
	 * there's no more data to read. Each line will appended to a StringBuilder
	 * and returned as String.
	 * 
	 * (c) public domain: http://senior.ceng.metu.edu.tr/2009/praeda/2009/01/11/a-simple-restful-client-at-android/
	 */
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		StringBuilder sb = new StringBuilder();
		
		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				sb.append(line + "\n");
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
		return sb.toString();
	}
}
