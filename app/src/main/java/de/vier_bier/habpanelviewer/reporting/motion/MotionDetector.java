package de.vier_bier.habpanelviewer.reporting.motion;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Base class for motion detectors.
 */
public class MotionDetector extends Thread implements IMotionDetector, ICamera.ILumaListener {
    private static final String TAG = "MotionDetector";

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
    private Camera mCamera;
    private AtomicReference<LumaData> mLumaData = new AtomicReference<>();

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

                    Log.v(TAG, "processing done");
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
            status.set(mContext.getString(R.string.pref_motion), mContext.getString(R.string.enabled) + "\n" + (mContext.getString(R.string.resolution, 640, 480) + "\n"
                    + mContext.getString(R.string.boxesLeniency, mBoxes, mLeniency) + "\n"
                    + mContext.getString(R.string.framesProcessed, mFrameCount, mDetectionCount) + "\n"
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
        boolean newEnabled = prefs.getBoolean("pref_motion_detection_enabled", false);
        int newBoxes = Integer.parseInt(prefs.getString("pref_motion_detection_granularity", "20"));
        int newLeniency = Integer.parseInt(prefs.getString("pref_motion_detection_leniency", "20"));

        mSleepTime = Integer.parseInt(prefs.getString("pref_motion_detection_sleep", "500"));

        if (newEnabled) {
            boolean changed = newBoxes != mBoxes || newLeniency != mLeniency;

            if (changed) {
                mComparer = null;
                mPreviousState = null;
            }

            if (!mEnabled) {
                mMotionReporter.updateFromPreferences(prefs);

                if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    mBoxes = newBoxes;
                    mLeniency = newLeniency;
                    mDetectionCount = 0;
                    mFrameCount = 0;

                    try {
                        mCamera.addLumaListener(this);
                    } catch (CameraException e) {
                        Log.e(TAG, "Could not enable MotionDetector", e);
                    }
                    mEnabled = true;
                } else {
                    ActivityCompat.requestPermissions(mContext, new String[]{Manifest.permission.CAMERA},
                            MY_PERMISSIONS_MOTION_REQUEST_CAMERA);
                }
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
        mPreviousState = s;

        return differing;
    }

    private synchronized void stopDetection() {
        try {
            mCamera.removeLumaListener(this);
        } catch (CameraException e) {
            Log.e(TAG, "Could not disable MotionDetector", e);
        }
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
