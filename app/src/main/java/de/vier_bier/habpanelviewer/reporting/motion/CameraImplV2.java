package de.vier_bier.habpanelviewer.reporting.motion;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.vier_bier.habpanelviewer.R;

/**
 * Concrete camera implementation using the V2 API.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class CameraImplV2 extends AbstractCameraImpl {
    private static final String TAG = "HPV-CameraImplV2";

    private final CameraManager mCamManager;
    private HandlerThread mPreviewThread;
    private Handler mPreviewHandler;
    private HandlerThread mPictureThread;
    private Handler mPictureHandler;
    private boolean mTakingPicture;

    private CameraDevice mCamera;
    private String mCameraId;
    private int mCameraOrientation;

    private CameraCaptureSession mCaptureSession;
    private volatile boolean mPreviewRunning;
    @SuppressWarnings("FieldCanBeLocal")
    private ImageReader mImageReader; //do not use a variable as this gets GC'ed

    CameraImplV2(Activity context, TextureView prevView) throws CameraException {
        super(context, prevView);

        mCamManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        if (mCamManager == null) {
            throw new CameraException(mActivity.getString(R.string.couldNotObtainCameraService));
        }

        try {
            for (String camId : mCamManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mCamManager.getCameraCharacteristics(camId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    mCameraId = camId;

                    Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    if (orientation != null) {
                        mCameraOrientation = orientation;
                    }

                    Log.v(TAG, "found front-facing camera with id " + camId + " and orientation " + mCameraOrientation);
                }
            }
        } catch (CameraAccessException e) {
            throw new CameraException(e);
        }

        if (mCameraId == null) {
            throw new CameraException(mActivity.getString(R.string.frontCameraMissing));
        }

        mPreviewThread = new HandlerThread("previewThread");
        mPreviewThread.start();
        mPreviewHandler = new Handler(mPreviewThread.getLooper());
    }

    @Override
    public void lockCamera() throws CameraException {
        if (mCameraId != null && mCamera == null) {
            try {
                if (ActivityCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    throw new CameraException(mActivity.getString(R.string.permissionMissing, Manifest.permission.CAMERA));
                }

                final CountDownLatch latch = new CountDownLatch(1);
                mCamManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice cameraDevice) {
                        Log.d(TAG, "mCamera opened: " + cameraDevice);
                        mCamera = cameraDevice;
                        latch.countDown();
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                        Log.d(TAG, "mCamera disconnected: " + cameraDevice);
                        unlockCamera();
                        latch.countDown();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice cameraDevice, int i) {
                        Log.e(TAG, "mCamera error: " + cameraDevice + ", error code: " + i);
                        unlockCamera();
                        latch.countDown();
                    }
                }, mPreviewHandler);

                try {
                    latch.await(2, TimeUnit.SECONDS);
                    if (latch.getCount() == 1) {
                        throw new CameraException(mActivity.getString(R.string.openingCamTimedOut));
                    }
                } catch (InterruptedException e) {
                    throw new CameraException(mActivity.getString(R.string.openingCamInterrupted), e);
                }
            } catch (CameraAccessException e) {
                throw new CameraException(e);
            }
        } else {
            throw new CameraException(mActivity.getString(R.string.frontCameraMissing));
        }
    }

    @Override
    public void setDeviceRotation(int deviceOrientation) {
        mDeviceOrientation = deviceOrientation;

        // configure transform if preview is running only
        if (isPreviewRunning()) {
            try {
                CameraCharacteristics characteristics = mCamManager.getCameraCharacteristics(mCamera.getId());

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    Log.w(TAG, "Could not find a valid preview size");
                } else {
                    final Point previewSize = chooseOptimalSize(toPointArray(map.getOutputSizes(ImageFormat.YUV_420_888)));
                    setDeviceOrientation(previewSize);
                }
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to set device orientation", e);
            }
        }
    }

    private void setDeviceOrientation(Point previewSize) {
        mActivity.runOnUiThread(() -> configureTransform(previewSize, mDeviceOrientation));
    }

    @Override
    public void unlockCamera() {
        if (mCamera != null) {
            mCamera.close();
            mCamera = null;
            mCaptureSession = null;
            mPreviewRunning = false;
        }
    }

    @Override
    public boolean isCameraLocked() {
        return mCamera != null;
    }

    @Override
    public void startPreview(SurfaceTexture surface, IPreviewListener previewListener) {
        if (isPreviewRunning()) {
            previewListener.started();
            return;
        }

        if (mCamera != null && surface != null) {
            Log.v(TAG, "trying to start preview...");
            try {
                CameraCharacteristics characteristics
                        = mCamManager.getCameraCharacteristics(mCamera.getId());
                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    Log.w(TAG, "Could not find a valid preview size");
                }

                final Point previewSize = chooseOptimalSize(toPointArray(map.getOutputSizes(ImageFormat.YUV_420_888)));
                Log.v(TAG, "preview image size is " + previewSize.x + "x" + previewSize.y);

                mActivity.runOnUiThread(() -> configureTransform(previewSize, mActivity.getWindowManager().getDefaultDisplay().getRotation()));

                mImageReader = ImageReader.newInstance(previewSize.x, previewSize.y,
                        ImageFormat.YUV_420_888, 2);
                mImageReader.setOnImageAvailableListener(imageReader -> {
                    // check if we have a camera as we might get an image after closing and we
                    // do not want to set mPreviewRunning to true in this case
                    if (!mPreviewRunning && mCamera != null) {
                        mPreviewRunning = true;
                        previewListener.started();
                    }

                    if (!mTakingPicture) {
                        try (Image i = imageReader.acquireLatestImage()) {
                            LumaData ld = null;
                            for (ILumaListener l : mListeners) {
                                if (l.needsPreview()) {
                                    if (ld == null && i != null) {
                                        ByteBuffer buffer = i.getPlanes()[0].getBuffer();
                                        ld = new LumaData(buffer, i.getWidth(), i.getHeight());
                                    }
                                    l.preview(ld);
                                }
                            }
                        }
                    }
                }, mPreviewHandler);

                CaptureRequest.Builder mPreviewRequestBuilder
                        = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mPreviewRequestBuilder.addTarget(mImageReader.getSurface());
                Surface previewSurface = new Surface(surface);
                mPreviewRequestBuilder.addTarget(previewSurface);

                ArrayList<Surface> surfaces = new ArrayList<>();
                surfaces.add(mImageReader.getSurface());
                surfaces.add(previewSurface);

                final CountDownLatch initLatch = new CountDownLatch(1);
                mCamera.createCaptureSession(surfaces,
                        new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                Log.d(TAG, "Capture session configured");

                                // The mCamera is already closed
                                if (null == mCamera) {
                                    previewListener.error(mActivity.getString(R.string.camAlreadyClosed));
                                    initLatch.countDown();
                                    return;
                                }

                                mCaptureSession = cameraCaptureSession;
                                try {
                                    // Auto focus should be continuous for mCamera preview.
                                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                    // Finally, we start displaying the mCamera preview.
                                    cameraCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                                            null, null);
                                } catch (CameraAccessException | IllegalStateException e) {
                                    previewListener.exception(e);
                                    previewListener.error(mActivity.getString(R.string.couldNotCreatePreview));
                                } finally {
                                    initLatch.countDown();
                                }
                            }

                            @Override
                            public void onClosed(@NonNull CameraCaptureSession session) {
                                super.onClosed(session);
                                mPreviewRunning = false;
                                initLatch.countDown();
                            }

                            @Override
                            public void onConfigureFailed(
                                    @NonNull CameraCaptureSession cameraCaptureSession) {
                                previewListener.error(mActivity.getString(R.string.couldNotCreateCapture));
                                initLatch.countDown();
                            }
                        }, mPreviewHandler
                );
                initLatch.await();
            } catch (CameraAccessException | InterruptedException e) {
                previewListener.exception(e);
                previewListener.error(mActivity.getString(R.string.couldNotCreatePreview));
            }
        } else {
            previewListener.error(mActivity.getString(R.string.frontCameraMissing));
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (mPictureThread != null) {
            mPreviewThread.quitSafely();
        }
        if (mPictureThread != null) {
            mPictureThread.quitSafely();
        }

        super.finalize();
    }

    @Override
    public void stopPreview() throws CameraException {
        if (mCaptureSession != null) {
            try {
                mCaptureSession.close();

                try {
                    for (int i = 0; i < 50 && isPreviewRunning(); i++) {
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                if (isPreviewRunning()) {
                    throw new CameraException(mActivity.getString(R.string.couldNotStopPreview));
                }
                mCaptureSession = null;
            } catch (IllegalStateException e) {
                Log.e(TAG,"Got IllegalStateException when closing capture session", e);
                // camera session has been closed, no problem
            }
        }
    }

    @Override
    public boolean isPreviewRunning() {
        return mPreviewRunning && mCamera != null && mCaptureSession != null;
    }

    @Override
    /**
     * This is asynchronous!
     */
    public void takePicture(IPictureListener iPictureHandler) {
        if (mCamera != null) {
            if (mPictureThread == null) {
                Log.d(TAG, "creating picture thread...");

                mPictureThread = new HandlerThread("takePictureThread") {
                    @Override
                    public void run() {
                        Log.d(TAG, "takePictureThread running...");
                        try {
                            super.run();
                        } finally {
                            Log.d(TAG, "takePictureThread exiting...");
                       }
                    }
                };
                mPictureThread.start();
                mPictureHandler = new Handler(mPictureThread.getLooper());
            }

            try {
                mTakingPicture = true;

                int width = 640;
                int height = 480;

                Log.d(TAG, "creating ImageReader...");
                ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
                Log.d(TAG, "adding surfaces...");
                List<Surface> outputSurfaces = new ArrayList<>(2);
                outputSurfaces.add(reader.getSurface());
                Log.d(TAG, "creating builder...");
                final CaptureRequest.Builder captureBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                Log.d(TAG, "adding target...");
                captureBuilder.addTarget(reader.getSurface());
                Log.d(TAG, "setting control mode...");
                captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

                Log.d(TAG, "creating readerListener...");
                ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                    byte[] mBuffer;

                    @Override
                    public void onImageAvailable(ImageReader imageReader) {
                        Log.d(TAG, "onImageAvailable");
                        try (Image image = imageReader.acquireLatestImage()) {
                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();

                            if (mBuffer == null || mBuffer.length != buffer.capacity()) {
                                mBuffer = new byte[buffer.capacity()];
                            }
                            buffer.get(mBuffer);

                            iPictureHandler.picture(mBuffer);
                            mTakingPicture = false;
                        }
                    }
                };
                reader.setOnImageAvailableListener(readerListener, mPictureHandler);
                Log.d(TAG, "creating captureListener...");
                final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                        Log.v(TAG, "onCaptureCompleted");
                        mTakingPicture = false;
                    }

                    @Override
                    public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                        Log.v(TAG, "onCaptureFailed");
                        mTakingPicture = false;
                    }

                    @Override
                    public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
                        Log.v(TAG, "onCaptureSequenceCompleted");
                        mTakingPicture = false;
                    }

                    @Override
                    public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
                        Log.v(TAG, "onCaptureSequenceAborted");
                        mTakingPicture = false;
                    }

                    @Override
                    public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                        Log.v(TAG, "onCaptureStarted");
                        super.onCaptureStarted(session, request, timestamp, frameNumber);
                    }

                    @Override
                    public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                        Log.v(TAG, "onCaptureProgressed");
                        super.onCaptureProgressed(session, request, partialResult);
                    }

                    @Override
                    public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
                        Log.v(TAG, "onCaptureBufferLost");
                        super.onCaptureBufferLost(session, request, target, frameNumber);
                    }
                };

                Log.d(TAG, "creating capture session...");
                mCamera.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        Log.v(TAG, "onConfigured");
                        try {
                            session.capture(captureBuilder.build(), captureListener, mPictureHandler);
                        } catch (Throwable t) {
                            Log.e(TAG, "Failed to capture picture", t);
                            iPictureHandler.error(mActivity.getString(R.string.camFailedToTakePic));
                            mTakingPicture = false;
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Log.e(TAG, "onConfigureFailed");
                        iPictureHandler.error(mActivity.getString(R.string.couldNotCreateCapture));
                        mTakingPicture = false;
                    }

                    @Override
                    public void onReady(@NonNull CameraCaptureSession session) {
                        Log.v(TAG, "onReady");
                        super.onReady(session);
                    }

                    @Override
                    public void onActive(@NonNull CameraCaptureSession session) {
                        Log.v(TAG, "onActive");
                        super.onActive(session);
                    }

                    @Override
                    public void onCaptureQueueEmpty(@NonNull CameraCaptureSession session) {
                        Log.v(TAG, "onCaptureQueueEmpty");
                        super.onCaptureQueueEmpty(session);
                    }

                    @Override
                    public void onClosed(@NonNull CameraCaptureSession session) {
                        Log.v(TAG, "onClosed");
                        super.onClosed(session);
                    }

                    @Override
                    public void onSurfacePrepared(@NonNull CameraCaptureSession session, @NonNull Surface surface) {
                        Log.v(TAG, "onSurfacePrepared");
                        super.onSurfacePrepared(session, surface);
                    }
                }, mPictureHandler);

                Log.d(TAG, "capture session created");
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to take picture", e);
                iPictureHandler.error(mActivity.getString(R.string.camFailedToTakePic));
            } finally {
                Log.d(TAG, "leaving takePicture");
            }
        } else {
            throw new IllegalStateException(mActivity.getString(R.string.camNotInitialized));
        }
    }

    @Override
    public int getCameraOrientation() {
        try {
            CameraCharacteristics characteristics
                    = mCamManager.getCameraCharacteristics(mCameraId);
            Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (orientation != null) {
                return orientation;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Couldn't find out camera sensor orientation");
        }

        return 0;
    }

    private void configureTransform(Point previewSize, int rotation) {
        if (null == mPreviewView || null == mActivity) {
            return;
        }

        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, mPreviewView.getWidth(), mPreviewView.getHeight());
        RectF bufferRect = new RectF(0, 0, previewSize.y, previewSize.x);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) mPreviewView.getHeight() / previewSize.y,
                    (float) mPreviewView.getWidth() / previewSize.x);
            matrix.postScale(scale, scale, centerX, centerY);
        }
        matrix.postRotate(-90 * rotation, centerX, centerY);
        mPreviewView.setTransform(matrix);
    }

    private Point[] toPointArray(Size[] supportedPictureSizes) {
        ArrayList<Point> result = new ArrayList<>();
        for (Size s : supportedPictureSizes) {
            result.add(new Point(s.getWidth(), s.getHeight()));
        }
        return result.toArray(new Point[0]);
    }
}
