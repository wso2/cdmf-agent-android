package org.wso2.iot.agent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;

import org.wso2.iot.agent.utils.Constants;
import org.wso2.iot.agent.utils.Preference;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class SplashActivity extends Activity {

    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;
    private static boolean isInstantiated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Constants.AUTO_ENROLLMENT_BACKGROUND_SERVICE_ENABLED || isInstantiated) {
            startActivity();
        } else {
            isInstantiated = true;
            setContentView(R.layout.activity_splash);
            View decorView = getWindow().getDecorView();
            // Hide the status bar.
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
            /* New Handler to start the AgentReceptionActivity
             * and close this Splash-Screen after some seconds.*/
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    startActivity();
                }
            }, AUTO_HIDE_DELAY_MILLIS);
        }
    }

    private void startActivity() {
        Intent intent;
        if (Preference.hasPreferenceKey(this, Constants.TOKEN_EXPIRED)) {
            intent = new Intent(getApplicationContext(), AuthenticationActivity.class);
        } else {
            intent = new Intent(getApplicationContext(), AgentReceptionActivity.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

}
