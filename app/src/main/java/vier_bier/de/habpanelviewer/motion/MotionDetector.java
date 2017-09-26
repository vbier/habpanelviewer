package vier_bier.de.habpanelviewer.motion;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
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
 * Motion detection thread.
 */
public class MotionDetector extends Thread {
    public static final int MY_PERMISSIONS_MOTION_REQUEST_CAMERA = 42;
    private boolean enabled;
    private boolean running;
    private int detectionCount = 0;
    private int frameCount = 0;

    private int mCameraId;
    private Camera mCamera;
    private SurfaceTexture surface;

    private final AtomicReference<ImageData> fPreview = new AtomicReference<>();
    private final AtomicBoolean fStopped = new AtomicBoolean(false);

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

    public boolean isEnabled() {
        return enabled;
    }

    public String getPreviewInfo() {
        if (mCamera != null) {
            return "camera id " + mCameraId + ", detection resolution " + mCamera.getParameters().getPreviewSize().width + "x" + mCamera.getParameters().getPreviewSize().height + "\n"
                    + frameCount + " frames processed, motion has been detected " + detectionCount + " times";
        } else {
            return "camera could not be opened";
        }
    }

    public synchronized void shutdown() {
        stopDetection();

        fStopped.set(true);
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
                int minLuma = 1000;
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

    public synchronized void updateFromPreferences(Activity context, SharedPreferences prefs) {
        enabled = prefs.getBoolean("pref_motion_detection_enabled", false);

        if (enabled) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try {
                    startDetection((TextureView) context.findViewById(R.id.surfaceView));
                } catch (CameraAccessException e) {
                    Log.e("Habpanelview", "Could not enable MotionDetector", e);
                }
            } else {
                ActivityCompat.requestPermissions(context,
                        new String[]{Manifest.permission.CAMERA},
                        MY_PERMISSIONS_MOTION_REQUEST_CAMERA);
            }
        } else {
            stopDetection();
        }
    }

    private static Camera.Size chooseOptimalSize(List<Camera.Size> choices, int width, int height, Size aspectRatio) {
        List<Camera.Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Camera.Size option : choices) {
            if (option.height == option.width * h / w &&
                    option.width >= width && option.height >= height) {
                bigEnough.add(option);
            }
        }

        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return Collections.max(choices, new CompareSizesByArea());
        }
    }

    private void setPreview(ImageData p) {
        frameCount++;
        fPreview.set(p);
    }

    private synchronized boolean detect(LumaData s) {
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

    private synchronized void startDetection(TextureView textureView) throws CameraAccessException {
        Log.d("Habpanelview", "detection ");

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                Log.d("Habpanelview", "surface texture available: " + surfaceTexture);

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
                Log.d("Habpanelview", "surface texture destroyed: " + surfaceTexture);

                surface = null;
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            }
        });

        detectionCount = 0;
        mCameraId = -1;
        frameCount = 0;

        if (mCamera == null) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    mCameraId = i;
                    mCamera = Camera.open(i);
                    break;
                }
            }

            if (mCamera == null) {
                throw new CameraAccessException(CameraAccessException.CAMERA_ERROR, "Could not find front facing camera!");
            }
        }

        if (textureView.isAvailable()) {
            surface = textureView.getSurfaceTexture();
            startPreview();
        }
    }

    private synchronized void startPreview() {
        if (enabled && !running && mCamera != null && surface != null) {
            try {
                mCamera.stopPreview();
                mCamera.setPreviewTexture(surface);

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
                running = true;
            } catch (IOException e) {
                Log.e("Habpanelview", "Error setting preview texture", e);
            }
        }
    }

    private synchronized void stopDetection() {
        if (mCamera != null) {
            mCamera.stopPreview();

            Camera c = mCamera;
            mCamera = null;
            c.release();

            running = false;
        }
    }

    private static class CompareSizesByArea implements Comparator<Camera.Size> {

        @Override
        public int compare(Camera.Size lhs, Camera.Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.width * lhs.height -
                    (long) rhs.width * rhs.height);
        }

    }
}
