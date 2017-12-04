package org.wso2.emm.system.service.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.wso2.emm.system.service.R;
import org.wso2.emm.system.service.api.OTADownload;
import org.wso2.emm.system.service.api.OTAServerManager;
import org.wso2.emm.system.service.utils.Preference;

/**
 * This service will start the download monitoring thread by invoking the startDownloadUpgradePackage()
 * method after a reboot
 */

public class OTADownloadService extends Service {

    private static final String TAG = OTADownloadService.class.getName();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean isAvailabledownloadReference = Preference.getBoolean(this, this.getResources().getString(R.string.download_manager_reference_id_available));
        int downloadManagerSuccessState = Preference.getInt(this, this.getResources().getString(R.string.download_manager_success_state));
        int downloadManagerDefaultErrorCode = Preference.getInt(this, this.getResources().getString(R.string.download_manager_default_error_code));
        int downloadManagerDefaultStateInfoCode = Preference.getInt(this, this.getResources().getString(R.string.download_manager_default_state_info_code));
        boolean downloadManagerCompletionSuccessful = Preference.getBoolean(this, this.getResources().getString(R.string.download_manager_completion_successful));
        boolean verificationStartFlag = Preference.getBoolean(this, this.getResources().getString(R.string.verification_start_flag));
        boolean packageInstallationStartFlag = Preference.getBoolean(this, this.getResources().getString(R.string.package_installation_start_flag));
        Log.d(TAG, "Download manager reference id availability: " + isAvailabledownloadReference);

        if (isAvailabledownloadReference) {
            OTADownload otaDownload = new OTADownload(this);
            OTAServerManager otaServerManager = otaDownload.getOtaServerManager();
            otaServerManager.startDownloadUpgradePackage(otaServerManager);
        } else if (downloadManagerCompletionSuccessful && !verificationStartFlag) {
            OTADownload otaDownload = new OTADownload(this);
            OTAServerManager otaServerManager = otaDownload.getOtaServerManager();
            OTAServerManager.OTAStateChangeListener otaStateChangeListener = otaServerManager.getOTAStateChangeListener();
            otaStateChangeListener.onStateOrProgress(downloadManagerSuccessState,
                    downloadManagerDefaultErrorCode, null, downloadManagerDefaultStateInfoCode);
        } else if (downloadManagerCompletionSuccessful && !packageInstallationStartFlag) {
            OTADownload otaDownload = new OTADownload(this);
            OTAServerManager otaServerManager = otaDownload.getOtaServerManager();
            otaServerManager.startInstallUpgradePackage();
        } else {
            stopSelf();
        }
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
