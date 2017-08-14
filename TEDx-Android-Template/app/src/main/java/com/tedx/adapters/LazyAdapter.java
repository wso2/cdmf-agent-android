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

package com.tedx.adapters;

import java.util.List;
import java.util.Map;


import com.tedx.alcatraz.R;
import com.tedx.activities.LazyActivity;
import com.tedx.helpers.Common;
import com.tedx.utility.RemoteImageView;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.SimpleAdapter;

public class LazyAdapter extends SimpleAdapter implements Filterable{
	public static final String IMAGE = "LazyAdapter_image";

	private boolean mDone = false;
	private boolean mFlinging = false;
	private LazyActivity mActivity;

	public LazyAdapter(LazyActivity context, List<? extends Map<String, String>> data, int resource, String[] from, int[] to) {
		super(context, data, resource, from, to);
		mActivity = context;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		// see if we need to load the next page
		if (!mDone && (getCount() - position) <= 1) {
			if (mActivity.isTaskFinished()) {
				mActivity.loadNextPage();
			}
		}

		View ret = super.getView(position, convertView, parent);
		if (ret != null) {
			RemoteImageView riv = (RemoteImageView) ret.findViewById(android.R.id.icon);
			if (riv != null && !mFlinging) {
				riv.loadImage();
			}
		}
		return ret;
	}

	public void setStopLoading(boolean done) {
		mDone = done;
	}

	public void setFlinging(boolean flinging) {
		mFlinging = flinging;
	}

	@Override
	public void setViewImage(final ImageView image, final String value) {
		if (value != null && value.length() > 0 && image instanceof RemoteImageView) {
			RemoteImageView riv = (RemoteImageView) image;
			riv.setLocalURI(Common.getCacheFileName(value));
			riv.setRemoteURI(value);
			super.setViewImage(image, R.drawable.missingphoto);
		} else {
			image.setVisibility(View.GONE);
		}
	}
}