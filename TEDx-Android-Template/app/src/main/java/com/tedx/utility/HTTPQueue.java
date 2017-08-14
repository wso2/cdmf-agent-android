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

import java.util.ArrayList;
import java.util.HashMap;

import android.os.Handler;
import android.os.Message;

public class HTTPQueue {
	public static final int PRIORITY_LOW = 0;
	public static final int PRIORITY_HIGH = 1;

	private volatile static HTTPQueue sInstance = null;

	private ArrayList<HTTPThread> mQueue = new ArrayList<HTTPThread>();
	private HashMap<Long, Boolean> mThreads = new HashMap<Long, Boolean>();
	private Handler mQueuedHandler = null;

	private HTTPQueue() {
	}

	public static HTTPQueue getInstance() {
		if (sInstance == null) {
			sInstance = new HTTPQueue();
		}
		return sInstance;
	}

	public void enqueue(HTTPThread task) {
		enqueue(task, PRIORITY_LOW);
	}

	public synchronized void enqueue(HTTPThread task, int priority) {
		Boolean exists = mThreads.get(task.getId());
		if (exists == null) {
			if (mQueue.size() == 0 || priority == PRIORITY_LOW) {
				mQueue.add(task);
			} else {
				mQueue.add(1, task);
			}
			mThreads.put(task.getId(), true);
		}
		runFirst();
	}

	public synchronized void dequeue(final HTTPThread task) {
		mThreads.remove(task.getId());
		mQueue.remove(task);
	}

	public synchronized void finished(int result) {
		if (mQueuedHandler != null) {
			mQueuedHandler.sendEmptyMessage(result);
		}
		runFirst();
	}

	private synchronized void runFirst() {
		if (mQueue.size() > 0) {
			HTTPThread task = mQueue.get(0);
			if (task.getStatus() == HTTPThread.STATUS_PENDING) {
				mQueuedHandler = task.getHandler();
				task.setHandler(mHandler);
				task.start();
			} else if (task.getStatus() == HTTPThread.STATUS_FINISHED) {
				HTTPThread thread = mQueue.remove(0);
				mThreads.remove(thread.getId());
				runFirst();
			}
		}
	}

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message message) {
			finished(message.what);
		}
	};
}
