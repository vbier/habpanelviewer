package vier_bier.de.habpanelviewer.motion;

/**
 * Camera related exception independent on Android API level.
 */
public class CameraException extends Exception {
    public CameraException(String s) {
        super(s);
    }

    public CameraException(Exception e) {
        super(e);
    }
}
