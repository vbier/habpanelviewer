package de.vier_bier.habpanelviewer.reporting.motion;

import android.content.SharedPreferences;

/**
 * Interface for motion detectors capabilities for the MainActivity
 */
public interface IMotionDetector {
    int MY_PERMISSIONS_MOTION_REQUEST_CAMERA = 42;

    void terminate();

    void updateFromPreferences(SharedPreferences prefs);

    Camera getCamera();
}
