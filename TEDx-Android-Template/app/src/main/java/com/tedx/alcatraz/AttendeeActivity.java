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

import com.tedx.logics.AttendeeLogic;
import com.tedx.utility.IntentIntegrator;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.accounts.OnAccountsUpdateListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

public class AttendeeActivity extends Activity implements OnAccountsUpdateListener{
	private IntentIntegrator _notesIntent;
    public static final String TAG = "ContactsAdder";
    public static final String ACCOUNT_NAME =
            "com.example.android.contactmanager.ContactsAdder.ACCOUNT_NAME";
    public static final String ACCOUNT_TYPE =
            "com.example.android.contactmanager.ContactsAdder.ACCOUNT_TYPE";
	
    private ArrayList<AccountData> mAccounts;
    private AccountAdapter mAccountAdapter;
    private Spinner mAccountSpinner;
    private Button btnaddcontact;
    private Button btnaddcontact_blackwhite;
    private Button btnfacebook;
    private Button btntwitter;
    private TextView txtName;
    private TextView txtDescription;
    private AccountData mSelectedAccount;
    private String EventUniqueId;
    
    Bundle AttendeeInfo;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.attendee);
        
        //Find Event Unique Id from scanner
        EventUniqueId = this.getIntent().getExtras().getString("EventUniqueId");
        AttendeeInfo = AttendeeLogic.GetCurrentDancers(this.getResources(), EventUniqueId);
        if(AttendeeInfo == null)
        {
	        //Condition
	        AlertDialog alert = new AlertDialog.Builder(AttendeeActivity.this)
	         .setTitle("Can't find this person")
	         .setMessage("Sorry, we were unable to find this attendee, please take his information manually.")
	         .setPositiveButton("OK", new DialogInterface.OnClickListener() {
	             public void onClick(DialogInterface dialog, int whichButton) {
	            	 finish();
	             }
	         })
	         .create();
	         
	         alert.show();
        }
        else
        {
	        //setting up values
	        btnaddcontact = (Button) findViewById(R.id.btnaddcontact);
	        btnaddcontact_blackwhite = (Button) findViewById(R.id.btnaddcontact_blackwhite);
	        btnfacebook = (Button) findViewById(R.id.btnfacebook);
	        btntwitter = (Button) findViewById(R.id.btntwitter);
	        txtName = (TextView)findViewById(R.id.txtName);
	        txtDescription = (TextView)findViewById(R.id.txtDescription);
	        
	        if(!AttendeeInfo.getString("Facebook").equalsIgnoreCase(null) && !AttendeeInfo.getString("Facebook").equalsIgnoreCase(""))
	        {
	        	btnfacebook.setVisibility(Button.VISIBLE);
	        }
	        if(!AttendeeInfo.getString("Twitter").equalsIgnoreCase(null) && !AttendeeInfo.getString("Twitter").equalsIgnoreCase(""))
	        {
	        	btntwitter.setVisibility(Button.VISIBLE);
	        }
	        
	        txtName.setText(AttendeeInfo.getString("FirstName") + " " + AttendeeInfo.getString("LastName"));
	        txtDescription.setText(AttendeeInfo.getString("Description"));
        }
        
        mAccountSpinner = (Spinner) findViewById(R.id.accountSpinner);

        // Prepare model for account spinner
        mAccounts = new ArrayList<AccountData>();
        mAccountAdapter = new AccountAdapter(this, mAccounts);
        mAccountSpinner.setAdapter(mAccountAdapter);

        // Prepares Catch Note
        _notesIntent = new IntentIntegrator(this);

        // Prepare the system account manager. On registering the listener below, we also ask for
        // an initial callback to pre-populate the account list.
        AccountManager.get(this).addOnAccountsUpdatedListener(this, null, true);

        // Register handlers for UI elements
        mAccountSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long i) {
                updateAccountSelection();
            }

            public void onNothingSelected(AdapterView<?> parent) {
                // We don't need to worry about nothing being selected, since Spinners don't allow
                // this.
            }
        });
    }
    
    public void btnaddcontact_Click(View target)
    {	
        // Get values from UI
        String name = AttendeeInfo.getString("FirstName") + " " + AttendeeInfo.getString("LastName");
        String phone = AttendeeInfo.getString("ContactNumber");
        String email = AttendeeInfo.getString("Email");
        int phoneType = Phone.TYPE_MOBILE;
        int emailType = Email.TYPE_WORK;

        // Prepare contact creation request
        //
        // Note: We use RawContacts because this data must be associated with a particular account.
        //       The system will aggregate this with any other data for this contact and create a
        //       coresponding entry in the ContactsContract.Contacts provider for us.
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, mSelectedAccount.getType())
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, mSelectedAccount.getName())
                .build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build());
        
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, phoneType)
                .build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Email.DATA, email)
                .withValue(ContactsContract.CommonDataKinds.Email.TYPE, emailType)
                .build());

        // Ask the Contact provider to create a new contact
        //Log.i(TAG,"Selected account: " + mSelectedAccount.getName() + " (" + mSelectedAccount.getType() + ")");
        Log.i(TAG,"Creating contact: " + name);
        try {
            getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            btnaddcontact.setEnabled(false);
            btnaddcontact.setClickable(false);
            
            btnaddcontact_blackwhite.setVisibility(Button.VISIBLE);
            btnaddcontact.setVisibility(Button.GONE);
            
            Context ctx = getApplicationContext();
            CharSequence txt = name + " has been added to your contact list";
            int duration = Toast.LENGTH_LONG;
            Toast toast = Toast.makeText(ctx, txt, duration);
            toast.show();
        } catch (Exception e) {
            // Display warning
            Context ctx = getApplicationContext();
            CharSequence txt = "Failed to add to your contact";
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(ctx, txt, duration);
            toast.show();

            // Log exception
            Log.e(TAG, "Exceptoin encoutered while inserting contact: " + e);
        }    }
    
    public void btnnote_Click(View target)
    {
    	String notetag =	AttendeeInfo.getString("FirstName") + " " + AttendeeInfo.getString("LastName") +
    						"\nContact Number: " + AttendeeInfo.getString("ContactNumber") +
    						"\nEmail: " + AttendeeInfo.getString("Email") + 
    						"\nWebsite: " + AttendeeInfo.getString("Website") + 
    						"\nFacebook: " + AttendeeInfo.getString("Facebook") + 
    						"\nTwitter: " + AttendeeInfo.getString("Twitter") +
    						"\n\n#contact\n\n" +
    						this.getString(R.string.notetag);
		_notesIntent.createNote(notetag);
    }
    
    public void btnfacebook_Click(View target)
    {
    	String facebookurl = this.getString(R.string.attendeefacebook);
		Uri uri = Uri.parse(facebookurl + AttendeeInfo.getString("Facebook"));
		Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		this.startActivity(intent);    
    }
    
    public void btntwitter_Click(View target)
    {
    	String twitterurl = this.getString(R.string.attendeetwitter);
		Uri uri = Uri.parse(twitterurl + AttendeeInfo.getString("Twitter"));
		Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		this.startActivity(intent);    
    }


    /**
     * Called when this activity is about to be destroyed by the system.
     */
    @Override
    public void onDestroy() {
        // Remove AccountManager callback
        AccountManager.get(this).removeOnAccountsUpdatedListener(this);
        super.onDestroy();
    }

    /**
     * Updates account list spinner when the list of Accounts on the system changes. Satisfies
     * OnAccountsUpdateListener implementation.
     */
    public void onAccountsUpdated(Account[] a) {
        Log.i(TAG, "Account list update detected");
        // Clear out any old data to prevent duplicates
        mAccounts.clear();

        // Get account data from system
        AuthenticatorDescription[] accountTypes = AccountManager.get(this).getAuthenticatorTypes();

        // Populate tables
        for (int i = 0; i < a.length; i++) {
            // The user may have multiple accounts with the same name, so we need to construct a
            // meaningful display name for each.
            String systemAccountType = a[i].type;
            AuthenticatorDescription ad = getAuthenticatorDescription(systemAccountType,
                    accountTypes);
            AccountData data = new AccountData(a[i].name, ad);
            mAccounts.add(data);
        }

        // Update the account spinner
        mAccountAdapter.notifyDataSetChanged();
    }

    /**
     * Obtain the AuthenticatorDescription for a given account type.
     * @param type The account type to locate.
     * @param dictionary An array of AuthenticatorDescriptions, as returned by AccountManager.
     * @return The description for the specified account type.
     */
    private static AuthenticatorDescription getAuthenticatorDescription(String type,
            AuthenticatorDescription[] dictionary) {
        for (int i = 0; i < dictionary.length; i++) {
            if (dictionary[i].type.equals(type)) {
                return dictionary[i];
            }
        }
        // No match found
        throw new RuntimeException("Unable to find matching authenticator");
    }

    /**
     * Update account selection. If NO_ACCOUNT is selected, then we prohibit inserting new contacts.
     */
    private void updateAccountSelection() {
        // Read current account selection
        mSelectedAccount = (AccountData) mAccountSpinner.getSelectedItem();
    }

    /**
     * A container class used to repreresent all known information about an account.
     */
    private class AccountData {
        private String mName;
        private String mType;
        private CharSequence mTypeLabel;
        private Drawable mIcon;

        /**
         * @param name The name of the account. This is usually the user's email address or
         *        username.
         * @param description The description for this account. This will be dictated by the
         *        type of account returned, and can be obtained from the system AccountManager.
         */
        public AccountData(String name, AuthenticatorDescription description) {
            mName = name;
            if (description != null) {
                mType = description.type;

                // The type string is stored in a resource, so we need to convert it into something
                // human readable.
                String packageName = description.packageName;
                PackageManager pm = getPackageManager();

                if (description.labelId != 0) {
                    mTypeLabel = pm.getText(packageName, description.labelId, null);
                    if (mTypeLabel == null) {
                        throw new IllegalArgumentException("LabelID provided, but label not found");
                    }
                } else {
                    mTypeLabel = "";
                }

                if (description.iconId != 0) {
                    mIcon = pm.getDrawable(packageName, description.iconId, null);
                    if (mIcon == null) {
                        throw new IllegalArgumentException("IconID provided, but drawable not " +
                                "found");
                    }
                } else {
                    mIcon = getResources().getDrawable(android.R.drawable.sym_def_app_icon);
                }
            }
        }

        public String getName() {
            return mName;
        }

        public String getType() {
            return mType;
        }

        public CharSequence getTypeLabel() {
            return mTypeLabel;
        }

        public Drawable getIcon() {
            return mIcon;
        }

        public String toString() {
            return mName;
        }
    }

    /**
     * Custom adapter used to display account icons and descriptions in the account spinner.
     */
    private class AccountAdapter extends ArrayAdapter<AccountData> {
        public AccountAdapter(Context context, ArrayList<AccountData> accountData) {
            super(context, android.R.layout.simple_spinner_item, accountData);
            setDropDownViewResource(R.layout.account_entry);
        }

        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            // Inflate a view template
            if (convertView == null) {
                LayoutInflater layoutInflater = getLayoutInflater();
                convertView = layoutInflater.inflate(R.layout.account_entry, parent, false);
            }
            TextView firstAccountLine = (TextView) convertView.findViewById(R.id.firstAccountLine);
            TextView secondAccountLine = (TextView) convertView.findViewById(R.id.secondAccountLine);
            ImageView accountIcon = (ImageView) convertView.findViewById(R.id.accountIcon);

            // Populate template
            AccountData data = getItem(position);
            firstAccountLine.setText(data.getName());
            secondAccountLine.setText(data.getTypeLabel());
            Drawable icon = data.getIcon();
            if (icon == null) {
                icon = getResources().getDrawable(android.R.drawable.ic_menu_search);
            }
            accountIcon.setImageDrawable(icon);
            return convertView;
        }
    }
}

