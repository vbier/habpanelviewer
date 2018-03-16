package de.vier_bier.habpanelviewer.reporting.motion;

import android.graphics.Point;

import java.util.ArrayList;

/**
 * Interface for being notified about motion events.
 */
public interface IMotionListener {
    void motionDetected(ArrayList<Point> differing);

    void noMotion();

    void tooDark();

    class MotionAdapter implements IMotionListener {
        @Override
        public void motionDetected(ArrayList<Point> differing) {
        }

        @Override
        public void noMotion() {
        }

        @Override
        public void tooDark() {
        }
    }
}
