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

import java.util.ArrayList;

import com.tedx.alcatraz.AboutActivity;
import com.tedx.alcatraz.R;
import com.tedx.alcatraz.ScheduleActivity;
import com.tedx.alcatraz.SpeakerResultActivity;
import com.tedx.utility.IntentIntegrator;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;

public class Main extends Activity {
    /** Called when the activity is first created. */
	private IntentIntegrator _notesIntent;
	private static final int MENU_CONTACT = 1;
	private static final int MENU_FACEBOOK = 2;
	private static final int MENU_TWITTER = 3;

    @Override
    public void onCreate(Bundle savedInstanceState) {
    	requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        _notesIntent = new IntentIntegrator(this);

    }
    
    //Loading About
    public void btnabout_Click(View target){
    	Intent intent = new Intent(this, AboutActivity.class);
		this.startActivity(intent);
    }
    
    //Loading Speaker List
    public void btnspeakers_Click(View target){
    	Intent intent = new Intent(this, SpeakerResultActivity.class);
		this.startActivity(intent);
    }
    
    //Loading Contact (Email)
    public void btncontact_Click(View target){
    	String emailaddress = this.getString(R.string.email_subject);
    	String emailsubject = this.getString(R.string.email_subject);

    	final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
    	emailIntent.setType("plain/text");
    	emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{emailaddress});
    	emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, emailsubject);    	
    	startActivity(Intent.createChooser(emailIntent, "Send mail..."));
    }
    
    //Loading Schedule
    public void btnschedule_Click(View target){
    	Intent intent = new Intent(this, ScheduleActivity.class);
		this.startActivity(intent);
    }
    
    //Loading Facebook
    public void btnfacebook_Click(View target){

    	String facebookurl = this.getString(R.string.facebookurl);
		Uri uri = Uri.parse(facebookurl);
		Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		this.startActivity(intent);    
    }
    
    //Loading Facebook
    public void btntwitter_Click(View target){

    	String facebookurl = this.getString(R.string.twitterurl);
		Uri uri = Uri.parse(facebookurl);
		Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		this.startActivity(intent);    
    }
    
    
    //Loading Attend
    public void btnattend_Click(View target){
    	String registerurl = this.getString(R.string.registerurl);

		Uri uri = Uri.parse(registerurl);
		Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		this.startActivity(intent);
    }
    
    //Loading Note
    public void btnnote_Click(View target)
    {
    	String notetag = this.getString(R.string.notetag);
		_notesIntent.createNote(notetag);
    }
    
    //Loading Map
    public void btnmap_Click(View target)
    {

    	Intent intent = new Intent(this, EventMapActivity.class);

	    ArrayList<String> Latitude = new ArrayList<String>();
	    Latitude.add("37.787835");
	    ArrayList<String> Longitude = new ArrayList<String>();
	    Longitude.add("-122.397067");
	    ArrayList<String> Name = new ArrayList<String>();
	    Name.add("Temple Nightclub");
	    ArrayList<String> Description = new ArrayList<String>();
	    Description.add("540 Howard Street, San Francisco, California");
	    
	    Bundle locationBundle = new Bundle();
	    locationBundle.putStringArrayList("Latitude", Latitude);
	    locationBundle.putStringArrayList("Longitude", Longitude);
	    locationBundle.putStringArrayList("Name", Name);
	    locationBundle.putStringArrayList("Description", Description);

	    intent.putExtra("Location", locationBundle);
		this.startActivity(intent);
    }
    
    public void btnscan_Click(View target)
    {
    	try
    	{
            Intent intent = new Intent("com.google.zxing.client.android.SCAN");
            intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
            startActivityForResult(intent, 0);
            //startActivity(intent);	
    	}
    	catch (ActivityNotFoundException e) 
    	{
    	    AlertDialog.Builder downloadDialog = new AlertDialog.Builder(this);
    	    downloadDialog.setTitle("Barcode Scanner needed");
    	    downloadDialog.setMessage("You need Barcode Scanner to scan others" );
    	    downloadDialog.setPositiveButton("Download", new DialogInterface.OnClickListener() {
    	      public void onClick(DialogInterface dialogInterface, int i) {
    	        Uri uri = Uri.parse("market://search?q=pname:com.google.zxing.client.android");
    	        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
    	        Main.this.startActivity(intent);
    	      }
    	    });
    	    downloadDialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
    	      public void onClick(DialogInterface dialogInterface, int i) {}
    	    });
    	    downloadDialog.show();
    	}
    }
    
    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == 0) {
            if (resultCode == RESULT_OK) {
                String contents = intent.getStringExtra("SCAN_RESULT");
                //String format = intent.getStringExtra("SCAN_RESULT_FORMAT");
                
            	Intent attendee = new Intent(this, AttendeeActivity.class);
            	attendee.putExtra("EventUniqueId", contents);
        		this.startActivity(attendee);
                // Handle successful scan
            } else if (resultCode == RESULT_CANCELED) {
                // Handle cancel
            }
        }
        else
        {
        	super.onActivityResult(requestCode, resultCode, intent);
        }
	}
    
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {		
		// sets the search menu button key

		menu.add(0, MENU_CONTACT, 0, "Contact")
		.setIcon(R.drawable.mail_menu)
		.setAlphabeticShortcut(SearchManager.MENU_KEY);
		
		menu.add(0, MENU_FACEBOOK, 0, "Facebook")
		.setIcon(R.drawable.facebook_menu)
		.setAlphabeticShortcut(SearchManager.MENU_KEY);	        
	        
		menu.add(0, MENU_TWITTER, 0, "Twitter")
		.setIcon(R.drawable.twitter_menu)
		.setAlphabeticShortcut(SearchManager.MENU_KEY);

		return super.onCreateOptionsMenu(menu);
	}
    
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {			
			case MENU_CONTACT:
				btncontact_Click(null);
				return true;
			case MENU_FACEBOOK:
				btnfacebook_Click(null);
				return true;
			case MENU_TWITTER:
				btntwitter_Click(null);
				return true;
		}
		return super.onOptionsItemSelected(item);
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