package de.vier_bier.habpanelviewer.reporting.motion;

/**
 * Camera related exception independent on Android API level.
 */
public class CameraException extends Exception {
    CameraException(String s) {
        super(s);
    }

    CameraException(Exception e) {
        super(e);
    }
}
