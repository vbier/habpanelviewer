package de.vier_bier.habpanelviewer.reporting.motion;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Point;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import de.vier_bier.habpanelviewer.Constants;
import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Base class for motion detectors.
 */
public class MotionDetector extends Thread implements IMotionDetector, ICamera.ILumaListener {
    private static final String TAG = "HPV-MotionDetector";

    private final AtomicBoolean mStopped = new AtomicBoolean(false);

    private final Activity mContext;
    private boolean mEnabled;
    private int mBoxes = 50;

    private int mSleepTime;
    private int mLeniency = 20;
    private final MotionReporter mMotionReporter;
    private int mDetectionCount = 0;
    private int mFrameCount = 0;

    private LumaData mPreviousState;
    private Comparer mComparer;
    private final Camera mCamera;
    private final AtomicReference<LumaData> mLumaData = new AtomicReference<>();

    public MotionDetector(Activity context, Camera camera, IMotionListener l, ServerConnection serverConnection) {
        mContext = context;
        mCamera = camera;

        mMotionReporter = new MotionReporter(l, serverConnection);
        EventBus.getDefault().register(this);

        setDaemon(true);
        start();
    }

    public void run() {
        try {
            while (!mStopped.get()) {
                try {
                    Thread.sleep(mSleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                LumaData greyState = getLumaData();

                if (greyState != null) {
                    Log.v(TAG, "processing frame");

                    mFrameCount++;

                    int minLuma = 1000;
                    if (greyState.isDarker(minLuma)) {
                        Log.v(TAG, "too dark");
                        mMotionReporter.tooDark();
                    } else {
                        ArrayList<Point> differing = detect(greyState);
                        if (differing != null && !differing.isEmpty()) {
                            mDetectionCount++;
                            mMotionReporter.motionDetected(differing);
                            Log.v(TAG, "motion");
                        } else {
                            mMotionReporter.noMotion();
                        }
                    }
                }
            }
        } finally {
            Log.v(TAG, "motion thread finished");
        }
    }

    private LumaData getLumaData() {
        return mLumaData.getAndSet(null);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        if (mEnabled) {
            Resources res = mContext.getResources();
            status.set(mContext.getString(R.string.pref_motion), mContext.getString(R.string.enabled) + "\n" + (mContext.getString(R.string.resolution, 640, 480) + "\n"
                    + res.getQuantityString(R.plurals.boxesLeniency, mBoxes, mBoxes, mLeniency) + "\n"
                    + res.getQuantityString(R.plurals.frames, mFrameCount, mFrameCount)
                    + res.getQuantityString(R.plurals.motionDetected, mDetectionCount, mDetectionCount) + "\n"
                    + mContext.getString(R.string.timeBetweenDetections, mSleepTime)));
        } else {
            status.set(mContext.getString(R.string.pref_motion), mContext.getString(R.string.disabled));
        }
    }

    @Override
    public synchronized void terminate() {
        EventBus.getDefault().unregister(this);

        mMotionReporter.terminate();
        stopDetection();

        mStopped.set(true);
    }

    @Override
    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        boolean newEnabled = prefs.getBoolean(Constants.PREF_MOTION_DETECTION_ENABLED, false);
        int newBoxes = Integer.parseInt(prefs.getString(Constants.PREF_MOTION_DETECTION_GRANULARITY, "20"));
        int newLeniency = Integer.parseInt(prefs.getString(Constants.PREF_MOTION_DETECTION_LENIENCY, "20"));

        mSleepTime = Integer.parseInt(prefs.getString(Constants.PREF_MOTION_DETECTION_SLEEP, "500"));

        if (newEnabled) {
            boolean changed = newBoxes != mBoxes || newLeniency != mLeniency;

            if (changed) {
                mComparer = null;
                mPreviousState = null;
            }

            if (!mEnabled) {
                mMotionReporter.updateFromPreferences(prefs);

                mBoxes = newBoxes;
                mLeniency = newLeniency;
                mDetectionCount = 0;
                mFrameCount = 0;

                mCamera.addLumaListener(this);
                mEnabled = true;
            }
        } else if (mEnabled) {
            stopDetection();
        }
    }

    @Override
    public Camera getCamera() {
        return mCamera;
    }

    private synchronized ArrayList<Point> detect(LumaData s) {
        if (mComparer == null) {
            mComparer = new Comparer(s.getWidth(), s.getHeight(), mBoxes, mLeniency);
        }

        if (mPreviousState == null) {
            mPreviousState = s;
            return null;
        }

        if (s.getWidth() != mPreviousState.getWidth() || s.getHeight() != mPreviousState.getHeight()) {
            return null;
        }

        ArrayList<Point> differing = mComparer.isDifferent(s, mPreviousState);
        mPreviousState.release();
        mPreviousState = s;

        return differing;
    }

    private synchronized void stopDetection() {
        mCamera.removeLumaListener(this);
        mEnabled = false;
        mComparer = null;
        mPreviousState = null;
    }

    @Override
    public void preview(LumaData greyState) {
        mLumaData.set(greyState);
    }

    @Override
    public boolean needsPreview() {
        return mLumaData.get() == null;
    }
}
