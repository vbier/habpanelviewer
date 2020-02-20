package de.vier_bier.habpanelviewer.reporting.motion;

import android.content.SharedPreferences;
import android.graphics.Point;

import java.util.ArrayList;

import de.vier_bier.habpanelviewer.Constants;
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
    private int mMotionTimeout;

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

        if (mMotion && System.currentTimeMillis() - mLastMotionTime > mMotionTimeout) {
            mMotion = false;
            mServerConnection.updateState(mMotionItem, "OPEN");
        }
    }

    public void updateFromPreferences(SharedPreferences prefs) {
        if (mMotionItem == null || !mMotionItem.equalsIgnoreCase(prefs.getString(Constants.PREF_MOTION_DETECTION_ITEM, ""))) {
            mMotionItem = prefs.getString(Constants.PREF_MOTION_DETECTION_ITEM, "");
            mMotion = false;
        }

        mMotionTimeout = Integer.parseInt(prefs.getString(Constants.PREF_MOTION_DETECTION_TIMEOUT, "60")) * 1000;
        mServerConnection.updateState(mMotionItem, mMotion ? "CLOSED" : "OPEN");
    }

    public void terminate() {
        mMotion = false;
        mServerConnection.updateState(mMotionItem, "OPEN");
    }
}
