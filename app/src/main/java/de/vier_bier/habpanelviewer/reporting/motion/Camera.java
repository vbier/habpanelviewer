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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.TextureView;
import android.view.ViewGroup;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final Handler mWorkHandler;

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

        HandlerThread mWorker = new HandlerThread("CameraWorker");
        mWorker.start();
        mWorkHandler = new Handler(mWorker.getLooper());

        EventBus.getDefault().register(this);

        mVersion = getCameraVersion(prefs);
        mImplementation = createCamera(mVersion);
    }

    @Override
    protected void finalize() throws Throwable {
        stopPreview();

        super.finalize();
    }

    public void setDeviceRotation(int rotation) {
        mWorkHandler.post(() -> {
            Log.d(TAG, "setting orientation...");
            mImplementation.setDeviceRotation(rotation);
            Log.d(TAG, "setting orientation finished");
        });
    }

    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        mWorkHandler.post(() -> {
            Log.d(TAG, "updating from preferences...");
            doUpdateFromPreferences(prefs);
            Log.d(TAG, "updating from preferences finished");
        });
    }

    public void terminate() {
        mWorkHandler.post(() -> {
            Log.d(TAG, "terminating...");
            doTerminate();
            Log.d(TAG, "terminating finished");
        });
    }

    public int getSensorOrientation() {
        // this is intentionally not done in the worker thread, as it is only called in
        // MainActivity.onCreate and in code already running in the wt.
        return mImplementation.getCameraOrientation();
    }

    public void takePicture(ICamera.IPictureListener h,
                            int takeDelay,
                            int compQuality) {
        mWorkHandler.post(() -> {
            Log.d(TAG, "taking picture...");
            try {
                doTakePicture(h, takeDelay, compQuality);
            } catch (CameraException e) {
                h.error(e.getLocalizedMessage());
            } finally {
                Log.d(TAG, "taking picture finished");
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

        if (mListeners.size() > 0 && !isPreviewRunning() && !mShowPreview) {
            mWorkHandler.post(() -> {
                try {
                    startPreview(new ICamera.LoggingPreviewListener());
                } catch (CameraException e) {
                    Log.e(TAG, "Could not enable MotionDetector", e);
                }
            });
        }
    }

    void removeLumaListener(ICamera.ILumaListener l) {
        synchronized (mListeners) {
            mListeners.remove(l);
            mImplementation.removeLumaListener(l);
        }

        if (mListeners.isEmpty() && !mShowPreview) {
            mWorkHandler.post(() -> {
                try {
                    stopPreview();
                } catch (CameraException e) {
                    Log.e(TAG, "Could not disable MotionDetector", e);
                }
            });
        }
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
                    Log.d(TAG, "stopping preview...");
                    mImplementation.stopPreview();
                    Log.d(TAG, "stopping preview finished.");
                }
                if (isCameraLocked()) {
                    Log.d(TAG, "unlocking camera...");
                    mImplementation.unlockCamera();
                    Log.d(TAG, "unlocking camera finished.");
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

            // we have zo resize the previewView before starting the preview
            mUiHandler.post(() -> {
                // make sure preview view has the correct size
                ViewGroup.LayoutParams params =
                        mPreviewView.getLayoutParams();

                if (mShowPreview) {
                    params.height = 480;
                    params.width = 640;
                } else {
                    // if we have no preview, we still need a visible
                    // TextureView in order to have a working motion detection.
                    // Resize it to 1x1pxs so it does not get in the way.
                    params.height = 1;
                    params.width = 1;
                }
                mPreviewView.setLayoutParams(params);
            });

            if (shouldRun && !isPreviewRunning()) {
                Log.d(TAG, "locking camera...");
                mImplementation.lockCamera();
                Log.d(TAG, "locking camera finished");

                registerSurfaceListener(new ICamera.LoggingPreviewListener());
            } else if (isPreviewRunning() && !shouldRun) {
                Log.d(TAG, "stopping preview ...");
                mImplementation.stopPreview();
                Log.d(TAG, "stopping preview finished.");

                if (isCameraLocked()) {
                    Log.d(TAG, "unlocking camera ...");
                    mImplementation.unlockCamera();
                    Log.d(TAG, "unlocking camera finished.");
                }
            }

        } catch (CameraException e) {
            Log.e(TAG, "failed to update preview state", e);
            UiUtil.showSnackBar(mPreviewView, e.getMessage());
        }
    }

    private void doTerminate() {
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


    private void doTakePicture(ICamera.IPictureListener h,
                                         int takeDelay,
                                         int compQuality) throws CameraException {
        boolean wasPreviewRunning = isPreviewRunning();

        final CountDownLatch latch = new CountDownLatch(1);
        final ICamera.IPreviewListener pl = new ICamera.IPreviewListener() {
            AtomicBoolean pictureTaken = new AtomicBoolean();

            @Override
            public void started() {
                if (!pictureTaken.getAndSet(true)) {

                    h.progress(mContext.getString(R.string.previewStarted));

                    if (!wasPreviewRunning) {
                        try {
                            Thread.sleep(takeDelay);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "interrupted while waiting for take picture delay");
                        }
                    }

                    Log.d(TAG, "mImplementation.takePicture...");
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
                                Log.e(TAG, "Error restarting preview", e);
                                h.error(e.getMessage());
                            }
                            latch.countDown();
                        }

                        @Override
                        public void error(String message) {
                            Log.e(TAG, message);
                            h.error(message);

                            try {
                                stopPreview();

                                if (wasPreviewRunning) {
                                    startPreview(new ICamera.LoggingPreviewListener());
                                }
                            } catch (CameraException e) {
                                Log.e(TAG, "Error restarting preview", e);
                                h.error(e.getMessage());
                            }
                            latch.countDown();
                        }

                        @Override
                        public void progress(String message) {
                            Log.d(TAG, message);
                            h.progress(message);
                        }
                    });
                    Log.d(TAG, "mImplementation.takePicture finished");
                }
            }

            @Override
            public void error(String message) {
                latch.countDown();
                h.error(message);
                Log.e(TAG, message);
            }

            @Override
            public void exception(Exception e) {
                latch.countDown();
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
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new CameraException("Failed to take picture", e);
        }
        Log.d(TAG, "mImplementation.takePicture latch counted down");
    }

    private void startPreview(ICamera.IPreviewListener previewListener) throws CameraException {
        if (!isCameraLocked()) {
            previewListener.progress(mContext.getString(R.string.lockingCamera));
            Log.d(TAG, "locking camera...");
            mImplementation.lockCamera();
            Log.d(TAG, "locking camera finished");
        }

        if (!mImplementation.isPreviewRunning()) {
            if (mSurface == null) {
                registerSurfaceListener(previewListener);
            } else {
                previewListener.progress(mContext.getString(R.string.startingPreview));
                Log.d(TAG, "starting preview...");
                mImplementation.startPreview(mSurface, previewListener);
                Log.d(TAG, "starting preview finished");
            }
        } else {
            previewListener.started();
        }
    }

    private void stopPreview() throws CameraException {
        if (mImplementation.isPreviewRunning()) {
            Log.d(TAG, "stopping preview...");
            mImplementation.stopPreview();
            Log.d(TAG, "stopping preview finished");
        }

        if (mImplementation.isCameraLocked()) {
            Log.d(TAG, "unlocking camera...");
            mImplementation.unlockCamera();
            Log.d(TAG, "unlocking camera finished");
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
            Log.d(TAG, "starting preview...");
            mImplementation.startPreview(mSurface, previewListener);
            Log.d(TAG, "starting preview finished");
            return;
        }

        previewListener.progress(mContext.getString(R.string.waitingSurface));
        mPreviewView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                if (mSurface == null) {
                    previewListener.progress(mContext.getString(R.string.surfaceObtained));
                    mSurface = surfaceTexture;
                    Log.d(TAG, "starting preview...");
                    mImplementation.startPreview(mSurface, previewListener);
                    Log.d(TAG, "starting preview finished");
                } else {
                    mPreviewView.setSurfaceTexture(mSurface);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                Log.d(TAG, "surface destroyed: " + surfaceTexture);
                try {
                    Log.d(TAG, "stopping preview...");
                    mImplementation.stopPreview();
                    Log.d(TAG, "stopping preview finished");
                } catch (CameraException e) {
                    Log.e(TAG, "Error stopping preview", e);
                    previewListener.error("Failed to stop preview: " + e.getMessage());
                }

                return mSurface == null;
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
