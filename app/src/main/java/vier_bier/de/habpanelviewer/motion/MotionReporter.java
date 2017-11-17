package vier_bier.de.habpanelviewer.motion;

import android.content.SharedPreferences;
import android.graphics.Point;

import java.util.ArrayList;

import vier_bier.de.habpanelviewer.openhab.SetItemStateTask;

/**
 * Reports motion events to openHAB.
 */
public class MotionReporter extends MotionListener.MotionAdapter {
    private static final String TAG = "MotionReporter";

    private MotionListener mListener;

    private long mLastMotionTime;
    private boolean mMotion;

    private String mServerURL;
    private boolean mIgnoreCertErrors;

    private String mMotionItem;

    public MotionReporter(MotionListener l) {
        mListener = l;
    }

    @Override
    public void motionDetected(ArrayList<Point> differing) {
        mListener.motionDetected(differing);

        mLastMotionTime = System.currentTimeMillis();
        if (!mMotion) {
            mMotion = true;
            updateState();
        }
    }

    @Override
    public void noMotion() {
        mListener.noMotion();

        if (mMotion && System.currentTimeMillis() - mLastMotionTime > 60000) {
            mMotion = false;
            updateState();
        }
    }

    public void updateFromPreferences(SharedPreferences prefs) {
        mServerURL = prefs.getString("pref_url", "");
        mIgnoreCertErrors = prefs.getBoolean("pref_ignore_ssl_errors", false);

        if (mMotionItem == null || !mMotionItem.equalsIgnoreCase(prefs.getString("pref_motion_item", ""))) {
            mMotionItem = prefs.getString("pref_motion_item", "");
        }

        updateState();
    }

    public void terminate() {
        mMotion = false;
        updateState();
    }

    private void updateState() {
        if (!mMotionItem.isEmpty()) {
            SetItemStateTask t = new SetItemStateTask(mServerURL, mIgnoreCertErrors);
            t.execute(new SetItemStateTask.ItemState(mMotionItem, mMotion ? "CLOSED" : "OPEN"));
        }
    }
}
