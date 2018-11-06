package de.vier_bier.habpanelviewer.reporting.motion;

/**
 * Camera related exception independent on Android API level.
 */
class CameraException extends Exception {
    CameraException(String s) {
        super(s);
    }

    CameraException(String s, Exception e) {
        super(s, e);
    }

    CameraException(Exception e) {
        super(e);
    }
}
