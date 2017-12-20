package de.vier_bier.habpanelviewer.reporting.motion;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.Camera;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;

/**
 * Motion detection using the old Camera API.
 */
@SuppressWarnings("deprecation")
public class MotionDetector extends AbstractMotionDetector<ImageData> {
    private static final String TAG = "MotionDetector";

    private boolean mRunning;
    private Camera mCamera;

    public MotionDetector(Activity context, MotionListener l, ServerConnection serverConnection) {
        super(context, l, serverConnection);
    }

    @Override
    protected int getSensorOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(Integer.parseInt(mCameraId), info);

        return info.orientation;
    }

    @Override
    protected LumaData getPreviewLumaData() {
        ImageData p = mPreview.getAndSet(null);
        if (p != null) {
            return p.extractLumaData(mBoxes);
        }

        return null;
    }

    protected String createCamera(int deviceDegrees) throws CameraException {
        if (mCamera == null) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    mCamera = Camera.open(i);

                    int result = (info.orientation + deviceDegrees) % 360;
                    result = (360 - result) % 360;
                    mCamera.setDisplayOrientation(result);

                    return String.valueOf(i);
                }
            }

            throw new CameraException(mContext.getString(R.string.frontCameraMissing));
        }

        return null;
    }

    protected synchronized void startPreview() {
        if (mEnabled && !mRunning && mCamera != null && mSurface != null) {
            try {
                mCamera.stopPreview();
                mCamera.setPreviewTexture(mSurface);

                Camera.Parameters parameters = mCamera.getParameters();
                chooseOptimalSize(toPointArray(parameters.getSupportedPictureSizes()), 640, 480, new Point(640, 480));

                parameters.setPreviewSize(mPreviewSize.x, mPreviewSize.y);
                mCamera.setParameters(parameters);

                mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                    @Override
                    public void onPreviewFrame(byte[] bytes, Camera camera) {
                        if (mCamera == camera && mPreview.get() == null) {
                            Log.v(TAG, "preview image available: size " + mPreviewSize.x + "x" + mPreviewSize.y);

                            setPreview(new ImageData(bytes, mPreviewSize.x, mPreviewSize.y));
                        }
                    }
                });

                mCamera.startPreview();
                mRunning = true;
            } catch (IOException e) {
                Log.e(TAG, "Error setting preview texture", e);
            }
        }
    }

    private Point[] toPointArray(List<Camera.Size> supportedPictureSizes) {
        ArrayList<Point> result = new ArrayList<>();
        for (Camera.Size s : supportedPictureSizes) {
            result.add(new Point(s.width, s.height));
        }
        return result.toArray(new Point[result.size()]);
    }

    @Override
    protected synchronized void stopPreview() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();

            Camera c = mCamera;
            mCamera = null;
            c.release();

            mRunning = false;
        }
    }

    @Override
    protected String getCameraInfo() {
        String camStr = mContext.getString(R.string.camApiPreLollipop) + "\n";
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, info);
            camStr += mContext.getString(R.string.cameraId, String.valueOf(i)) + ": ";

            boolean hasFlash = mContext.getApplicationContext().getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);

            camStr += (hasFlash ? mContext.getString(R.string.has) : mContext.getString(R.string.no)) + " " + mContext.getString(R.string.flash) + ", ";
            camStr += (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK ?
                    mContext.getString(R.string.backFacing) : mContext.getString(R.string.frontFacing)) + "\n";
        }

        return camStr.trim();
    }
}