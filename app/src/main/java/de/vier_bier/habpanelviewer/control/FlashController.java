package de.vier_bier.habpanelviewer.control;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.openhab.StateUpdateListener;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Controller for the back-facing cameras flash light.
 */
@TargetApi(Build.VERSION_CODES.M)
public class FlashController implements StateUpdateListener {
    private Context mCtx;
    private CameraManager camManager;
    private ServerConnection mServerConnection;

    private FlashControlThread controller;
    private String torchId;

    private boolean enabled;
    private String flashItemName;

    private Pattern flashOnPattern;
    private Pattern flashPulsatingPattern;

    private ApplicationStatus mStatus;

    public FlashController(Context ctx, CameraManager cameraManager, ServerConnection serverConnection) throws CameraAccessException, IllegalAccessException {
        mCtx = ctx;
        camManager = cameraManager;
        mServerConnection = serverConnection;

        EventBus.getDefault().register(this);

        for (String camId : camManager.getCameraIdList()) {
            CameraCharacteristics characteristics = camManager.getCameraCharacteristics(camId);
            Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

            if (facing == CameraCharacteristics.LENS_FACING_BACK && hasFlash) {
                torchId = camId;
                break;
            }
        }

        if (torchId == null) {
            throw new IllegalAccessException(ctx.getString(R.string.couldNotFindBackFlash));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        mStatus = status;
        addStatusItems();
    }

    private void addStatusItems() {
        if (mStatus == null) {
            return;
        }

        if (isEnabled()) {
            String status = mCtx.getString(R.string.enabled);
            if (!flashItemName.isEmpty()) {
                status += "\n" + flashItemName + "=" + mServerConnection.getState(flashItemName);
            }
            mStatus.set(mCtx.getString(R.string.pref_flash), status);
        } else {
            mStatus.set(mCtx.getString(R.string.pref_flash), mCtx.getString(R.string.disabled));
        }
    }

    private boolean isEnabled() {
        return enabled;
    }

    public void terminate() {
        if (controller != null) {
            controller.terminate();
            controller = null;
        }
    }

    private FlashControlThread createController() {
        if (controller == null) {
            controller = new FlashControlThread();
            controller.start();
        }

        return controller;
    }

    public void updateFromPreferences(SharedPreferences prefs) {
        flashPulsatingPattern = null;
        flashOnPattern = null;
        flashItemName = prefs.getString("pref_flash_item", "");
        enabled = prefs.getBoolean("pref_flash_enabled", false);

        String pulsatingRegexpStr = prefs.getString("pref_flash_pulse_regex", "");
        if (!pulsatingRegexpStr.isEmpty()) {
            try {
                flashPulsatingPattern = Pattern.compile(pulsatingRegexpStr);
            } catch (PatternSyntaxException e) {
                // is handled in the preferences
            }
        }

        String steadyRegexpStr = prefs.getString("pref_flash_steady_regex", "");
        if (!steadyRegexpStr.isEmpty()) {
            try {
                flashOnPattern = Pattern.compile(steadyRegexpStr);
            } catch (PatternSyntaxException e) {
                // is handled in the preferences
            }
        }

        mServerConnection.subscribeItems(this, flashItemName);
    }

    @Override
    public void itemUpdated(String name, final String value) {
        Log.i("Habpanelview", "flash item state=" + value);
        addStatusItems();

        if (flashOnPattern != null && value != null && flashOnPattern.matcher(value).matches()) {
            createController().enableFlash();
        } else if (flashPulsatingPattern != null && value != null && flashPulsatingPattern.matcher(value).matches()) {
            createController().pulseFlash();
        } else {
            if (controller != null) {
                controller.disableFlash();
            }
        }
    }

    private class FlashControlThread extends Thread {
        private final AtomicBoolean fRunning = new AtomicBoolean(true);
        private AtomicBoolean fPulsing = new AtomicBoolean(false);
        private AtomicBoolean fOn = new AtomicBoolean(false);

        private boolean fFlashOn = false;

        private FlashControlThread() {
            super("FlashControlThread");
            setDaemon(true);
        }

        private void terminate() {
            fRunning.set(false);
            try {
                join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void run() {
            Log.d("Habpanelview", "FlashControlThread started");

            while (fRunning.get()) {
                synchronized (fRunning) {
                    setFlash(fOn.get() || fPulsing.get() && !fFlashOn);

                    try {
                        fRunning.wait(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            setFlash(false);
            Log.d("Habpanelview", "FlashControlThread finished");
        }

        private void pulseFlash() {
            Log.d("Habpanelview", "pulseFlash");

            synchronized (fRunning) {
                fPulsing.set(true);
                fOn.set(false);

                fRunning.notifyAll();
            }
        }

        private void disableFlash() {
            Log.d("Habpanelview", "disableFlash");

            synchronized (fRunning) {
                fPulsing.set(false);
                fOn.set(false);

                fRunning.notifyAll();
            }
        }

        private void enableFlash() {
            Log.d("Habpanelview", "disableFlash");

            synchronized (fRunning) {
                fPulsing.set(false);
                fOn.set(true);

                fRunning.notifyAll();
            }
        }

        private void setFlash(boolean flashing) {
            if (flashing != fFlashOn) {
                fFlashOn = flashing;

                try {
                    if (torchId != null) {
                        camManager.setTorchMode(torchId, flashing);
                        Log.d("Habpanelview", "Set torchmode " + flashing);
                    }
                } catch (CameraAccessException e) {
                    if (e.getReason() != CameraAccessException.MAX_CAMERAS_IN_USE) {
                        Log.e("Habpanelview", "Failed to toggle flash!", e);
                    }
                }
            }
        }
    }
}
