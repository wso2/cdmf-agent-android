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

import com.tedx.alcatraz.R;
import com.tedx.objects.SnapticIntent;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.location.Location;
import android.net.Uri;
import android.widget.Toast;

public class IntentIntegrator {
	private static final String NOTES_PACKAGE_NAME = "com.threebanana.notes"; 
	private static final String NOTES_MARKET_URI = "http://market.android.com/search?q=pname:" + NOTES_PACKAGE_NAME;
	
	private static final int NOTES_MIN_VERSION_CODE = 19;
	
	private final Context _context;
	
	public IntentIntegrator(Context context) {
		_context = context;
	}
	
	public void createNote(String message) {
		createNote(message, null, -1, false, null);
	}

	public void createNote(String message, int cursorPosition) {
		createNote(message, null, cursorPosition, false, null);
	}

	public void createNote(String message, boolean autoSave) {
		createNote(message, null, -1, autoSave, null);
	}

	public void createNote(String message, Uri imageUri) {
		createNote(message, null, -1, false, imageUri);
	}
	
	public void createNote(String message, Location location) {
		createNote(message, location, -1, false, null);
	}
	
	public void createNote(String message, Location location, int cursorPosition, boolean autoSave, Uri imageUri) {
		// Verify that correct version of notes is installed
		if (!isNotesInstalled()) {
			return;
		}
		
		// Create the Intent		
		Intent intent = new Intent();
		
		// This action signifies you want to add a new note to the user's notebook
		intent.setAction(SnapticIntent.ACTION_ADD);
		
		// Mandatory. This will be the content of the note. The object should be
		// a String.
		intent.putExtra(Intent.EXTRA_TEXT, message);

		// Mandatory; EXTRA_SOURCE identifies your app as the source
		// for this note. Don't use the example below; please arrange with the
		// Snaptic development team for the string you will use to identify your
		// app. The object should be a String.
		intent.putExtra(SnapticIntent.EXTRA_SOURCE, "Snaptic Intent Test Utility");
		
		// Optional; if EXTRA_TITLE is supplied it will appear in the
		// titlebar of the note editor activity in 3banana. The object should be
		// a String.
		intent.putExtra(Intent.EXTRA_TITLE, "Intent Testing");

		// Optional: include an image. Image URIs should point to JPEG images,
		// accessible to external packages (i.e., don't point to content private
		// to your application). The object should be a Uri.
		if (imageUri != null) {
			intent.putExtra(Intent.EXTRA_STREAM, imageUri);
		}
		
		// Optional: include a location. The object should be a Location.
		if (location != null) {
			intent.putExtra(SnapticIntent.EXTRA_LOCATION, location);		
		}
		
		// Optional: specify a cursor position for the editor. The type should
		// be an int.
		if (cursorPosition >= 0) {
			intent.putExtra(SnapticIntent.EXTRA_CURSOR_POSITION, cursorPosition);
		}
		
		// Optional: specify autosave. Intents with autosave set will send the
		// note and its contents, save it immediately, and return to your
		// activity. You may want to provide feedback to your users that the
		// action completed. The type should be a boolean.
		if (autoSave) {
			intent.putExtra(SnapticIntent.EXTRA_AUTOSAVE, true);
		}

		// Start the Intent
		startNotesIntent(intent);
	}
	
	public void viewNotes(String tag) {
		// Verify that correct version of notes is installed
		if (!isNotesInstalled()) {
			return;
		}

		// Prefix with hash if necessary
		if (!tag.startsWith("#")) {
			tag = "#" + tag;
		}
		
		// Create the Intent		
		Intent intent = new Intent();
		intent.setAction(SnapticIntent.ACTION_VIEW);
		intent.putExtra(SnapticIntent.EXTRA_VIEW_FILTER, tag);

		// Start the Intent
		startNotesIntent(intent);
	}	
	
	private boolean isNotesInstalled() {
		// Verify that correct version of notes is installed
		try {
			PackageInfo packageInfo = _context.getPackageManager().getPackageInfo(NOTES_PACKAGE_NAME, PackageManager.GET_ACTIVITIES);
			
			if (packageInfo.versionCode < NOTES_MIN_VERSION_CODE) {
				displayUpgradeDialog(packageInfo.applicationInfo.name);
				return false;
			}			
		} catch (NameNotFoundException e) {
			displayInstallDialog();
			return false;
		}
		
		return true;
	}
		
	private void displayInstallDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(_context);
		
		builder.setTitle(R.string.install_notes_title);
		builder.setMessage(R.string.install_notes_message);
		builder.setIcon(R.drawable.market_icon);
		
		builder.setNegativeButton(R.string.cancel_button, null);
		
		builder.setPositiveButton(R.string.install_button, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				displayNotesMarketPage();
			}
		});
		
		builder.show();				
	}
	
	private void displayUpgradeDialog(String appName) {
		AlertDialog.Builder builder = new AlertDialog.Builder(_context);
		
		builder.setTitle(R.string.upgrade_notes_title);
		builder.setMessage(R.string.upgrade_notes_message);
		builder.setIcon(R.drawable.market_icon);
		
		builder.setNegativeButton(R.string.cancel_button, null);
		
		builder.setPositiveButton(R.string.upgrade_button, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				displayNotesMarketPage();
			}
		});
		
		builder.show();		
	}
	
	private void displayNotesMarketPage() {
		try {
			Uri uri = Uri.parse(NOTES_MARKET_URI);
			Intent intent = new Intent(Intent.ACTION_VIEW, uri);
			_context.startActivity(intent);
		} catch (ActivityNotFoundException e) {
			e.printStackTrace();
			displayError(R.string.market_error_message);
		}
	}
	
	private void displayError(int messageId) {
        new AlertDialog.Builder(_context)
	        .setIcon(android.R.drawable.ic_dialog_alert)
	        .setTitle(R.string.error_dialog_title)
	        .setMessage(messageId)
	        .setPositiveButton(_context.getString(R.string.ok_button), null)
	        .show();			
	}	
	
	private void startNotesIntent(Intent intent) {		
		// Start the Intent
		try {
			_context.startActivity(intent);
			
			if (intent.hasExtra(SnapticIntent.EXTRA_AUTOSAVE)) {
				// Pop up a mesage to let your users know when a quick note has
				// been added.
	    		Toast.makeText(_context,
	    				R.string.toast_quick_note,
	    				Toast.LENGTH_SHORT).show(); 
			}
		} catch (ActivityNotFoundException e) {
			e.printStackTrace();
			displayError(R.string.notes_intent_error);
		}		
	}
}
