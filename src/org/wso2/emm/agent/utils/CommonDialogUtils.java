package org.wso2.emm.agent.utils;

import org.wso2.emm.agent.R;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
/**
 * 
 * The CommonDialogUtils class contains the all dialog templates.
 *
 */
public abstract class CommonDialogUtils {
	
	private static ProgressDialog progressDialog;
	
	/**
	 * Return an Alert Dialog with one button.
	 * 
	 * @param context the Activity which needs this alert dialog
	 * @param message the message in the alert
	 * @param positiveBtnLabel the label of the positive button
	 * @param positiveClickListener the onClickListener of the positive button
	 * 
	 * @return the generated Alert Dialog
	 */
	public static AlertDialog.Builder getAlertDialogWithOneButton(Context context,
			String message, String positiveBtnLabel, 
			DialogInterface.OnClickListener positiveClickListener) {
		
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(message)
				.setPositiveButton(positiveBtnLabel, positiveClickListener);

		return builder;
	}

	/**
	 * Return an Alert Dialog with two buttons.
	 * 
	 * @param context 
	 * @param context the Activity which needs this alert dialog
	 * @param message the message in the alert
	 * @param positiveBtnLabel the label of the positive button
	 * @param negetiveBtnLabel the label of the negative button
	 * @param positiveClickListener the onClickListener of the positive button
	 * @param negativeClickListener the onClickListener of the negative button
	 * 
	 * @return the generated Alert Dialog.
	 */
	public static AlertDialog.Builder getAlertDialogWithTwoButton(Context context,
			String message, String positiveBtnLabel, String negetiveBtnLabel,
			DialogInterface.OnClickListener positiveClickListener,
			DialogInterface.OnClickListener negativeClickListener) {
		
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(message)
				.setPositiveButton(positiveBtnLabel, positiveClickListener)
				.setNegativeButton(negetiveBtnLabel, negativeClickListener);

		return builder;
	}
	
	/**
	 * Shows the Network unavailable message.
	 * 
	 * @param context the Activity where checking the network availability.
	 */
	public static void showNetworkUnavailableMessage(Context context) {
		AlertDialog.Builder builder = CommonDialogUtils
				.getAlertDialogWithOneButton(
						context,
						"Network connectivity is unavailable. Please check your network connectivity.",
						context.getResources().getString(R.string.button_ok), null);
		builder.show();
	}
	
	public static AlertDialog.Builder getAlertDialogWithTwoButtonAndTitle(Context context,
			String title, String message,
			String positiveBtnLabel, String negetiveBtnLabel,
			DialogInterface.OnClickListener positiveClickListener,
			DialogInterface.OnClickListener negativeClickListener) {
		
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(title);
		builder.setMessage(message)
				.setPositiveButton(positiveBtnLabel, positiveClickListener)
				.setNegativeButton(negetiveBtnLabel, negativeClickListener);

		return builder;
	}
	
	/**
	 * Shows the ProgressDialog.
	 * 
	 * @param context the Activity which needs the ProgressDialog.
	 * @param title the title
	 * @param message the message
	 * @param cancelListener the OnCancelListener
	 */
	public static void showPrgressDialog (Context context, String title, String message, OnCancelListener cancelListener) {
		progressDialog = ProgressDialog.show(context,
				context.getResources().getString(R.string.dialog_license_agreement),
				context.getResources().getString(R.string.dialog_please_wait), true);
		progressDialog.setCancelable(true);
		progressDialog.setOnCancelListener(cancelListener);
	}
	
	/**
	 * Stops progressDialog.
	 * 
	 */
	public static void stopProgressDialog() {
		if (progressDialog != null && progressDialog.isShowing()) {
			progressDialog.dismiss();
		}
	}

}
