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
import android.view.TextureView;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import vier_bier.de.habpanelviewer.R;

/**
 * Created by volla on 13.09.17.
 */
public class MotionDetector extends Thread {
    public static final int MY_PERMISSIONS_MOTION_REQUEST_CAMERA = 42;
    private boolean enabled;

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
                parameters.setPreviewSize(640, 480);
                mCamera.setParameters(parameters);

                Log.i("Habpanelview", "preview size: " + mCamera.getParameters().getPreviewSize().width + "x" + mCamera.getParameters().getPreviewSize().height);

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
}
