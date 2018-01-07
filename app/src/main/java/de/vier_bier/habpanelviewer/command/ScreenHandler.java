package de.vier_bier.habpanelviewer.command;

import android.app.Activity;
import android.os.PowerManager;
import android.util.Log;

import de.vier_bier.habpanelviewer.R;

/**
 * Handler for SCREEN_ON, KEEP_SCREEN_ON and ALLOW_SCREEN_OFF commands.
 */
public class ScreenHandler implements CommandHandler {
    private final PowerManager.WakeLock screenOnLock;
    private final Activity mActivity;

    public ScreenHandler(PowerManager pwrManager, Activity activity) {
        mActivity = activity;

        screenOnLock = pwrManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "HabpanelViewer");

        setKeepScreenOn(false);
    }

    @Override
    public boolean handleCommand(String cmd) {
        if ("SCREEN_ON".equals(cmd)) {
            screenOn();
        } else if ("ALLOW_SCREEN_OFF".equals(cmd)) {
            setKeepScreenOn(false);
        } else if ("KEEP_SCREEN_ON".equals(cmd)) {
            screenOn();
            setKeepScreenOn(true);
        } else {
            return false;
        }

        return true;
    }

    public void setKeepScreenOn(final boolean on) {
        Log.v("ScreenController", "setKeepScreenOn: on=" + on);

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.findViewById(R.id.activity_main_webview).setKeepScreenOn(on);
            }
        });
    }

    private synchronized void screenOn() {
        if (!screenOnLock.isHeld()) {
            screenOnLock.acquire();
            screenOnLock.release();
        }
    }
}
