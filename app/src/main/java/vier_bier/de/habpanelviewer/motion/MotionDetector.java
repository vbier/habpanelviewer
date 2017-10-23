package vier_bier.de.habpanelviewer.motion;

import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.util.Log;
import android.util.Size;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Motion detection using the old Camera API.
 */
@SuppressWarnings("deprecation")
public class MotionDetector extends AbstractMotionDetector<ImageData> {
    private static final String TAG = "MotionDetector";

    private boolean mRunning;
    private Camera mCamera;

    public MotionDetector(MotionListener l) {
        super(l);
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

    protected String createCamera(int deviceDegrees) throws CameraAccessException {
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

            throw new CameraAccessException(CameraAccessException.CAMERA_ERROR, "Could not find front facing camera!");
        }

        return null;
    }

    protected synchronized void startPreview() {
        if (mEnabled && !mRunning && mCamera != null && mSurface != null) {
            try {
                mCamera.stopPreview();
                mCamera.setPreviewTexture(mSurface);

                Camera.Parameters parameters = mCamera.getParameters();
                chooseOptimalSize(toSizeArray(parameters.getSupportedPictureSizes()), 640, 480, new Size(640, 480));

                parameters.setPreviewSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                mCamera.setParameters(parameters);

                Log.d(TAG, "preview size: " + mCamera.getParameters().getPreviewSize().width + "x" + mCamera.getParameters().getPreviewSize().height);

                mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                    @Override
                    public void onPreviewFrame(byte[] bytes, Camera camera) {
                        if (mCamera == camera) {
                            setPreview(new ImageData(bytes, camera.getParameters().getPreviewSize().width, camera.getParameters().getPreviewSize().height));
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

    private Size[] toSizeArray(List<Camera.Size> supportedPictureSizes) {
        ArrayList<Size> result = new ArrayList<>();
        for (Camera.Size s : supportedPictureSizes) {
            result.add(new Size(s.width, s.height));
        }
        return result.toArray(new Size[result.size()]);
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
}
