package vier_bier.de.habpanelviewer.motion;

import android.app.Activity;
import android.content.SharedPreferences;

/**
 * Interface for motion detectors capabilities for the MainActivity
 */
public interface IMotionDetector {
    int MY_PERMISSIONS_MOTION_REQUEST_CAMERA = 42;

    boolean isEnabled();

    String getPreviewInfo();

    void shutdown();

    void updateFromPreferences(Activity context, SharedPreferences prefs);

    String getCameraInfo(Activity context);
}
