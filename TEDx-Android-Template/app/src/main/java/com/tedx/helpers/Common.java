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

package com.tedx.helpers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.os.Environment;


public class Common {
	private final static String CACHE_DIRECTORY = Environment.getExternalStorageDirectory() + "/.tedx-cache/";
	
	public static String getCacheFileName(String url) {		
		StringBuilder builder = new StringBuilder();
		builder.setLength(0);
		builder.append(CACHE_DIRECTORY);
		builder.append(url.hashCode()).append(".jpg");
		return builder.toString();
	}
	
	public static String getContent(InputStream is) throws IOException {
		StringBuilder builder = new StringBuilder();
		BufferedReader buffer = new BufferedReader(new InputStreamReader(is));
		String line = null;
		while ((line = buffer.readLine()) != null) {
			builder.append(line).append("\n");
		}
		buffer.close();
		return builder.toString().trim();
	}
}
