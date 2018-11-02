package de.vier_bier.habpanelviewer.reporting.motion;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.core.app.ActivityCompat;
import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.UiUtil;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Generic camera implementation that delegates to the V1 and V2 camera implementations.
 */
public class Camera {
    public final static int MY_REQUEST_CAMERA = 42;

    private static final String TAG = "HPV-Camera";

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private final Activity mContext;
    private final TextureView mPreviewView;

    private ICamera mImplementation;
    private SurfaceTexture mSurface;

    private CameraVersion mVersion;
    private boolean mShowPreview;

    private final List<ICamera.ILumaListener> mListeners = new ArrayList<>();

    public Camera(Activity ctx, TextureView tv, SharedPreferences prefs) {
        mContext = ctx;
        mPreviewView = tv;

        EventBus.getDefault().register(this);

        mVersion = getCameraVersion(prefs);
        mImplementation = createCamera(mVersion);
    }

    public void setDeviceRotation(int rotation) {
        mExecutor.submit(() -> mImplementation.setDeviceRotation(rotation));
    }

    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        mExecutor.submit(() -> doUpdateFromPreferences(prefs));
    }

    public void terminate() {
        mExecutor.submit(this::doTerminate);
    }

    public int getSensorOrientation() {
        // this is intentionally not done in the worker thread, as it is only called in
        // MainActivity.onCreate and in code already running in the wt.
        return mImplementation.getCameraOrientation();
    }

    public void takePicture(ICamera.IPictureListener h,
                            int takeDelay,
                            int compQuality) {
        mExecutor.submit(() -> {
            try {
                doTakePicture(h, takeDelay, compQuality);
            } catch (CameraException e) {
                h.error(e.getLocalizedMessage());
            }
        });
    }

    public boolean canBeUsed() {
        return mVersion != CameraVersion.NONE;
    }

    void addLumaListener(ICamera.ILumaListener l) {
        synchronized (mListeners) {
            mListeners.add(l);
            mImplementation.addLumaListener(l);
        }

        mExecutor.submit(() -> {
            if (mListeners.size() > 0 && !isPreviewRunning() && !mShowPreview) {
                try {
                    startPreview(new ICamera.LoggingPreviewListener());
                } catch (CameraException e) {
                    Log.e(TAG, "Could not enable MotionDetector", e);
                }
            }
        });
    }

    void removeLumaListener(ICamera.ILumaListener l) {
        synchronized (mListeners) {
            mListeners.remove(l);
            mImplementation.removeLumaListener(l);
        }

        mExecutor.submit(() -> {
            if (mListeners.isEmpty() && !mShowPreview) {
                try {
                    stopPreview();
                } catch (CameraException e) {
                    Log.e(TAG, "Could not disable MotionDetector", e);
                }
            }
        });
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

    private synchronized void doUpdateFromPreferences(SharedPreferences prefs) {
        try {
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
        } catch (CameraException e) {
            UiUtil.showSnackBar(mPreviewView, e.getMessage());
        }
    }

    private synchronized void doTerminate() {
        EventBus.getDefault().unregister(this);

        if (isPreviewRunning()) {
            try {
                stopPreview();
            } catch (CameraException e) {
                Log.e(TAG, "failed to stop preview on termination", e);
            }
        }

        mVersion = CameraVersion.NONE;
        mImplementation = new CameraImplNone("camera terminated");
    }


    private synchronized void doTakePicture(ICamera.IPictureListener h,
                                         int takeDelay,
                                         int compQuality) throws CameraException {
        boolean wasPreviewRunning = isPreviewRunning();

        final ICamera.IPreviewListener pl = new ICamera.IPreviewListener() {
            @Override
            public void started() {
                h.progress(mContext.getString(R.string.previewStarted));

                if (!wasPreviewRunning) {
                    try {
                        Thread.sleep(takeDelay);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "interrupted while waiting for take picture delay");
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
                        rotated.compress(Bitmap.CompressFormat.JPEG, compQuality, stream);
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
        if (mImplementation.isPreviewRunning()) {
            mImplementation.stopPreview();
        }

        if (mImplementation.isCameraLocked()) {
            mImplementation.unlockCamera();
        }
    }

    private void lockCamera() throws CameraException {
        if (!mImplementation.isCameraLocked()) {
            mImplementation.lockCamera();
        }
    }

    private boolean isCameraLocked() {
        return mImplementation.isCameraLocked();
    }

    private boolean isPreviewRunning() {
        return mImplementation.isPreviewRunning();
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
                Log.d(TAG, "surface destroyed: " + surfaceTexture);
                try {
                    mImplementation.stopPreview();
                } catch (CameraException e) {
                    Log.e(TAG, "Error stopping preview", e);
                    previewListener.error("Failed to stop preview: " + e.getMessage());
                }

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
            if (version == CameraVersion.PERMISSION_MISSING) {
                return new CameraImplNone("Required permission missing: " + Manifest.permission.CAMERA);
            }

            if (version == CameraVersion.V2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mVersion = CameraVersion.V2;
                return new CameraImplV2(mContext, mPreviewView);
            }

            mVersion = CameraVersion.V1;
            return new CameraImplV1(mContext, mPreviewView);
        } catch (CameraException e) {
            mVersion = CameraVersion.NONE;
            return new CameraImplNone(e.getMessage());
        }
    }

    private CameraVersion getCameraVersion(SharedPreferences prefs) {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            boolean shouldRun = prefs.getBoolean("pref_motion_detection_preview", false) || !mListeners.isEmpty();
            if (shouldRun) {
                ActivityCompat.requestPermissions(mContext, new String[]{Manifest.permission.CAMERA},
                        MY_REQUEST_CAMERA);
            }

            return CameraVersion.PERMISSION_MISSING;
        }

        boolean newApi = prefs.getBoolean("pref_motion_detection_new_api", Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
        if (newApi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return CameraVersion.V2;
        }

        return CameraVersion.V1;
    }

    enum CameraVersion {
        PERMISSION_MISSING, NONE, V1, V2
    }
}
