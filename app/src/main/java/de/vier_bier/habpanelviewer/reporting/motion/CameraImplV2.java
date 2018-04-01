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
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
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
    private static final String TAG = "CameraImplV2";

    private final CameraManager mCamManager;
    private HandlerThread mPreviewThread;
    private Handler mPreviewHandler;
    private HandlerThread mPictureThread;
    private Handler mPictureHandler;

    private CameraDevice mCamera;
    private String mCameraId;
    private int mCameraOrientation;

    private CameraCaptureSession mCaptureSession;
    private volatile boolean mPreviewRunning;
    private ImageReader mImageReader; //do not use a variable as this gets GC'ed

    CameraImplV2(Activity context, TextureView prevView, int deviceOrientation) throws CameraException {
        super(context, prevView, deviceOrientation);

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
                    throw new CameraException("Required permission missing: " + Manifest.permission.CAMERA);
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
                        mCamera = null;
                        latch.countDown();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice cameraDevice, int i) {
                        Log.e(TAG, "mCamera error: " + cameraDevice + ", error code: " + i);
                        mCamera = null;
                        latch.countDown();
                    }
                }, mPreviewHandler);

                try {
                    latch.await(2, TimeUnit.SECONDS);
                    if (latch.getCount() == 1) {
                        throw new CameraException("Opening camera timed out");
                    }
                } catch (InterruptedException e) {
                    throw new CameraException("Got interrupt while opening camera");
                }
            } catch (CameraAccessException e) {
                throw new CameraException(e);
            }
        } else {
            throw new CameraException(mActivity.getString(R.string.frontCameraMissing));
        }
    }

    @Override
    public void setDeviceOrientation(int deviceOrientation) {
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
                    mDeviceOrientation = deviceOrientation;
                    setDeviceOrientation(previewSize);
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            mDeviceOrientation = deviceOrientation;
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
        }
    }

    @Override
    public boolean isCameraLocked() {
        return mCamera != null;
    }

    @Override
    public void startPreview(SurfaceTexture surface, IPreviewListener previewListener) {
        if (mPreviewRunning) {
            previewListener.started();
            return;
        }

        if (mCamera != null) {
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
                mImageReader.setOnImageAvailableListener(reader -> {
                    if (!mPreviewRunning) {
                        mPreviewRunning = true;
                        previewListener.started();
                    }

                    Image i = reader.acquireLatestImage();

                    try {
                        LumaData ld = null;
                        for (ILumaListener l : mListeners) {
                            if (l.needsPreview()) {
                                if (ld == null && i != null) {
                                    Log.v(TAG, "preview image available and needed: size " + i.getWidth() + "x" + i.getHeight());

                                    ByteBuffer luma = i.getPlanes()[0].getBuffer();
                                    final byte[] data = new byte[luma.capacity()];
                                    luma.get(data);

                                    ld = new LumaData(data, i.getWidth(), i.getHeight());
                                }
                                l.preview(ld);
                            }
                        }
                    } finally {
                        if (i != null) {
                            i.close();
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

                mCamera.createCaptureSession(surfaces,
                        new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                Log.d(TAG, "Capture session configured");

                                // The mCamera is already closed
                                if (null == mCamera) {
                                    previewListener.error("Capture session has no camera");
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
                                } catch (CameraAccessException e) {
                                    previewListener.exception(e);
                                    previewListener.error("Could not create preview request");
                                }
                            }

                            @Override
                            public void onClosed(@NonNull CameraCaptureSession session) {
                                super.onClosed(session);
                                mPreviewRunning = false;
                            }

                            @Override
                            public void onConfigureFailed(
                                    @NonNull CameraCaptureSession cameraCaptureSession) {
                                previewListener.error("Could not create capture session");
                            }
                        }, mPreviewHandler
                );
            } catch (CameraAccessException e) {
                previewListener.exception(e);
                previewListener.error("Could not create preview");
            }
        } else {
            previewListener.error("Camera not found");
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
                    for (int i = 0; i < 10 && mPreviewRunning; i++) {
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                if (mPreviewRunning) {
                    throw new CameraException("could not stop preview");
                }
                mCaptureSession = null;
            } catch (IllegalStateException e) {
                // camera session has been closed, no problem
            }
        }
    }

    @Override
    public boolean isPreviewRunning() {
        return mPreviewRunning;
    }

    @Override
    public void takePicture(IPictureListener iPictureHandler) {
        if (mCamera != null) {
            if (mPictureThread == null) {
                mPictureThread = new HandlerThread("takePictureThread");
                mPictureThread.start();
                mPictureHandler = new Handler(mPictureThread.getLooper());
            }

            try {
                int width = 640;
                int height = 480;

                ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
                List<Surface> outputSurfaces = new ArrayList<>(2);
                outputSurfaces.add(reader.getSurface());
                final CaptureRequest.Builder captureBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                captureBuilder.addTarget(reader.getSurface());
                captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                ImageReader.OnImageAvailableListener readerListener = reader1 -> {
                    Image image = null;
                    try {
                        image = reader1.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);

                        iPictureHandler.picture(bytes);
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                };
                reader.setOnImageAvailableListener(readerListener, mPictureHandler);
                final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                    }
                };


                mCamera.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try {
                            session.capture(captureBuilder.build(), captureListener, mPictureHandler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    }
                }, mPictureHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else {
            throw new IllegalStateException("Motion detection not running");
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
        return result.toArray(new Point[result.size()]);
    }
}
