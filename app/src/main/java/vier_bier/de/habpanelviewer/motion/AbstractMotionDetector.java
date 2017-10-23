package vier_bier.de.habpanelviewer.motion;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import vier_bier.de.habpanelviewer.R;

/**
 * Base class for motion detectors.
 */
abstract class AbstractMotionDetector<D> extends Thread implements IMotionDetector {
    private static final String TAG = "AbstractMotionDetector";

    final AtomicReference<D> mPreview = new AtomicReference<>();
    private final AtomicBoolean mStopped = new AtomicBoolean(false);

    SurfaceTexture mSurface;
    boolean mEnabled;
    Size mPreviewSize;
    String mCameraId;
    int mBoxes = 50;

    private int mLeniency = 20;
    private int mRotationCorrection;
    private int mDeviceRotation;
    private MotionListener mListener;
    private int mDetectionCount = 0;
    private int mFrameCount = 0;

    private LumaData mPreviousState;
    private Comparer mComparer;


    AbstractMotionDetector(MotionListener l) {
        mListener = l;

        setDaemon(true);
        start();
    }

    @Override
    public String getPreviewInfo() {
        if (mCameraId != null) {
            return "camera id " + mCameraId + ", detection resolution " + mPreviewSize.getWidth() + "x" + mPreviewSize.getHeight() + "\n"
                    + mBoxes + " detection boxes, leniency is " + mLeniency + "\n"
                    + mFrameCount + " frames processed, motion has been detected " + mDetectionCount + " times";
        } else {
            return "camera could not be opened";
        }
    }

    @Override
    public boolean isEnabled() {
        return mEnabled;
    }

    @Override
    public synchronized void shutdown() {
        stopDetection();

        mStopped.set(true);
    }

    @Override
    public synchronized void updateFromPreferences(Activity context, SharedPreferences prefs) {
        boolean newEnabled = prefs.getBoolean("pref_motion_detection_enabled", false);
        int newBoxes = Integer.parseInt(prefs.getString("pref_motion_detection_granularity", "20"));
        int newLeniency = Integer.parseInt(prefs.getString("pref_motion_detection_leniency", "20"));
        int newDeviceRotation = context.getWindowManager().getDefaultDisplay().getRotation();

        if (newEnabled) {
            boolean changed = newBoxes != mBoxes || newLeniency != mLeniency || newDeviceRotation != mDeviceRotation;

            if (changed && mEnabled) {
                stopDetection();
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try {
                    mBoxes = newBoxes;
                    mLeniency = newLeniency;
                    mDeviceRotation = newDeviceRotation;

                    startDetection((TextureView) context.findViewById(R.id.previewView), newDeviceRotation * 90);
                    mEnabled = true;
                } catch (CameraAccessException e) {
                    Log.e(TAG, "Could not enable MotionDetector", e);
                }
            } else {
                ActivityCompat.requestPermissions(context, new String[]{Manifest.permission.CAMERA},
                        MY_PERMISSIONS_MOTION_REQUEST_CAMERA);
            }
        } else if (mEnabled) {
            stopDetection();
            mEnabled = false;
        }
    }

    protected abstract int getSensorOrientation();

    public void run() {
        try {
            while (!mStopped.get()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                LumaData greyState = getPreviewLumaData();

                if (greyState != null) {
                    Log.v(TAG, "processing frame");

                    if (mPreviewSize == null) {
                        mPreviewSize = new Size(greyState.getWidth(), greyState.getHeight());
                    }

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

    void setPreview(D p) {
        mFrameCount++;
        mPreview.set(p);
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

    protected synchronized void startDetection(TextureView textureView, int deviceDegrees) throws CameraAccessException {
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

    void chooseOptimalSize(Size[] choices, int textureViewWidth,
                           int textureViewHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
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

    private static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    protected abstract LumaData getPreviewLumaData();

    synchronized void stopDetection() {
        stopPreview();
        mComparer = null;
        mPreviousState = null;
    }

    protected abstract void stopPreview();

    protected abstract void startPreview();

    protected abstract String createCamera(int deviceDegrees) throws CameraAccessException;
}
