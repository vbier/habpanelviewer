package vier_bier.de.habpanelviewer.motion;

import android.content.SharedPreferences;

/**
 * Interface for motion detectors capabilities for the MainActivity
 */
public interface IMotionDetector {
    int MY_PERMISSIONS_MOTION_REQUEST_CAMERA = 42;

    void shutdown();

    void updateFromPreferences(SharedPreferences prefs);
}
