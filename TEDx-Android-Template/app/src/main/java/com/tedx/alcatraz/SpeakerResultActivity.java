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

package com.tedx.alcatraz;

import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;

import com.tedx.alcatraz.R;
import com.tedx.objects.SearchResult;
import com.tedx.activities.LazyActivity;

public class SpeakerResultActivity extends LazyActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
    	requestWindowFeature(Window.FEATURE_NO_TITLE);
		mFrom = new String[] {
				SearchResult.NAME,
				SearchResult.TOPIC,
				SearchResult.PHOTOURL
		};

		mTo = new int[] {
				android.R.id.text1,
				android.R.id.text2,
				android.R.id.icon
		};
		
		super.onCreate(savedInstanceState, R.layout.searchresults, R.layout.searchresultrow);
	}
	

	public void onItemClick(AdapterView<?> list, View row, int position, long id) {
		startActivityForPosition(SpeakerActivity.class, position);
	}

	@Override
	protected LoadTask newLoadTask() {
		return new LoadSearchResultTask();
	}

	@Override
	protected void setTaskActivity() {
		mLoadTask.activity = this;
	}

	protected static class LoadSearchResultTask extends LoadTask {
		@Override
		protected Boolean doInBackground(String... params) {
			SpeakerResultActivity activity = (SpeakerResultActivity) super.activity;

			int EventId = Integer.valueOf(activity.getResources().getString(R.string.eventId));
			
			String Url = 
				com.tedx.logics.SearchResultLogic.getSearchResultsByCriteriaURL(
						activity.getResources(), EventId, activity.mPage);
			return loadUrl(Url);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			if (!result) {
				activity.showDialog(DIALOG_ERROR_LOADING);
			}
		}
	}

	@Override
	protected HashMap<String, String> loadJSON(JSONObject data) throws JSONException {
		HashMap<String, String> SearchResults = new HashMap<String, String>();
		
		SearchResults.put(SearchResult.NAME, data.getString("FirstName") + " " + data.getString("LastName") );
		SearchResults.put(SearchResult.TITLE, data.getString("Title"));
		SearchResults.put(SearchResult.EMAIL, data.getString("Email"));
		SearchResults.put(SearchResult.FACEBOOK, data.getString("Facebook"));
		SearchResults.put(SearchResult.PHOTOURL, data.getString("PhotoUrl"));
		SearchResults.put(SearchResult.SPEAKERID, data.getString("SpeakerId"));
		SearchResults.put(SearchResult.TWITTER, String.valueOf(data.getString("Twitter")));
		SearchResults.put(SearchResult.EMAIL, String.valueOf(data.getString("Email")));
		SearchResults.put(SearchResult.TOPIC, String.valueOf(data.getString("Topic")));

		return SearchResults;
	}
	
    //Back Button
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.ECLAIR
                && keyCode == KeyEvent.KEYCODE_BACK
                && event.getRepeatCount() == 0) {
            // Take care of calling this method on earlier versions of
            // the platform where it doesn't exist.
            onBackPressed();
        }

        return super.onKeyDown(keyCode, event);
    }

    
    @Override
    public void onBackPressed() {
        // This will be called either automatically for you on 2.0
        // or later, or by the code above on earlier versions of the
        // platform.
		finish();
        return;
    }
}
