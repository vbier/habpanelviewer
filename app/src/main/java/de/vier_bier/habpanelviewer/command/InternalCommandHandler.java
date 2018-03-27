package de.vier_bier.habpanelviewer.command;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.preference.PreferenceManager;

import com.jakewharton.processphoenix.ProcessPhoenix;

import java.io.ByteArrayOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.vier_bier.habpanelviewer.MainActivity;
import de.vier_bier.habpanelviewer.ScreenCapturer;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.reporting.motion.IMotionDetector;

/**
 * Handler for RESTART, UPDATE_ITEMS, ENABLE_MOTION_DETECTION, DISABLE_MOTION_DETECTION, START_APP,
 * CAPTURE_SCREEN commands.
 */
public class InternalCommandHandler implements ICommandHandler {
    private final Pattern START_PATTERN = Pattern.compile("START_APP (.+)");
    private final Pattern CAPTURE_PATTERN = Pattern.compile("CAPTURE_SCREEN (.+)");

    private final MainActivity mActivity;
    private final ServerConnection mConnection;
    private final IMotionDetector mMotionDetector;

    public InternalCommandHandler(MainActivity mainActivity, IMotionDetector motionDetector, ServerConnection connection) {
        mActivity = mainActivity;
        mMotionDetector = motionDetector;
        mConnection = connection;
    }

    @Override
    public boolean handleCommand(String cmd) {
        String parameter;

        if ("RESTART".equals(cmd)) {
            mActivity.destroy();
            ProcessPhoenix.triggerRebirth(mActivity);
        } else if ("UPDATE_ITEMS".equals(cmd)) {
            mConnection.sendCurrentValues();
        } else if ("ENABLE_MOTION_DETECTION".equals(cmd)) {
            setMotionDetectionEnabled(true);
        } else if ("DISABLE_MOTION_DETECTION".equals(cmd)) {
            setMotionDetectionEnabled(false);
        } else if ((parameter = matchesRegexp(CAPTURE_PATTERN, cmd)) != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ScreenCapturer c = mActivity.getCapturer();
                if (c == null) {
                    throw new IllegalArgumentException("Could not create capturer. Has the permission been granted?");
                }

                Bitmap bmp = c.captureScreen();
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, os);

                byte[] bytes = os.toByteArray();

                mConnection.updateJpeg(parameter, bytes);
            } else {
                throw new IllegalArgumentException("Lollipop or newer needed to capture the screen");
            }
        } else if ((parameter = matchesRegexp(START_PATTERN, cmd)) != null) {
            Intent launchIntent = mActivity.getPackageManager().getLaunchIntentForPackage(parameter);

            if (launchIntent != null) {
                mActivity.startActivity(launchIntent);
            } else {
                throw new IllegalArgumentException("Could not find app for package " + parameter);
            }
        } else {
            return false;
        }

        return true;
    }

    private void setMotionDetectionEnabled(boolean enabled) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);

        SharedPreferences.Editor editor1 = prefs.edit();
        editor1.putBoolean("pref_motion_detection_enabled", enabled);
        editor1.apply();

        mActivity.runOnUiThread(() -> mActivity.updateMotionPreferences());
    }

    private String matchesRegexp(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);

        if (m.matches()) {
            return m.group(1);
        }

        return null;
    }
}
