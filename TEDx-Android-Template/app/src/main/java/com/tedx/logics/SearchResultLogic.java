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

import android.content.res.Resources;
import android.net.Uri;

import com.tedx.alcatraz.R;


public class SearchResultLogic 
{
	// get url for getting search results by search string
	// for background loading
	public static String getSearchResultsByCriteriaURL(Resources res, int EventId, int Page) {		
		String Action = "GetSpeakersByEventId";
		String URL = res.getString(R.string.WebServiceAddress) + Action;		
		
		Uri u = Uri.parse(URL);
		Uri.Builder builder = u.buildUpon();		
		builder.appendQueryParameter("eventid", String.valueOf(EventId));
		builder.appendQueryParameter("page", String.valueOf(Page));
		URL = builder.build().toString();
		
		return URL;
	}
}