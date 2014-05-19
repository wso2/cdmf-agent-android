package org.wso2.emm.agent.utils;

import org.wso2.emm.agent.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

public abstract class CommonDialogUtils {
	
	public static AlertDialog.Builder getAlertDialogWithOneButton(Context context,
			String message, String positiveBtnLabel, 
			DialogInterface.OnClickListener positiveClickListener) {
		
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(message)
				.setPositiveButton(positiveBtnLabel, positiveClickListener);

		return builder;
	}

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
	
	public static void showNetworkUnavailableMessage(Context context) {
		AlertDialog.Builder builder = CommonDialogUtils
				.getAlertDialogWithOneButton(
						context,
						"Network connectivity is unavailable. Please check your network connectivity.",
						context.getResources().getString(R.string.button_ok), null);
		builder.show();
	}

}
