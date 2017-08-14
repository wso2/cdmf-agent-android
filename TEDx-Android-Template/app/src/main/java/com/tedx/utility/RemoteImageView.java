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

package com.tedx.utility;

import java.io.File;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.tedx.alcatraz.R;

public class RemoteImageView extends ImageView{
	private String mLocal;
	private String mRemote;
	private HTTPThread mThread = null;

	public RemoteImageView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public RemoteImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public void setLocalURI(String local) {
		mLocal = local;
	}

	public void setRemoteURI(String uri) {
		if (uri.startsWith("http")) {
			mRemote = uri;
		}
	}

	public void loadImage() {
		if (mRemote != null) {
			if (mLocal == null) {
				mLocal = Environment.getExternalStorageDirectory() + "/.remote-image-view-cache/" + mRemote.hashCode() + ".jpg";
			}
			// check for the local file here instead of in the thread because
			// otherwise previously-cached files wouldn't be loaded until after
			// the remote ones have been downloaded.
			File local = new File(mLocal);
			if (local.exists()) {
				setFromLocal();
			} else {
				// we already have the local reference, so just make the parent
				// directories here instead of in the thread.
				local.getParentFile().mkdirs();
				queue();
			}
		}
	}

	@Override
	public void finalize() {
		if (mThread != null) {
			HTTPQueue queue = HTTPQueue.getInstance();
			queue.dequeue(mThread);
		}
	}

	private void queue() {
		if (mThread == null) {
			mThread = new HTTPThread(mRemote, mLocal, mHandler);
			HTTPQueue queue = HTTPQueue.getInstance();
			queue.enqueue(mThread, HTTPQueue.PRIORITY_HIGH);
		}
		setImageResource(R.drawable.missingphoto);
	}

	private void setFromLocal() {
		mThread = null;
		Drawable d = Drawable.createFromPath(mLocal);
		if (d != null) {
			setImageDrawable(d);
		}
	}

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			setFromLocal();
		}
	};
}
