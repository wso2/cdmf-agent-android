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
import java.util.List;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;

import com.tedx.alcatraz.R;

//This portion is temporary here, it will be changed pull data from the server
public class EventMapActivity extends MapActivity {
	MapView mapView;
	
	List<Overlay> mapOverlays;
	Drawable drawable;
	HelloItemizedOverlay itemizedOverlay;
	
    ArrayList<String> Latitude = new ArrayList<String>();
    ArrayList<String> Longitude = new ArrayList<String>();
    ArrayList<String> Name = new ArrayList<String>();
	ArrayList<String> Description = new ArrayList<String>();
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.map);
        
        Intent intent = getIntent();
        Bundle LocationBundle = intent.getExtras().getBundle("Location");
        if(LocationBundle != null)
        {
        	Latitude = LocationBundle.getStringArrayList("Latitude");
        	Longitude = LocationBundle.getStringArrayList("Longitude");
        	Name = LocationBundle.getStringArrayList("Name");
        	Description = LocationBundle.getStringArrayList("Description");
        }
        else
        {
        	finish();
        }
        
        mapView = (MapView) findViewById(R.id.mapview);
        mapView.setBuiltInZoomControls(true);
        mapView.setSatellite(false);
        
        mapOverlays = mapView.getOverlays();
        //drawable = this.getResources().getDrawable(R.drawable.androidmarker);
        //itemizedOverlay = new HelloItemizedOverlay(drawable);
        LoadMap();
    }
		
	
	public void LoadMap()
	{
		int size = Longitude.size();
		for(int i = 0; i < size; i++)
		{
			
			int tmpLat = (int)(Float.valueOf(Latitude.get(i)) * 1000000);
			int tmpLong = (int)(Float.valueOf(Longitude.get(i)) * 1000000);
			GeoPoint point = new GeoPoint(tmpLat, tmpLong);
			OverlayItem overlayitem = new OverlayItem(point, Name.get(i), Description.get(i));

			//itemizedOverlay.addOverlay(overlayitem);
			
			HelloItemizedOverlay temp = new HelloItemizedOverlay(this.getResources().getDrawable(R.drawable.pin));
			temp.addOverlay(overlayitem);
			mapOverlays.add(temp);	
			
			if(i == 1)
			{
				mapView.getController().setZoom(16);
				mapView.getController().setCenter(point);
			}
		}
	}
	
	@Override
	protected boolean isRouteDisplayed() {
	    return false;
	}

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
    
    private class HelloItemizedOverlay extends ItemizedOverlay {

    	private ArrayList<OverlayItem> mOverlays = new ArrayList<OverlayItem>();	
    	
    	public HelloItemizedOverlay(Drawable defaultMarker) {
    		//super(defaultMarker);
    		super(boundCenterBottom(defaultMarker));
    		// TODO Auto-generated constructor stub
    	}

    	public void addOverlay(OverlayItem overlay) {
    	    mOverlays.add(overlay);
    	    populate();
    	}
    	
    	@Override
    	protected OverlayItem createItem(int i) {
    	  return mOverlays.get(i);
    	}

    	@Override
    	public int size() {
    		// TODO Auto-generated method stub
    		return mOverlays.size();
    	}
    	
    	 @Override
    	 protected boolean onTap(int i) {    		 
    		 //final int position = Integer.valueOf(mOverlays.get(i).getSnippet());
    		 Drawable picture = EventMapActivity.this.getResources().getDrawable(R.drawable.pin);
    		 
             AlertDialog alert = new AlertDialog.Builder(EventMapActivity.this)
             .setIcon(picture)
             .setTitle(mOverlays.get(i).getTitle())
             .setMessage(mOverlays.get(i).getSnippet())
             .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                 public void onClick(DialogInterface dialog, int whichButton) {
 
                     /* User clicked OK so do some stuff */
                 }
             })
             .setNeutralButton("Directions", new DialogInterface.OnClickListener() {
                 public void onClick(DialogInterface dialog, int whichButton) {
                	 String uri = "google.navigation:q=" 
                		 + EventMapActivity.this.getResources().getString(R.string.event_address);
                	 startActivity(new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri)));  
                 }
             })
             .create();
    		 
    		 alert.show();
    		 return(true);
    	 }
    }
}
