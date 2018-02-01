package de.vier_bier.habpanelviewer.reporting.motion;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;

/**
 * Motion detection using Camera2 API.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MotionDetectorCamera2 extends AbstractMotionDetector<LumaData> {
    private static final String TAG = "MotionDetectorCamera2";

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private Activity mActivity;
    private CameraManager mCamManager;
    private TextureView mPreviewView;

    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private CameraCaptureSession mCaptureSession;

    private CameraDevice mCamera;

    public MotionDetectorCamera2(Activity context, CameraManager manager, MotionListener l, Activity act, ServerConnection serverConnection) {
        super(context, l, serverConnection);

        Log.d(TAG, "instantiating motion detection");

        mCamManager = manager;
        mActivity = act;
    }

    @Override
    protected LumaData getPreviewLumaData() {
        return getPreview();
    }

    @Override
    protected synchronized void startDetection(TextureView textureView, int deviceDegrees) throws CameraException {
        mPreviewView = textureView;
        super.startDetection(textureView, deviceDegrees);
    }

    @Override
    protected String createCamera(int deviceDegrees) throws CameraException {
        try {
            for (String camId : mCamManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mCamManager.getCameraCharacteristics(camId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    Log.d(TAG, "front-facing mCamera found: " + camId);
                    return camId;
                }
            }
        } catch (CameraAccessException e) {
            throw new CameraException(e);
        }

        throw new CameraException(mActivity.getString(R.string.frontCameraMissing));
    }

    protected void stopPreview() {
        if (mCamera != null) {
            mCamera.close();
            mCamera = null;
        }
    }

    private Point[] toPointArray(Size[] supportedPictureSizes) {
        ArrayList<Point> result = new ArrayList<>();
        for (Size s : supportedPictureSizes) {
            result.add(new Point(s.getWidth(), s.getHeight()));
        }
        return result.toArray(new Point[result.size()]);
    }

    protected void startPreview() {
        try {
            CameraCharacteristics characteristics
                    = mCamManager.getCameraCharacteristics(String.valueOf(mCameraId));
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            chooseOptimalSize(toPointArray(map.getOutputSizes(ImageFormat.YUV_420_888)), 640, 480, new Point(4, 3));

            mCamManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice) {
                    Log.d(TAG, "mCamera opened: " + cameraDevice);
                    mCamera = cameraDevice;
                    createCameraPreviewSession(mPreviewView);
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    Log.d(TAG, "mCamera disconnected: " + cameraDevice);
                    stopDetection();
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int i) {
                    Log.e(TAG, "mCamera error: " + cameraDevice + ", error code: " + i);
                    stopDetection();
                }
            }, null);
        } catch (CameraAccessException | SecurityException e) {
            Log.e(TAG, "Could not open camera", e);
        }
    }

    private void configureTransform(TextureView textureView) {
        if (null == textureView || null == mPreviewSize || null == mActivity) {
            return;
        }
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, textureView.getWidth(), textureView.getHeight());
        RectF bufferRect = new RectF(0, 0, mPreviewSize.y, mPreviewSize.x);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) textureView.getHeight() / mPreviewSize.y,
                    (float) textureView.getWidth() / mPreviewSize.x);
            matrix.postScale(scale, scale, centerX, centerY);
        }
        matrix.postRotate(-90 * rotation, centerX, centerY);
        textureView.setTransform(matrix);
    }

    private void createCameraPreviewSession(final TextureView previewView) {
        try {
            Log.v(TAG, "preview image size is " + mPreviewSize.x + "x" + mPreviewSize.y);

            configureTransform(previewView);

            ImageReader mImageReader = ImageReader.newInstance(mPreviewSize.x, mPreviewSize.y,
                    ImageFormat.YUV_420_888, 2);
            mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image i = reader.acquireLatestImage();

                    if (i != null) {
                        // only process if we do not yet have a buffered preview image
                        if (previewMissing()) {
                            Log.v(TAG, "preview image available: size " + i.getWidth() + "x" + i.getHeight());

                            ByteBuffer luma = i.getPlanes()[0].getBuffer();
                            final byte[] data = new byte[luma.capacity()];
                            luma.get(data);

                            setPreview(new LumaData(data, i.getWidth(), i.getHeight(), mBoxes));
                        }
                        i.close();
                    }
                }
            }, null);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());
            Surface previewSurface = new Surface(mSurface);
            mPreviewRequestBuilder.addTarget(previewSurface);

            ArrayList<Surface> surfaces = new ArrayList<>();
            surfaces.add(mImageReader.getSurface());
            surfaces.add(previewSurface);

            // Here, we create a CameraCaptureSession for mCamera preview.
            mCamera.createCaptureSession(surfaces,
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

    @Override
    protected int getSensorOrientation() {
        try {
            CameraCharacteristics characteristics
                    = mCamManager.getCameraCharacteristics(String.valueOf(mCameraId));
            return characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Couldn't find out camera sensor orientation");
        }

        return 0;
    }

    @Override
    protected String getCameraInfo() {
        StringBuilder camStr = new StringBuilder(mContext.getString(R.string.camApi2) + "\n");
        CameraManager camManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String camId : camManager.getCameraIdList()) {
                camStr.append(mContext.getString(R.string.cameraId, camId)).append(": ");

                CameraCharacteristics characteristics = camManager.getCameraCharacteristics(camId);
                Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

                camStr.append(hasFlash ? mContext.getString(R.string.has) : mContext.getString(R.string.no)).append(" ").append(mContext.getString(R.string.flash)).append(", ");
                camStr.append(facing == CameraCharacteristics.LENS_FACING_BACK ?
                        mContext.getString(R.string.backFacing) : mContext.getString(R.string.frontFacing)).append("\n");
            }
        } catch (CameraAccessException e) {
            camStr = new StringBuilder(mActivity.getString(R.string.failedAccessCamera) + ":" + e.getMessage());
        }

        return camStr.toString().trim();
    }
}
