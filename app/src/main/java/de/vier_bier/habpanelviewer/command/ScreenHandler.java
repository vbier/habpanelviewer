package de.vier_bier.habpanelviewer.command;

import android.app.Activity;
import android.content.Intent;
import android.os.PowerManager;

import de.vier_bier.habpanelviewer.EmptyActivity;
import de.vier_bier.habpanelviewer.ScreenControllingActivity;

/**
 * Handler for SCREEN_ON, KEEP_SCREEN_ON and ALLOW_SCREEN_OFF commands.
 */
public class ScreenHandler implements ICommandHandler {
    private final PowerManager.WakeLock screenOnLock;
    private final Activity mActivity;
    private boolean mKeepScreenOn;

    public ScreenHandler(PowerManager pwrManager, Activity activity) {
        mActivity = activity;
        screenOnLock = pwrManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "HabpanelViewer");

        setKeepScreenOn(false);
    }

    @Override
    public boolean handleCommand(Command cmd) {
        final String cmdStr = cmd.getCommand();

        if ("SCREEN_ON".equals(cmdStr)) {
            cmd.start();
            screenOn();
            screenDim(false);
        } else if ("ALLOW_SCREEN_OFF".equals(cmdStr)) {
            cmd.start();
            setKeepScreenOn(false);
        } else if ("KEEP_SCREEN_ON".equals(cmdStr)) {
            cmd.start();
            screenOn();
            setKeepScreenOn(true);
        } else if ("SCREEN_DIM".equals(cmdStr)) {
            cmd.start();
            screenDim(true);
        } else {
            return false;
        }

        cmd.finished();
        return true;
    }

    private void screenDim(final boolean dim) {
        Intent intent = new Intent();
        intent.setClass(mActivity, EmptyActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("dim", dim);
        intent.putExtra("keepScreenOn", mKeepScreenOn);
        mActivity.startActivityForResult(intent, 0);
    }

    private void setKeepScreenOn(final boolean on) {
        mKeepScreenOn = on;
        ScreenControllingActivity.setKeepScreenOn(mActivity, on);
    }

    private synchronized void screenOn() {
        screenOnLock.acquire(500);
    }
}
