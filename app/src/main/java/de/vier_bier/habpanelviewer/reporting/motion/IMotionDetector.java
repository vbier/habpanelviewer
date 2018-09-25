package de.vier_bier.habpanelviewer.reporting.motion;

import android.content.SharedPreferences;

/**
 * Interface for motion detectors capabilities for the MainActivity
 */
public interface IMotionDetector {
    void terminate();

    void updateFromPreferences(SharedPreferences prefs);

    Camera getCamera();
}
