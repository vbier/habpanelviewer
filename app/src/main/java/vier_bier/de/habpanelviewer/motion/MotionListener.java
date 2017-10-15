package vier_bier.de.habpanelviewer.motion;

/**
 * Interface for being notified about motion events.
 */
public interface MotionListener {
    void motionDetected();

    void noMotion();

    void tooDark();
}
