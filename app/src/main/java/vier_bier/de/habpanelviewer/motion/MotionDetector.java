package vier_bier.de.habpanelviewer.motion;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import vier_bier.de.habpanelviewer.R;

/**
 * Created by volla on 13.09.17.
 */
public class MotionDetector extends Thread {
    public static final int MY_PERMISSIONS_MOTION_REQUEST_CAMERA = 42;
    private boolean enabled;
    private int detectionCount = 0;

    private Camera mCamera;
    private TextureView mTextureView;

    private final AtomicReference<ImageData> fPreview = new AtomicReference<>();
    private final AtomicBoolean fStopped = new AtomicBoolean(false);
    private int minLuma = 1000;

    private static final int mXBoxes = 10;
    private static final int mYBoxes = 10;
    private LumaData mPreviousState = null;
    private Comparer comparer = null;

    private MotionListener listener;

    public MotionDetector(MotionListener l) {
        listener = l;

        setDaemon(true);
        start();
    }

    public synchronized void enableDetection(TextureView textureView) throws CameraAccessException {
        detectionCount = 0;

        if (mCamera == null) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    mCamera = Camera.open(i);
                    break;
                }
            }

            if (mCamera == null) {
                throw new CameraAccessException(CameraAccessException.CAMERA_ERROR, "Could not find front facing camera!");
            }
        }

        if (mCamera != null) {
            mTextureView = textureView;
            try {
                mCamera.setPreviewTexture(mTextureView.getSurfaceTexture());

                Camera.Parameters parameters = mCamera.getParameters();
                Camera.Size pSize = chooseOptimalSize(parameters.getSupportedPictureSizes(), 640, 480, new Size(640, 480));

                parameters.setPreviewSize(pSize.width, pSize.height);
                mCamera.setParameters(parameters);

                Log.d("Habpanelview", "preview size: " + mCamera.getParameters().getPreviewSize().width + "x" + mCamera.getParameters().getPreviewSize().height);

                mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                    @Override
                    public void onPreviewFrame(byte[] bytes, Camera camera) {
                        setPreview(new ImageData(bytes, camera.getParameters().getPreviewSize().width, camera.getParameters().getPreviewSize().height));
                    }
                });

                mCamera.startPreview();
            } catch (IOException e) {
                Log.e("Habpanelview", "Error setting preview texture", e);
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getPreviewInfo() {
        if (mCamera != null) {
            return "detection resolution " + mCamera.getParameters().getPreviewSize().width + "x" + mCamera.getParameters().getPreviewSize().height
                    + ", motion has been detected " + detectionCount + " times";
        } else {
            return "camera could not be opened";
        }
    }

    public synchronized void disableDetection() {
        if (mCamera != null) {
            mCamera.stopPreview();

            Camera c = mCamera;
            mCamera = null;
            c.release();
        }
    }

    public synchronized void shutdown() {
        disableDetection();

        fStopped.set(true);
    }

    public void setPreview(ImageData p) {
        fPreview.set(p);
    }

    public void run() {
        while (!fStopped.get()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            ImageData p = fPreview.getAndSet(null);

            if (p != null) {
                Log.v("Habpanelview", "processing frame");

                LumaData greyState = p.extractLumaData(mXBoxes, mYBoxes);
                if (greyState.isDarker(minLuma)) {
                    Log.d("Habpanelview", "too dark");
                } else if (detect(p.extractLumaData(mXBoxes, mYBoxes))) {
                    detectionCount++;
                    listener.motionDetected();
                    Log.d("Habpanelview", "motion");
                }

                Log.v("Habpanelview", "processing done");
            }
        }
    }

    /**
     * Detect motion using aggregate luma values. {@inheritDoc}
     */
    public synchronized boolean detect(LumaData s) {
        if (comparer == null) {
            comparer = new Comparer(s.getWidth(), s.getHeight(), mXBoxes, mYBoxes, 20);
        }

        if (mPreviousState == null) {
            mPreviousState = s;
            return false;
        }

        if (s.getWidth() != mPreviousState.getWidth() || s.getHeight() != mPreviousState.getHeight())
            return true;

        boolean isDifferent = comparer.isDifferent(s, mPreviousState);
        mPreviousState = s;

        return isDifferent;
    }

    public synchronized void updateFromPreferences(Activity context, SharedPreferences prefs) {
        enabled = prefs.getBoolean("pref_motion_detection_enabled", false);

        if (enabled) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try {
                    enableDetection((TextureView) context.findViewById(R.id.surfaceView));
                } catch (CameraAccessException e) {
                    Log.e("Habpanelview", "Could not enable MotionDetector", e);
                }
            } else {
                ActivityCompat.requestPermissions(context,
                        new String[]{Manifest.permission.CAMERA},
                        MY_PERMISSIONS_MOTION_REQUEST_CAMERA);
            }
        } else {
            disableDetection();
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    public static Camera.Size chooseOptimalSize(List<Camera.Size> choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Camera.Size> bigEnough = new ArrayList<Camera.Size>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Camera.Size option : choices) {
            if (option.height == option.width * h / w &&
                    option.width >= width && option.height >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return Collections.max(choices, new CompareSizesByArea());
        }
    }

    static class CompareSizesByArea implements Comparator<Camera.Size> {

        @Override
        public int compare(Camera.Size lhs, Camera.Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.width * lhs.height -
                    (long) rhs.width * rhs.height);
        }

    }
}
