package de.vier_bier.habpanelviewer.command;

import android.Manifest;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;

/**
 * Handles BLUETOOTH_ON and BLUETOOTH_OFF commands.
 */
public class BluetoothHandler implements ICommandHandler {
    private final Context mContext;
    private final BluetoothManager mManager;

    public BluetoothHandler(Context ctx, BluetoothManager manager) {
        mContext = ctx;
        mManager = manager;
    }

    @Override
    public boolean handleCommand(Command cmd) {
        final String cmdStr = cmd.getCommand();

        if ("BLUETOOTH_ON".equals(cmdStr)) {
            if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                cmd.failed("bluetooth permission missing");
            }

            cmd.start();
            mManager.getAdapter().enable();
        } else if ("BLUETOOTH_OFF".equals(cmdStr)) {
            if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                cmd.failed("bluetooth permission missing");
            }

            cmd.start();
            mManager.getAdapter().disable();
        } else {
            return false;
        }

        cmd.finished();
        return true;
    }
}
