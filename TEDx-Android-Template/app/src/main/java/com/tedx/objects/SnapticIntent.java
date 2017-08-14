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

package com.tedx.objects;

public final class SnapticIntent {
	// Intent actions
	public static final String ACTION_ADD = "com.snaptic.intent.action.ADD";
	public static final String ACTION_VIEW = "com.snaptic.intent.action.VIEW";
	
	// Intent extras for ACTION_ADD
	public static final String EXTRA_SOURCE = "com.snaptic.intent.extra.SOURCE";
	public static final String EXTRA_LOCATION = "com.snaptic.intent.extra.LOCATION";
	public static final String EXTRA_CURSOR_POSITION = "com.snaptic.intent.extra.CURSOR_POSITION";
	public static final String EXTRA_AUTOSAVE = "com.snaptic.intent.extra.AUTOSAVE";
	
	// Intent extras for ACTION_VIEW
	public static final String EXTRA_VIEW_FILTER = "com.snaptic.intent.extra.VIEW_FILTER";
}
