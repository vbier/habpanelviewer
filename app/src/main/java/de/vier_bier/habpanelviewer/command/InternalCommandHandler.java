package de.vier_bier.habpanelviewer.command;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import com.jakewharton.processphoenix.ProcessPhoenix;

import java.io.ByteArrayOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.vier_bier.habpanelviewer.MainActivity;
import de.vier_bier.habpanelviewer.ScreenCapturer;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.reporting.motion.CameraException;
import de.vier_bier.habpanelviewer.reporting.motion.ICamera;
import de.vier_bier.habpanelviewer.reporting.motion.IMotionDetector;

/**
 * Handler for RESTART, UPDATE_ITEMS, ENABLE_MOTION_DETECTION, DISABLE_MOTION_DETECTION, START_APP,
 * CAPTURE_SCREEN commands.
 */
public class InternalCommandHandler implements ICommandHandler {
    private final Pattern START_PATTERN = Pattern.compile("START_APP (.+)");
    private final Pattern CAPTURE_SCREEN_PATTERN = Pattern.compile("CAPTURE_SCREEN (.+)");
    private final Pattern CAPTURE_CAMERA_PATTERN = Pattern.compile("CAPTURE_CAMERA (.+)");

    private final MainActivity mActivity;
    private final ServerConnection mConnection;
    private final IMotionDetector mMotionDetector;
    private int takePictureDelay;

    public InternalCommandHandler(MainActivity mainActivity,
                                  IMotionDetector motionDetector,
                                  ServerConnection connection) {
        mActivity = mainActivity;
        mMotionDetector = motionDetector;
        mConnection = connection;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
        try {
            takePictureDelay = Integer.parseInt(prefs.getString("pref_take_pic_delay", "100"));
        } catch (NumberFormatException e) {
            takePictureDelay = 100;
            Log.e("Habpanelview", "could not parse pref_take_pic_delay value " + prefs.getString("pref_take_pic_delay", "100") + ". using default 100");
        }
    }

    @Override
    public boolean handleCommand(Command cmd) {
        final String cmdStr = cmd.getCommand();

        String parameter;

        if ("RESTART".equals(cmdStr)) {
            cmd.start();
            mActivity.destroy();
            ProcessPhoenix.triggerRebirth(mActivity);
        } else if ("UPDATE_ITEMS".equals(cmdStr)) {
            cmd.start();
            mConnection.sendCurrentValues();
        } else if ("ENABLE_MOTION_DETECTION".equals(cmdStr)) {
            cmd.start();
            setMotionDetectionEnabled(true);
        } else if ("DISABLE_MOTION_DETECTION".equals(cmdStr)) {
            cmd.start();
            setMotionDetectionEnabled(false);
        } else if ((parameter = matchesRegexp(CAPTURE_SCREEN_PATTERN, cmdStr)) != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ScreenCapturer c = mActivity.getCapturer();
                if (c == null) {
                    cmd.failed("could not create capture class. Has the permission been granted?");
                } else {
                    cmd.start();
                    Bitmap bmp = c.captureScreen();
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, os);

                    byte[] bytes = os.toByteArray();

                    mConnection.updateJpeg(parameter, bytes);
                }
            } else {
                cmd.failed("Lollipop or newer needed to capture the screen");
            }
        } else if ((parameter = matchesRegexp(CAPTURE_CAMERA_PATTERN, cmdStr)) != null) {
            cmd.start();
            try {
                final String p = parameter;
                mMotionDetector.getCamera().takePicture(new ICamera.IPictureListener() {
                    @Override
                    public void picture(byte[] data) {
                        mConnection.updateJpeg(p, data);
                        cmd.finished();
                    }

                    @Override
                    public void error(String message) {
                        cmd.failed(message);
                    }

                    @Override
                    public void progress(String message) {
                        cmd.progress(message);
                    }
                }, takePictureDelay);
            } catch (CameraException e) {
                cmd.failed(e.getLocalizedMessage());
            }
            return true;
        } else if ((parameter = matchesRegexp(START_PATTERN, cmdStr)) != null) {
            Intent launchIntent = mActivity.getPackageManager().getLaunchIntentForPackage(parameter);

            if (launchIntent != null) {
                cmd.start();
                mActivity.startActivity(launchIntent);
            } else {
                cmd.failed("could not find app for package " + parameter);
            }
        } else {
            return false;
        }

        cmd.finished();
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
