package vier_bier.de.habpanelviewer.motion;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Motion detection thread using Camera2 API.
 */
public class MotionDetectorCamera2 extends Thread implements IMotionDetector {
    private static final String TAG = "MotionDetectorCamera2";

    private CameraManager camManager;

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private CameraCaptureSession mCaptureSession;
    private ImageReader mImageReader;

    private CameraDevice mCamera;
    private String cameraId;

    private boolean enabled;
    private final AtomicReference<LumaData> fPreview = new AtomicReference<>();
    private final AtomicBoolean fStopped = new AtomicBoolean(false);

    private static final int mXBoxes = 10;
    private static final int mYBoxes = 10;
    private LumaData mPreviousState = null;
    private Comparer comparer = null;

    private MotionListener listener;
    private int detectionCount = 0;
    private int frameCount = 0;

    public MotionDetectorCamera2(CameraManager manager, MotionListener l) throws CameraAccessException {
        Log.d(TAG, "instantiating motion detection");

        camManager = manager;
        listener = l;

        for (String camId : camManager.getCameraIdList()) {
            CameraCharacteristics characteristics = camManager.getCameraCharacteristics(camId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                Log.d(TAG, "front-facing mCamera found: " + camId);
                cameraId = camId;
                break;
            }
        }

        if (cameraId == null) {
            throw new CameraAccessException(CameraAccessException.CAMERA_ERROR, "Could not find front facing mCamera!");
        }

        setDaemon(true);
        start();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getPreviewInfo() {
        if (mCamera != null) {
            return "camera id " + cameraId + ",\n"
                    + frameCount + " frames processed, motion has been detected " + detectionCount + " times";
        } else {
            return "camera could not be opened";
        }
    }

    @Override
    public synchronized void updateFromPreferences(Activity context, SharedPreferences prefs) {
        enabled = prefs.getBoolean("pref_motion_detection_enabled", false);

        if (enabled) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try {
                    startDetection();
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
        }
    }

    @Override
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

            LumaData greyState = fPreview.getAndSet(null);

            if (greyState != null) {
                Log.v(TAG, "processing frame");

                int minLuma = 1000;
                if (greyState.isDarker(minLuma)) {
                    Log.v(TAG, "too dark");
                } else if (detect(greyState)) {
                    detectionCount++;
                    listener.motionDetected();
                    Log.v(TAG, "motion");
                }

                Log.v(TAG, "processing done");
            }
        }
    }

    private void startDetection() throws CameraAccessException {
        Log.d(TAG, "starting motion detection");
        openCamera();
    }

    private void stopDetection() {
        if (mCamera != null) {
            mCamera.close();
            mCamera = null;
        }
    }

    private void openCamera() {
        try {
            camManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice) {
                    Log.d(TAG, "mCamera opened: " + cameraDevice);
                    mCameraOpenCloseLock.release();
                    mCamera = cameraDevice;
                    createCameraPreviewSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    mCameraOpenCloseLock.release();
                    Log.d(TAG, "mCamera disconnected: " + cameraDevice);
                    stopDetection();
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int i) {
                    mCameraOpenCloseLock.release();
                    Log.e(TAG, "mCamera error: " + cameraDevice + ", error code: " + i);
                    stopDetection();
                }
            }, null);
        } catch (CameraAccessException | SecurityException e) {
            Log.e(TAG, "Could not open camera", e);
        }
    }

    private void createCameraPreviewSession() {
        try {
            CameraCharacteristics characteristics
                    = camManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            Size mPreviewSize = chooseOptimalSize(map.getOutputSizes(ImageFormat.YUV_420_888), 640, 480, new Size(4, 3));
            Log.v(TAG, "preview image size is " + mPreviewSize.getWidth() + "x" + mPreviewSize.getHeight());

            mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                    ImageFormat.YUV_420_888, 2);
            mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image i = reader.acquireLatestImage();

                    if (i != null) {
                        if (fPreview.get() == null) {
                            Log.v(TAG, "preview image available: size " + i.getWidth() + "x" + i.getHeight());
                        }

                        ByteBuffer luma = i.getPlanes()[0].getBuffer();
                        final byte[] data = new byte[luma.capacity()];
                        luma.get(data);

                        setPreview(new LumaData(data, i.getWidth(), i.getHeight(), mXBoxes, mYBoxes));
                        i.close();
                    }
                }
            }, null);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());

            // Here, we create a CameraCaptureSession for mCamera preview.
            mCamera.createCaptureSession(Collections.singletonList(mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG, "Capture session configured");

                            // The mCamera is already closed
                            if (null == mCamera) {
                                Log.e(TAG, "Capture session has no camera");
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for mCamera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                // Finally, we start displaying the mCamera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        null, null);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Could not create preview request", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, "Could not create capture session");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            Log.e(TAG, "Could not create preview", e);
        }
    }

    private void setPreview(LumaData p) {
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

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
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
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

}
