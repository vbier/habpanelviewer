package de.vier_bier.habpanelviewer.reporting.motion;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.TextureView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Base class for motion detectors.
 */
abstract class AbstractMotionDetector<D> extends Thread implements IMotionDetector {
    private static final String TAG = "AbstractMotionDetector";

    private final AtomicReference<D> mPreview = new AtomicReference<>();
    private final AtomicBoolean mStopped = new AtomicBoolean(false);

    final Activity mContext;
    SurfaceTexture mSurface;
    boolean mEnabled;
    Point mPreviewSize;
    String mCameraId;
    int mBoxes = 50;

    private int mSleepTime;
    private int mLeniency = 20;
    private int mRotationCorrection;
    private int mDeviceRotation;
    private final MotionReporter mListener;
    private int mDetectionCount = 0;
    private int mFrameCount = 0;

    private LumaData mPreviousState;
    private Comparer mComparer;

    AbstractMotionDetector(Activity context, IMotionListener l, ServerConnection serverConnection) {
        super("AbstractMotionDetector");

        mContext = context;
        mListener = new MotionReporter(l, serverConnection);
        EventBus.getDefault().register(this);

        setDaemon(true);
        start();
    }

    protected abstract int getSensorOrientation();

    protected abstract LumaData getPreviewLumaData();

    protected abstract void stopPreview();

    protected abstract void startPreview();

    protected abstract String getCameraInfo();

    protected abstract String createCamera(int deviceDegrees) throws CameraException;

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        status.set(mContext.getString(R.string.cameras), getCameraInfo());
        if (mEnabled) {
            status.set(mContext.getString(R.string.pref_motion), mContext.getString(R.string.enabled) + "\n" + getPreviewInfo());
        } else {
            status.set(mContext.getString(R.string.pref_motion), mContext.getString(R.string.disabled));
        }
    }

    @Override
    public synchronized void terminate() {
        mListener.terminate();
        stopDetection();

        mStopped.set(true);
    }

    @Override
    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        boolean newEnabled = prefs.getBoolean("pref_motion_detection_enabled", false);
        int newBoxes = Integer.parseInt(prefs.getString("pref_motion_detection_granularity", "20"));
        int newLeniency = Integer.parseInt(prefs.getString("pref_motion_detection_leniency", "20"));
        int newDeviceRotation = mContext.getWindowManager().getDefaultDisplay().getRotation();

        mSleepTime = Integer.parseInt(prefs.getString("pref_motion_detection_sleep", "500"));

        if (newEnabled) {
            mListener.updateFromPreferences(prefs);

            boolean changed = newBoxes != mBoxes || newLeniency != mLeniency || newDeviceRotation != mDeviceRotation;

            if (changed && mEnabled) {
                stopDetection();
            }

            if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try {
                    mBoxes = newBoxes;
                    mLeniency = newLeniency;
                    mDeviceRotation = newDeviceRotation;

                    startDetection(mContext.findViewById(R.id.previewView), newDeviceRotation * 90);
                    mEnabled = true;
                } catch (CameraException e) {
                    Log.e(TAG, "Could not enable MotionDetector", e);
                }
            } else {
                ActivityCompat.requestPermissions(mContext, new String[]{Manifest.permission.CAMERA},
                        MY_PERMISSIONS_MOTION_REQUEST_CAMERA);
            }
        } else if (mEnabled) {
            stopDetection();
            mEnabled = false;
        }
    }

    public void run() {
        try {
            while (!mStopped.get()) {
                try {
                    Thread.sleep(mSleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                LumaData greyState = getPreviewLumaData();

                if (greyState != null) {
                    Log.v(TAG, "processing frame");

                    if (mPreviewSize == null) {
                        mPreviewSize = new Point(greyState.getWidth(), greyState.getHeight());
                    }

                    mFrameCount++;

                    int minLuma = 1000;
                    if (greyState.isDarker(minLuma)) {
                        Log.v(TAG, "too dark");
                        mListener.tooDark();
                    } else {
                        ArrayList<Point> differing = detect(greyState);
                        if (differing != null && !differing.isEmpty()) {
                            mDetectionCount++;
                            mListener.motionDetected(differing);
                            Log.v(TAG, "motion");
                        } else {
                            mListener.noMotion();
                        }
                    }

                    Log.v(TAG, "processing done");
                }
            }
        } finally {
            Log.v(TAG, "motion thread finished");
        }
    }

    private String getPreviewInfo() {
        if (mCameraId != null) {
            String retVal = mContext.getString(R.string.cameraId, mCameraId);
            if (mPreviewSize != null) {
                retVal += ", " + mContext.getString(R.string.detectionResolution, mPreviewSize.x, mPreviewSize.y);
            }
            return retVal + "\n"
                    + mContext.getString(R.string.boxesLeniency, mBoxes, mLeniency) + "\n"
                    + mContext.getString(R.string.framesProcessed, mFrameCount, mDetectionCount);
        } else {
            return mContext.getString(R.string.failedAccessCamera);
        }
    }


    void setPreview(D p) {
        mPreview.set(p);
    }

    boolean previewMissing() {
        return mPreview.get() == null;
    }

    D getPreview() {
        return mPreview.getAndSet(null);
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

        return correctSensorRotation(differing);
    }

    synchronized void startDetection(TextureView textureView, int deviceDegrees) throws CameraException {
        stopDetection();

        Log.d(TAG, "starting detection");

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                Log.d(TAG, "mSurface texture available: " + surfaceTexture);

                if (surfaceTexture != mSurface) {
                    mSurface = surfaceTexture;
                    startPreview();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                Log.d(TAG, "mSurface texture destroyed: " + surfaceTexture);

                mSurface = null;
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            }
        });

        mDetectionCount = 0;
        mFrameCount = 0;

        mCameraId = createCamera(deviceDegrees);

        int rotation = getSensorOrientation() - 90 * mDeviceRotation;
        mRotationCorrection = (rotation + 360) % 360;

        if (textureView.isAvailable()) {
            mSurface = textureView.getSurfaceTexture();
            startPreview();
        }
    }

    private ArrayList<Point> correctSensorRotation(ArrayList<Point> points) {
        ArrayList<Point> corrected = new ArrayList<>(points.size());
        for (Point p : points) {
            if (mRotationCorrection == 270) {
                corrected.add(new Point(mBoxes - 1 - p.y, mBoxes - 1 - p.x));
            } else if (mRotationCorrection == 180) {
                corrected.add(new Point(mBoxes - 1 - p.x, p.y));
            } else if (mRotationCorrection == 90) {
                corrected.add(new Point(p.y, mBoxes - 1 - p.x));
            } else {
                corrected.add(new Point(p.x, mBoxes - 1 - p.y));
            }
        }

        return corrected;
    }

    void chooseOptimalSize(Point[] choices, int textureViewWidth,
                           int textureViewHeight, Point aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Point> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Point> notBigEnough = new ArrayList<>();
        int w = aspectRatio.x;
        int h = aspectRatio.y;
        for (Point option : choices) {
            if (option.y == option.x * h / w) {
                if (option.x >= textureViewWidth &&
                        option.y >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            mPreviewSize = Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            mPreviewSize = Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            mPreviewSize = choices[0];
        }
    }

    synchronized void stopDetection() {
        stopPreview();
        mComparer = null;
        mPreviousState = null;
    }

    private static class CompareSizesByArea implements Comparator<Point> {
        @Override
        public int compare(Point lhs, Point rhs) {
            return Long.signum((long) lhs.x * lhs.y -
                    (long) rhs.x * rhs.y);
        }

    }
}
