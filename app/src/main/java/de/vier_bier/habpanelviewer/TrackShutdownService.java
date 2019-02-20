package de.vier_bier.habpanelviewer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class TrackShutdownService extends Service {
    private static final String TAG = "HPV-TrackShutdownServ";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service Started");
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service Destroyed");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.e(TAG, "END");
        stopSelf();
    }
}
