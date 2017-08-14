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

package com.tedx.activities;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.tedx.alcatraz.R;
import com.tedx.adapters.LazyAdapter;
import com.tedx.helpers.Common;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

public abstract class LazyActivity extends Activity implements OnItemClickListener {
	private static final String TAG = "LazyActivity";

	protected static final int DIALOG_ERROR_LOADING = 10;

	protected static final int MENU_ARTIST = Menu.FIRST;
	protected static final int MENU_GENRE = Menu.FIRST + 1;

	protected ListView mList;
	protected LazyAdapter mAdapter;
	protected List<HashMap<String, String>> mAdapterData = new ArrayList<HashMap<String, String>>();
	protected String[] mFrom;
	protected int[] mTo;
	private boolean mMultipage = true;

	protected LoadTask mLoadTask;
	protected LoadQueue mScheduler = new LoadQueue();
	protected Exception mException = null;

	protected int mPage = 1;
	protected String mFilter = "";
	
	protected void onCreate(Bundle savedInstanceState, int layoutResId) {
		onCreate(savedInstanceState, layoutResId, android.R.layout.simple_list_item_1);		
	}

	protected void onCreate(Bundle savedInstanceState, int layoutResId, int rowLayoutResId) {		
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(layoutResId);

		mList = (ListView) findViewById(android.R.id.list);
		mAdapter = new LazyAdapter(this, mAdapterData, rowLayoutResId, mFrom, mTo);
		mList.setAdapter(mAdapter);
		mList.setOnItemClickListener(this);
		mList.setFastScrollEnabled(true);
		mList.setTextFilterEnabled(true);

		// setEmptyView(R.layout.loading);

		restoreState();

		if (mLoadTask == null) {
			mLoadTask = newLoadTask();
		}
		setTaskActivity();

		if (mLoadTask != null && mLoadTask.getStatus() == AsyncTask.Status.PENDING) {
			mLoadTask.execute();
		}
	}

	protected void setEmptyView(View v) {
		mList.setEmptyView(v);
	}

	protected void setEmptyView(int resId) {
		setEmptyView(LayoutInflater.from(this).inflate(resId, null));
	}

	protected String format(int resId, Object... args) {
		return String.format(getString(resId, args));
	}

	@SuppressWarnings("unchecked")
	protected void restoreState() {
		// restore state
		Object[] saved = (Object[]) getLastNonConfigurationInstance();
		if (saved != null) {
			mAdapterData.addAll((Collection<? extends HashMap<String, String>>) saved[0]);
			mAdapter.notifyDataSetChanged();
			mLoadTask = (LoadTask) saved[1];
		}
	}

