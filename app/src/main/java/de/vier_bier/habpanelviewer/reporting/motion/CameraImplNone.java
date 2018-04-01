package de.vier_bier.habpanelviewer.reporting.motion;

import android.graphics.SurfaceTexture;

public class CameraImplNone implements ICamera {
    private String mErrMsg;

    CameraImplNone(String error) {
        mErrMsg = error;
    }

    @Override
    public void lockCamera() {
    }

    @Override
    public void unlockCamera() {
    }

    @Override
    public boolean isCameraLocked() {
        return false;
    }

    @Override
    public void startPreview(SurfaceTexture surfaceTexture, IPreviewListener previewListener) {
        previewListener.error("");
        previewListener.started();
    }

    @Override
    public void stopPreview() {
    }

    @Override
    public boolean isPreviewRunning() {
        return false;
    }

    @Override
    public void addLumaListener(ILumaListener l) {
    }

    @Override
    public void removeLumaListener(ILumaListener l) {
    }

    @Override
    public int getCameraOrientation() {
        return 0;
    }

    @Override
    public void takePicture(IPictureListener iPictureHandler) {
    }

    @Override
    public void setDeviceOrientation(int deviceOrientation) {
    }

    public String getMessage() {
        return mErrMsg;
    }
}
