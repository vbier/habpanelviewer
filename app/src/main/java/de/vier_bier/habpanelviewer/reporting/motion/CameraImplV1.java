package de.vier_bier.habpanelviewer.reporting.motion;

import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;
import android.view.TextureView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.vier_bier.habpanelviewer.R;

/**
 * Concrete camera implementation using old camera API.
 */
class CameraImplV1 extends AbstractCameraImpl {
    private static final String TAG = "HPV-CameraImplV1";

    private Camera mCamera;
    private volatile boolean mPreviewRunning;
    private int mCameraId = -1;
    private int mCameraOrientation = 0;

    CameraImplV1(Activity context, TextureView previewView) throws CameraException {
        super(context, previewView);

        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCameraId = i;
                mCameraOrientation = info.orientation;

                Log.v(TAG, "found front-facing camera with id " + i + " and orientation " + mCameraOrientation);
            }
        }

        if (mCameraId == -1) {
            throw new CameraException(mActivity.getString(R.string.frontCameraMissing));
        }
    }


    @Override
    public void lockCamera() throws CameraException {
        if (mCamera == null && mCameraId != -1) {
            try {
                mCamera = Camera.open(mCameraId);
                setDeviceRotation(mDeviceOrientation);
            } catch (RuntimeException e) {
                throw new CameraException(mActivity.getString(R.string.openCameraFailed), e);
            }
        } else {
            throw new CameraException(mActivity.getString(R.string.frontCameraMissing));
        }
    }

    @Override
    public void setDeviceRotation(int deviceRotation) {
        if (mCamera != null) {
            mActivity.runOnUiThread(() -> mPreviewView.setTransform(new Matrix()));

            int result = (mCameraOrientation + deviceRotation * 90) % 360;
            result = (360 - result) % 360;

            Log.v(TAG, "setting camera display orientation " + result);
            mCamera.setDisplayOrientation(result);
            mDeviceOrientation = deviceRotation;
        }
    }

    @Override
    public void unlockCamera() {
        if (mCamera != null) {
            mCamera.release();
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
                mCamera.setPreviewTexture(surface);

                Camera.Parameters parameters = mCamera.getParameters();

                Point previewSize = chooseOptimalSize(toPointArray(parameters.getSupportedPictureSizes()));
                parameters.setPreviewSize(previewSize.x, previewSize.y);
                parameters.setPictureSize(previewSize.x, previewSize.y);

                mCamera.setParameters(parameters);
                mCamera.setPreviewCallback((bytes, camera) -> {
                    if (!mPreviewRunning) {
                        mPreviewRunning = true;
                        previewListener.started();
                    }

                    for (ILumaListener l : mListeners) {
                        if (l.needsPreview()) {
                            Log.v(TAG, "preview image available and needed: size " + previewSize.x + "x" + previewSize.y);
                            l.preview(LumaData.extractLuma(bytes, previewSize.x, previewSize.y));
                        }
                    }
                });

                mCamera.startPreview();
            } catch (IOException e) {
                previewListener.exception(e);
                previewListener.error(mActivity.getString(R.string.errorSettingTexture));
            }
        } else {
            previewListener.error(mActivity.getString(R.string.frontCameraMissing));
        }
    }

    @Override
    public void stopPreview() {
        if (mCamera != null && mPreviewRunning) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mPreviewRunning = false;
        }
    }

    @Override
    public boolean isPreviewRunning() {
        return mPreviewRunning;
    }

    @Override
    public void takePicture(IPictureListener iPictureHandler) {
        if (isPreviewRunning()) {
            mCamera.takePicture(null, null, (bytes, camera) -> {
                byte[] data = new byte[bytes.length];
                System.arraycopy(bytes, 0, data, 0, bytes.length);

                iPictureHandler.picture(data);
            });
        } else {
            throw new IllegalStateException("Preview not running");
        }
    }

    @Override
    public int getCameraOrientation() {
        return mCameraOrientation;
    }

    private Point[] toPointArray(List<Camera.Size> supportedPictureSizes) {
        ArrayList<Point> result = new ArrayList<>();
        for (Camera.Size s : supportedPictureSizes) {
            result.add(new Point(s.width, s.height));
        }
        return result.toArray(new Point[0]);
    }

}
