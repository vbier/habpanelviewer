package de.vier_bier.habpanelviewer.reporting.motion;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.util.Log;
import android.view.TextureView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Generic camera implementation.
 */
public class Camera {
    private static final String TAG = "Camera";

    private Activity mContext;
    private TextureView mPreviewView;

    private ICamera mImplementation;
    private SurfaceTexture mSurface;

    private CameraVersion mVersion;
    private int mDeviceOrientation;
    private boolean mShowPreview;

    private List<ICamera.ILumaListener> mListeners = new ArrayList<>();

    public Camera(Activity ctx, TextureView tv, SharedPreferences prefs) {
        mContext = ctx;
        mPreviewView = tv;

        EventBus.getDefault().register(this);

        mVersion = getCameraVersion(prefs);
        mDeviceOrientation = ctx.getWindowManager().getDefaultDisplay().getRotation() * 90;
        mImplementation = createCamera(mVersion);

        for (ICamera.ILumaListener l : mListeners) {
            mImplementation.addLumaListener(l);
        }
    }


    public void setDeviceRotation(int rotation) {
        if (rotation != mDeviceOrientation) {
            mImplementation.setDeviceOrientation(rotation);
            mDeviceOrientation = rotation;
        }
    }

    public synchronized void updateFromPreferences(SharedPreferences prefs) throws CameraException {
        CameraVersion v = getCameraVersion(prefs);

        // if camera api changed, close old camera, create new one
        if (v != mVersion) {
            if (isPreviewRunning()) {
                mImplementation.stopPreview();
            }
            if (isCameraLocked()) {
                mImplementation.unlockCamera();
            }

            mImplementation = createCamera(v);

            for (ICamera.ILumaListener l : mListeners) {
                mImplementation.addLumaListener(l);
            }
        }

        mShowPreview = prefs.getBoolean("pref_motion_detection_preview", false);
        if (mShowPreview) {
            int deviceOrientation = mContext.getWindowManager().getDefaultDisplay().getRotation() * 90;

            if (deviceOrientation != mDeviceOrientation) {
                mImplementation.setDeviceOrientation(deviceOrientation);
                mDeviceOrientation = deviceOrientation;
            }
        }

        // ensure camera preview state is correct
        // - preview state ON or lumalisteners -> preview running
        // - preview state OFF and no lumalisteners -> preview not running
        boolean shouldRun = mShowPreview || !mListeners.isEmpty();
        if (shouldRun && !isPreviewRunning()) {
            if (!isCameraLocked()) {
                lockCamera();
            }

            registerSurfaceListener(new ICamera.LoggingPreviewListener());
        } else if (isPreviewRunning() && !shouldRun) {
            mImplementation.stopPreview();

            if (isCameraLocked()) {
                mImplementation.unlockCamera();
            }
        }

        // make sure preview view has the correct size
        if (mShowPreview) {
            mPreviewView.getLayoutParams().height = 480;
            mPreviewView.getLayoutParams().width = 640;
        } else {
            // if we have no preview, we still need a visible
            // TextureView in order to have a working motion detection.
            // Resize it to 1x1pxs so it does not get in the way.
            mPreviewView.getLayoutParams().height = 1;
            mPreviewView.getLayoutParams().width = 1;
        }
        mPreviewView.setLayoutParams(mPreviewView.getLayoutParams());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        if (isPreviewRunning()) {
            status.set(mContext.getString(R.string.pref_camera), mContext.getString(R.string.enabled) + "\n"
                    + mContext.getString(R.string.resolution, 640, 480) + "\n"
                    + (mVersion == CameraVersion.V1 ? mContext.getString(R.string.camApiPreLollipop) : mContext.getString(R.string.camApi2)));
        } else if (mVersion == CameraVersion.NONE) {
            status.set(mContext.getString(R.string.pref_camera), ((CameraImplNone) mImplementation).getMessage());
        } else {
            status.set(mContext.getString(R.string.pref_camera), mContext.getString(R.string.disabled));
        }
    }

    public synchronized void terminate() {
        EventBus.getDefault().unregister(this);
        mImplementation = null;
    }

    public int getSensorOrientation() {
        return mImplementation.getCameraOrientation();
    }

    synchronized void addLumaListener(ICamera.ILumaListener l) throws CameraException {
        mListeners.add(l);

        if (mImplementation != null) {
            mImplementation.addLumaListener(l);

            if (mListeners.size() == 1 && !mShowPreview) {
                startPreview(new ICamera.LoggingPreviewListener());
            }
        }
    }

    synchronized void removeLumaListener(ICamera.ILumaListener l) throws CameraException {
        mListeners.remove(l);

        if (mImplementation != null) {
            mImplementation.removeLumaListener(l);

            if (mListeners.isEmpty() && !mShowPreview) {
                stopPreview();
            }
        }
    }

