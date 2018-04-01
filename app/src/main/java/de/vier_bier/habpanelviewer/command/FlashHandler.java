package de.vier_bier.habpanelviewer.command;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.vier_bier.habpanelviewer.R;

/**
 * Handler for FLASH_ON, FLASH_OFF, FLASH_BLINK commands.
 */
@TargetApi(Build.VERSION_CODES.M)
public class FlashHandler implements ICommandHandler {
    private final Pattern BLINK_PATTERN = Pattern.compile("FLASH_BLINK ([0-9]+)");

    private final CameraManager mCameraManager;
    private FlashControlThread controller;
    private String torchId;

    public FlashHandler(Context ctx, CameraManager cameraManager) throws CameraAccessException, IllegalAccessException {
        mCameraManager = cameraManager;

        for (String camId : mCameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(camId);
            Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK
                    && Boolean.TRUE.equals(hasFlash)) {
                torchId = camId;
                break;
            }
        }

        if (torchId == null) {
            throw new IllegalAccessException(ctx.getString(R.string.couldNotFindBackFlash));
        }
    }

    @Override
    public boolean handleCommand(Command cmd) {
        final String cmdStr = cmd.getCommand();

        Matcher m = BLINK_PATTERN.matcher(cmdStr);

        if ("FLASH_ON".equals(cmdStr)) {
            cmd.start();
            createController().enableFlash();
        } else if ("FLASH_OFF".equals(cmdStr)) {
            cmd.start();
            if (controller != null) {
                controller.disableFlash();
            }
        } else if ("FLASH_BLINK".equals(cmdStr)) {
            cmd.start();
            createController().pulseFlash(1000);
        } else if (m.matches()) {
            String value = m.group(1);

            try {
                int length = Integer.parseInt(value);
                cmd.start();
                createController().pulseFlash(length);
            } catch (NumberFormatException e) {
                cmd.failed("failed to parse length from command");
            }
        } else {
            return false;
        }

        cmd.finished();
        return true;
    }

    private FlashControlThread createController() {
        if (controller == null) {
            controller = new FlashControlThread();
            controller.start();
        }

        return controller;
    }

    public void terminate() {
        if (controller != null) {
            controller.terminate();
        }
    }

    private class FlashControlThread extends Thread {
        private final AtomicBoolean fRunning = new AtomicBoolean(true);
        private final AtomicInteger fPulseLength = new AtomicInteger(0);
        private final AtomicBoolean fOn = new AtomicBoolean(false);

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
                    setFlash(fOn.get() || fPulseLength.get() > 0 && !fFlashOn);

                    try {
                        fRunning.wait(fPulseLength.get() == 0 ? 1000 : fPulseLength.get());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            setFlash(false);
            Log.d("Habpanelview", "FlashControlThread finished");
        }

        private void pulseFlash(int pulseLength) {
            Log.d("Habpanelview", "pulseFlash: pulseLength=" + pulseLength);

            synchronized (fRunning) {
                fPulseLength.set(pulseLength);
                fOn.set(false);

                fRunning.notifyAll();
            }
        }

        private void disableFlash() {
            Log.d("Habpanelview", "disableFlash");

            synchronized (fRunning) {
                fPulseLength.set(0);
                fOn.set(false);

                fRunning.notifyAll();
            }
        }

        private void enableFlash() {
            Log.d("Habpanelview", "disableFlash");

            synchronized (fRunning) {
                fPulseLength.set(0);
                fOn.set(true);

                fRunning.notifyAll();
            }
        }

        private void setFlash(boolean flashing) {
            if (flashing != fFlashOn) {
                fFlashOn = flashing;

                try {
                    if (torchId != null) {
                        mCameraManager.setTorchMode(torchId, flashing);
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
