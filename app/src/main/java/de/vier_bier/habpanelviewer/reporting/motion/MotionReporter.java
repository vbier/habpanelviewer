package de.vier_bier.habpanelviewer.reporting.motion;

import android.content.SharedPreferences;
import android.graphics.Point;

import java.util.ArrayList;

import de.vier_bier.habpanelviewer.openhab.ServerConnection;

/**
 * Reports motion events to openHAB.
 */
public class MotionReporter extends IMotionListener.MotionAdapter {
    private final ServerConnection mServerConnection;
    private final IMotionListener mListener;

    private long mLastMotionTime;
    private boolean mMotion;

    private String mMotionItem;

    MotionReporter(IMotionListener l, ServerConnection serverConnection) {
        mServerConnection = serverConnection;
        mListener = l;
    }

    @Override
    public void motionDetected(ArrayList<Point> differing) {
        mListener.motionDetected(differing);

        mLastMotionTime = System.currentTimeMillis();
        if (!mMotion) {
            mMotion = true;
            mServerConnection.updateState(mMotionItem, "CLOSED");
        }
    }

    @Override
    public void noMotion() {
        mListener.noMotion();

        if (mMotion && System.currentTimeMillis() - mLastMotionTime > 60000) {
            mMotion = false;
            mServerConnection.updateState(mMotionItem, "OPEN");
        }
    }

    public void updateFromPreferences(SharedPreferences prefs) {
        if (mMotionItem == null || !mMotionItem.equalsIgnoreCase(prefs.getString("pref_motion_item", ""))) {
            mMotionItem = prefs.getString("pref_motion_item", "");
            mMotion = false;
        }

        mServerConnection.updateState(mMotionItem, mMotion ? "CLOSED" : "OPEN");
    }

    public void terminate() {
        mMotion = false;
        mServerConnection.updateState(mMotionItem, "OPEN");
    }
}
