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

    final AtomicReference<D> fPreview = new AtomicReference<>();
    private final AtomicBoolean fStopped = new AtomicBoolean(false);

    SurfaceTexture surface;
    boolean enabled;
    Size mPreviewSize;
    String mCameraId;
    int mBoxes = 50;

    private int mLeniency = 20;
    private int mRotationCorrection;
    private int mDeviceRotation;
    private MotionListener listener;
    private int detectionCount = 0;
    private int frameCount = 0;

    private LumaData mPreviousState;
    private Comparer comparer;


    AbstractMotionDetector(MotionListener l) {
        listener = l;

        setDaemon(true);
        start();
    }

    @Override
    public String getPreviewInfo() {
        if (mCameraId != null) {
            return "camera id " + mCameraId + ", detection resolution " + mPreviewSize.getWidth() + "x" + mPreviewSize.getHeight() + "\n"
                    + mBoxes + " detection boxes, leniency is " + mLeniency + "\n"
                    + frameCount + " frames processed, motion has been detected " + detectionCount + " times";
        } else {
            return "camera could not be opened";
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public synchronized void shutdown() {
        stopDetection();

        fStopped.set(true);
    }

    @Override
    public synchronized void updateFromPreferences(Activity context, SharedPreferences prefs) {
        enabled = prefs.getBoolean("pref_motion_detection_enabled", false);
        mBoxes = Integer.parseInt(prefs.getString("pref_motion_detection_granularity", "20"));
        mLeniency = Integer.parseInt(prefs.getString("pref_motion_detection_leniency", "20"));

        mDeviceRotation = context.getWindowManager().getDefaultDisplay().getRotation();

        if (enabled) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                int degrees = context.getWindowManager().getDefaultDisplay().getRotation() * 90;

                try {
                    startDetection((TextureView) context.findViewById(R.id.previewView), degrees);
                } catch (CameraAccessException e) {
                    Log.e(TAG, "Could not enable MotionDetector", e);
                }
            } else {
                ActivityCompat.requestPermissions(context,
                        new String[]{Manifest.permission.CAMERA},
                        MY_PERMISSIONS_MOTION_REQUEST_CAMERA);
            }
        } else {
            stopDetection();
            comparer = null;
        }
    }

    protected abstract int getSensorOrientation();

    public void run() {
        try {
            while (!fStopped.get()) {
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
                        listener.tooDark();
                    } else {
                        ArrayList<Point> differing = detect(greyState);
                        if (differing != null && !differing.isEmpty()) {
                            detectionCount++;
                            listener.motionDetected(differing);
                            Log.v(TAG, "motion");
                        } else {
                            listener.noMotion();
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
        frameCount++;
        fPreview.set(p);
    }

    private synchronized ArrayList<Point> detect(LumaData s) {
        if (comparer == null) {
            comparer = new Comparer(s.getWidth(), s.getHeight(), mBoxes, mLeniency);
        }

        if (mPreviousState == null) {
            mPreviousState = s;
            return null;
        }

        if (s.getWidth() != mPreviousState.getWidth() || s.getHeight() != mPreviousState.getHeight()) {
            return null;
        }

        ArrayList<Point> differing = comparer.isDifferent(s, mPreviousState);
        mPreviousState = s;

        return correctSensorRotation(differing);
    }

    protected synchronized void startDetection(TextureView textureView, int deviceDegrees) throws CameraAccessException {
        stopDetection();

        Log.d(TAG, "starting detection");

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                Log.d(TAG, "surface texture available: " + surfaceTexture);

                if (surfaceTexture != surface) {
                    surface = surfaceTexture;
                    startPreview();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                Log.d(TAG, "surface texture destroyed: " + surfaceTexture);

                surface = null;
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            }
        });

        detectionCount = 0;
        frameCount = 0;

        mCameraId = createCamera(deviceDegrees);

        int rotation = getSensorOrientation() - 90 * mDeviceRotation;
        mRotationCorrection = (rotation + 360) % 360;

        if (textureView.isAvailable()) {
            surface = textureView.getSurfaceTexture();
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

    protected synchronized void stopDetection() {
        stopPreview();
        comparer = null;
    }

    ;

    protected abstract void stopPreview();

    protected abstract void startPreview();

    protected abstract String createCamera(int deviceDegrees) throws CameraAccessException;
}
