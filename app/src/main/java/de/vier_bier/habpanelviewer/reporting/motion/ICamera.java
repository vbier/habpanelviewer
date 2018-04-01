package de.vier_bier.habpanelviewer.reporting.motion;

import android.graphics.SurfaceTexture;
import android.util.Log;

/**
 * Generic camera interface.
 */
public interface ICamera {
    void lockCamera() throws CameraException;

    void unlockCamera();

    boolean isCameraLocked();

    void startPreview(SurfaceTexture surfaceTexture, IPreviewListener previewListener);

    void stopPreview() throws CameraException;

    boolean isPreviewRunning();

    void addLumaListener(ILumaListener l);

    void removeLumaListener(ILumaListener l);

    int getCameraOrientation();

    void takePicture(IPictureListener iPictureHandler);

    void setDeviceOrientation(int deviceOrientation);

    interface ILumaListener {
        void preview(LumaData previewBytes);

        boolean needsPreview();
    }

    interface IPictureListener {
        void picture(byte[] data);

        void error(String message);

        void progress(String message);
    }

    interface IPreviewListener {
        void started();

        void error(String message);

        void exception(Exception e);

        void progress(String message);
    }

    class LoggingPreviewListener implements IPreviewListener {
        private static final String TAG = "LoggingPreviewListener";

        @Override
        public void started() {
            Log.d(TAG, "preview started");
        }

        @Override
        public void error(String message) {
            Log.e(TAG, "preview error: " + message);
        }

        @Override
        public void exception(Exception e) {
            Log.e(TAG, "preview error", e);
        }

        @Override
        public void progress(String message) {
            Log.d(TAG, "preview progress: " + message);
        }
    }
}
