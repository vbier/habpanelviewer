package vier_bier.de.habpanelviewer.motion;

import android.graphics.Point;

import java.util.ArrayList;

/**
 * Interface for being notified about motion events.
 */
public interface MotionListener {
    void motionDetected(ArrayList<Point> differing);

    void noMotion();

    void tooDark();
}