    public synchronized void takePicture(ICamera.IPictureListener h,
                                         int takeDelay) throws CameraException {
        boolean wasPreviewRunning = isPreviewRunning();

        final ICamera.IPreviewListener pl = new ICamera.IPreviewListener() {
            @Override
            public void started() {
                h.progress(mContext.getString(R.string.previewStarted));

                if (!wasPreviewRunning) {
                    try {
                        Thread.sleep(takeDelay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                mImplementation.takePicture(new ICamera.IPictureListener() {
                    @Override
                    public void picture(byte[] data) {
                        h.progress(mContext.getString(R.string.pictureTaken));
                        Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);

                        int mDeviceRotation = mContext.getWindowManager().getDefaultDisplay().getRotation();
                        Matrix matrix = new Matrix();
                        int rotation = getSensorOrientation() + 90 * mDeviceRotation;
                        matrix.postRotate(rotation);

                        Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);

                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        rotated.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                        h.picture(stream.toByteArray());

                        try {
                            stopPreview();

                            if (wasPreviewRunning) {
                                startPreview(new ICamera.LoggingPreviewListener());
                            }
                        } catch (CameraException e) {
                            h.error(e.getMessage());
                        }
                    }

                    @Override
                    public void error(String message) {
                        h.error(message);
                        Log.e(TAG, message);
                    }

                    @Override
                    public void progress(String message) {
                        Log.d(TAG, message);
                        h.progress(message);
                    }
                });
            }

            @Override
            public void error(String message) {
                h.error(message);
                Log.e(TAG, message);
            }

            @Override
            public void exception(Exception e) {
                h.error(e.getMessage());
                Log.e(TAG, "", e);
            }

            @Override
            public void progress(String message) {
                Log.d(TAG, message);
                h.progress(message);
            }
        };

        startPreview(pl);
    }

    private synchronized void startPreview(ICamera.IPreviewListener previewListener) throws CameraException {
        if (!isCameraLocked()) {
            previewListener.progress(mContext.getString(R.string.lockingCamera));
            lockCamera();
        }

        if (!mImplementation.isPreviewRunning()) {
            if (mSurface == null) {
                registerSurfaceListener(previewListener);
            } else {
                previewListener.progress(mContext.getString(R.string.startingPreview));
                mImplementation.startPreview(mSurface, previewListener);
            }
        } else {
            previewListener.started();
        }
    }

    private synchronized void stopPreview() throws CameraException {
        if (mImplementation != null && mImplementation.isPreviewRunning()) {
            mImplementation.stopPreview();
        }

        if (mImplementation != null && mImplementation.isCameraLocked()) {
            mImplementation.unlockCamera();
        }
    }

    private void lockCamera() throws CameraException {
        if (!mImplementation.isCameraLocked()) {
            mImplementation.lockCamera();
        }
    }

    private boolean isCameraLocked() {
        return mImplementation != null && mImplementation.isCameraLocked();
    }

    private boolean isPreviewRunning() {
        return mImplementation != null && mImplementation.isPreviewRunning();
    }

    private void registerSurfaceListener(ICamera.IPreviewListener previewListener) {
        if (mSurface == null && mPreviewView.getSurfaceTexture() != null) {
            mSurface = mPreviewView.getSurfaceTexture();
        }

        if (mSurface != null) {
            mImplementation.startPreview(mSurface, previewListener);
            return;
        }

        previewListener.progress(mContext.getString(R.string.waitingSurface));
        mPreviewView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                if (surfaceTexture != mSurface) {
                    previewListener.progress(mContext.getString(R.string.surfaceObtained));
                    mSurface = surfaceTexture;
                    mImplementation.startPreview(mSurface, previewListener);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
                if (surfaceTexture != mSurface) {
                    previewListener.progress(mContext.getString(R.string.surfaceObtained));
                    mSurface = surfaceTexture;
                    mImplementation.startPreview(mSurface, previewListener);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                Log.d(TAG, "mSurface texture destroyed: " + surfaceTexture);

                mSurface = null;
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            }
        });
    }

    private ICamera createCamera(CameraVersion version) {
        try {
            if (version == CameraVersion.V2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mVersion = CameraVersion.V2;
                return new CameraImplV2(mContext, mPreviewView, mDeviceOrientation);
            }

            mVersion = CameraVersion.V1;
            return new CameraImplV1(mContext, mPreviewView, mDeviceOrientation);
        } catch (CameraException e) {
            mVersion = CameraVersion.NONE;
            return new CameraImplNone(e.getMessage());
        }
    }

    private CameraVersion getCameraVersion(SharedPreferences prefs) {
        boolean newApi = prefs.getBoolean("pref_motion_detection_new_api", Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);

        if (newApi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return CameraVersion.V2;
        }

        return CameraVersion.V1;
    }

    public boolean isValid() {
        return mVersion != CameraVersion.NONE && mImplementation != null;
    }

    enum CameraVersion {
        NONE, V1, V2
    }
}