	@Override
	protected Dialog onCreateDialog(int which) {
		switch (which) {
			case DIALOG_ERROR_LOADING:
				return new AlertDialog.Builder(this).setTitle(R.string.error_loading_title).setMessage(R.string.error_loading_message).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_ERROR_LOADING);
					}
				}).create();
		}
		return super.onCreateDialog(which);
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return new Object[] {
				mAdapterData,
				mLoadTask
		};
	}

	final protected void setMultipage(boolean isMultipage) {
		mMultipage = isMultipage;
	}

	final protected boolean isMultipage() {
		return mMultipage;
	}

	final public void loadNextPage() {
		if (mMultipage) {
			mPage++;
			mLoadTask = newLoadTask();
			setTaskActivity();
			mLoadTask.execute();
		}
	}

	final public boolean isTaskFinished() {
		return mLoadTask.getStatus() == AsyncTask.Status.FINISHED;
	}

	abstract protected LoadTask newLoadTask();

	protected void setTaskActivity() {
		mLoadTask.activity = this;
	}

	protected void startActivityForPosition(Class<?> targetCls, int position) {
		HashMap<String, String> info = mAdapterData.get(position);
		Intent i = new Intent(this, targetCls);
		for (String key : info.keySet()) {
			i.putExtra(key, info.get(key));
		}
		startActivity(i);
	}

	protected void setException(Exception e) {
		mException = e;
	}

	protected void handleException() {
		if (mException != null) {
			Log.e(TAG, "Exception!", mException);
			if (mException instanceof UnknownHostException) {
				showDialog(DIALOG_ERROR_LOADING);
			}
		}
		mException = null;
	}

	abstract protected HashMap<String, String> loadJSON(JSONObject json) throws JSONException;

	protected class LoadQueue {
		public static final int PRIORITY_LOW = 0;
		public static final int PRIORITY_HIGH = 1;

		private ArrayList<LoadImageTask> mQueue = new ArrayList<LoadImageTask>();

		public void enqueue(LoadImageTask task) {
			enqueue(task, PRIORITY_LOW);
		}

		public void enqueue(LoadImageTask task, int priority) {
			if (mQueue.size() == 0 || priority == PRIORITY_LOW) {
				mQueue.add(task);
			} else {
				mQueue.add(1, task);
			}
			runFirst();
		}

		public void finished() {
			mQueue.remove(0);
			runFirst();
		}

		private void runFirst() {
			if (mQueue.size() > 0) {
				LoadImageTask task = mQueue.get(0);
				if (task.getStatus() == AsyncTask.Status.PENDING) {
					task.execute();
				} else if (task.getStatus() == AsyncTask.Status.FINISHED) {
					mQueue.remove(0);
					runFirst();
				}
			}
		}
	}

	private static class LoadImageTask extends AsyncTask<String, Integer, Boolean> {
		public LazyActivity activity;
		private String mUrl;

/*		public LoadImageTask(String url) {
			mUrl = url;
		}*/

		@Override
		protected void onPreExecute() {
			activity.mException = null;
		}

		@Override
		protected Boolean doInBackground(String... params) {
			try {
				if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
					URL request = new URL(mUrl);
					InputStream is = (InputStream) request.getContent();
					FileOutputStream fos = new FileOutputStream(Common.getCacheFileName(mUrl));
					try {
						byte[] buffer = new byte[4096];
						int l;
						while ((l = is.read(buffer)) != -1) {
							fos.write(buffer, 0, l);
						}
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						fos.flush();
						is.close();
						fos.close();
					}
				}
				return true;
			} catch (Exception e) {
				activity.setException(e);
			}
			return false;
		}

		@Override
		protected void onPostExecute(Boolean success) {
			activity.handleException();
			activity.mScheduler.finished();
		}
	}
	
	
	protected abstract static class LoadTask extends AsyncTask<String, HashMap<String, String>, Boolean> {
		public LazyActivity activity;
		protected boolean mCancelled;
		protected String mUrl = null;

		@Override
		protected void onPreExecute() {
			activity.mException = null;
			mCancelled = false;
			activity.setProgressBarIndeterminateVisibility(true);
			activity.mAdapter.setStopLoading(true);
			System.gc();
		}

		@Override
		protected void onProgressUpdate(HashMap<String, String>... updates) {
			activity.mAdapterData.add(updates[0]);
			activity.mAdapter.notifyDataSetChanged();
			activity.mAdapter.setStopLoading(false);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			activity.handleException();
			activity.setProgressBarIndeterminateVisibility(false);
			System.gc();
		}

		public Uri getLoadedURL() {
			if (mUrl != null) {
				return Uri.parse(mUrl);
			}
			return null;
		}
		
		@SuppressWarnings("unchecked")
		final protected boolean loadUrl(String url) {
			mUrl = url;
			try {
				URL request = new URL(mUrl);
				String jsonRaw = Common.getContent((InputStream) request.getContent());
				JSONArray collection = new JSONArray(jsonRaw);
				for (int i = 0; i < collection.length(); i++) {
					if (isCancelled()) {
						return false;
					}
					try {
						publishProgress(activity.loadJSON(collection.getJSONObject(i)));
					} catch (JSONException e) {
					}
				}
				return true;
			} catch (Exception e) {
				activity.setException(e);
			}
			return false;
		}
		
	}
}
