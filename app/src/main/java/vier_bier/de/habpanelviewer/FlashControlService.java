package vier_bier.de.habpanelviewer;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by volla on 07.09.17.
 */
public class FlashControlService {
    private FlashControlThread controller;
    private CameraManager camManager;

    public FlashControlService(CameraManager cameraManager) {
        camManager = cameraManager;
    }

    public void terminate() {
        if (controller != null) {
            controller.terminate();
            controller = null;
        }
    }

    public void disableFlash() {
        if (controller != null) {
            controller.disableFlash();
        }
    }

    private FlashControlThread createController() {
        if (controller == null) {
            controller = new FlashControlThread();
            controller.start();
        }

        return controller;
    }

    public void pulseFlash() {
        createController().pulseFlash();
    }

    public void enableFlash() {
        createController().enableFlash();
    }

    private class FlashControlThread extends Thread {
        private final AtomicBoolean fRunning = new AtomicBoolean(true);
        private AtomicBoolean fPulsing = new AtomicBoolean(false);
        private AtomicBoolean fOn = new AtomicBoolean(false);

        private boolean fFlashOn = false;

        public FlashControlThread() {
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

        public void pulseFlash() {
            Log.d("Habpanelview", "pulseFlash");

            synchronized (fRunning) {
                fPulsing.set(true);
                fOn.set(false);

                fRunning.notifyAll();
            }
        }

        public void disableFlash() {
            Log.d("Habpanelview", "disableFlash");

            synchronized (fRunning) {
                fPulsing.set(false);
                fOn.set(false);

                fRunning.notifyAll();
            }
        }

        public void enableFlash() {
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
                    for (String camId : camManager.getCameraIdList()) {
                        CameraCharacteristics characteristics = camManager.getCameraCharacteristics(camId);
                        Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

                        if (facing == CameraCharacteristics.LENS_FACING_BACK && hasFlash) {
                            camManager.setTorchMode(camId, flashing);
                            Log.d("Habpanelview", "Set torchmode " + flashing);

                            break;
                        }
                    }

                } catch (CameraAccessException e) {
                    Log.e("Habpanelview", "Failed to toggle flash!", e);
                }
            }
        }
    }
}
