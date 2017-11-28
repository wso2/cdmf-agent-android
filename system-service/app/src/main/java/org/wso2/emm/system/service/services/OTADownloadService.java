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
        Log.d(TAG, "Download manager reference id availability: " + isAvailabledownloadReference);
        if (isAvailabledownloadReference) {
            OTADownload otaDownload = new OTADownload(this);
            OTAServerManager otaServerManager = otaDownload.getOtaServerManager();
            otaServerManager.startDownloadUpgradePackage(otaServerManager);
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
