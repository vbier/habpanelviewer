package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Monitors pressure sensor state and reports to openHAB.
 */
public class PressureMonitor extends AbstractDeviceMonitor {
    private int mPressure;

    public PressureMonitor(Context ctx, SensorManager sensorManager, ServerConnection serverConnection) {
        super(ctx, sensorManager, serverConnection, ctx.getString(R.string.pref_pressure), "pressure", Sensor.TYPE_PRESSURE);
    }

    protected synchronized void addStatusItems(ApplicationStatus status) {
        if (mSensorEnabled) {
            String state = mCtx.getString(R.string.enabled);
            if (!mSensorItem.isEmpty()) {
                state += "\n" + mCtx.getString(R.string.pressure, mPressure, mSensorItem, mSensorState);
            }

            status.set(mCtx.getString(R.string.pref_pressure), state);
        } else {
            status.set(mCtx.getString(R.string.pref_pressure), mCtx.getString(R.string.disabled));
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        mPressure = (int) event.values[0];
        mServerConnection.updateState(mSensorItem, String.valueOf(mPressure));
    }
}