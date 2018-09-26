package de.vier_bier.habpanelviewer.command;

import android.app.Activity;
import android.content.Intent;
import android.os.PowerManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.vier_bier.habpanelviewer.EmptyActivity;
import de.vier_bier.habpanelviewer.ScreenControllingActivity;

/**
 * Handler for SCREEN_ON, KEEP_SCREEN_ON and ALLOW_SCREEN_OFF commands.
 */
public class ScreenHandler implements ICommandHandler {
    private final Pattern SET_PATTERN = Pattern.compile("SET_BRIGHTNESS (AUTO|[01]?[0-9]?[0-9])");
    private final Pattern SCREEN_ON_PATTERN = Pattern.compile("SCREEN_ON ([0-9]+)");

    private final PowerManager.WakeLock screenOnLock;
    private final Activity mActivity;
    private boolean mKeepScreenOn;

    public ScreenHandler(PowerManager pwrManager, Activity activity) {
        mActivity = activity;
        screenOnLock = pwrManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
                        | PowerManager.ON_AFTER_RELEASE, "HPV:ScreenOnWakeLock");

        setKeepScreenOn(false);
    }

    @Override
    public boolean handleCommand(Command cmd) {
        final String cmdStr = cmd.getCommand();
        Matcher m1 = SET_PATTERN.matcher(cmdStr);
        Matcher m2 = SCREEN_ON_PATTERN.matcher(cmdStr);

        if ("SCREEN_ON".equals(cmdStr)) {
            cmd.start();
            screenOn(0);
            screenDim(false);
        } else if ("ALLOW_SCREEN_OFF".equals(cmdStr)) {
            cmd.start();
            setKeepScreenOn(false);
        } else if ("KEEP_SCREEN_ON".equals(cmdStr)) {
            cmd.start();
            screenOn(0);
            setKeepScreenOn(true);
        } else if ("SCREEN_DIM".equals(cmdStr)) {
            cmd.start();
            screenDim(true);
        } else if (m1.matches()) {
            String value = m1.group(1);

            try {
                int brightness = -100;
                if (!"AUTO".equals(value)) {
                    brightness = Integer.parseInt(value);
                }

                cmd.start();
                ScreenControllingActivity.setBrightness(mActivity, brightness / 100f);
            } catch (NumberFormatException e) {
                cmd.failed("failed to parse brightness from command");
            }
        } else if (m2.matches()) {
            String value = m2.group(1);

            try {
                int seconds = Integer.parseInt(value);
                cmd.start();
                screenOn(seconds);
            } catch (NumberFormatException e) {
                cmd.failed("failed to parse duration from command. Using 0 seconds.");
                screenOn(0);
            }

            screenDim(false);
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

    private synchronized void screenOn(int durationSeconds) {
        screenOnLock.acquire(durationSeconds == 0 ? 500 : durationSeconds * 1000);
    }
}
