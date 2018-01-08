package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;

/**
 * Monitors pressure sensor state and reports to openHAB.
 */
public class PressureMonitor extends SensorMonitor {
    private int mPressure;

    public PressureMonitor(Context ctx, SensorManager sensorManager, ServerConnection serverConnection) throws SensorMissingException {
        super(ctx, sensorManager, serverConnection, "pressure", Sensor.TYPE_PRESSURE);
    }

    protected synchronized void addStatusItems() {
        if (mStatus == null) {
            return;
        }

        if (mSensorEnabled) {
            String state = mCtx.getString(R.string.enabled);
            if (!mSensorItem.isEmpty()) {
                state += "\n" + mCtx.getString(R.string.pressure, mPressure, mSensorItem, mSensorState);
            }

            mStatus.set(mCtx.getString(R.string.pref_pressure), state);
        } else {
            mStatus.set(mCtx.getString(R.string.pref_pressure), mCtx.getString(R.string.disabled));
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        mPressure = (int) event.values[0];
        mServerConnection.updateState(mSensorItem, String.valueOf(mPressure));
    }
}