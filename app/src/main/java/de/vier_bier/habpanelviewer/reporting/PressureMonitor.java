package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;

/**
 * Monitors pressure sensor state and reports to openHAB.
 */
public class PressureMonitor extends AbstractAveragingDeviceMonitor {
    public PressureMonitor(Context ctx, SensorManager sensorManager, ServerConnection serverConnection) {
        super(ctx, sensorManager, serverConnection, ctx.getString(R.string.pref_pressure), "pressure", Sensor.TYPE_PRESSURE);
    }

    @Override
    String getInfoString(Integer value, String item, String state) {
        return mCtx.getString(R.string.pressure, mValue, mSensorItem, mSensorState);
    }
}