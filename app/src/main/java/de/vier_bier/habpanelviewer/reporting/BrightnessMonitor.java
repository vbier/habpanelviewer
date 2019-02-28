package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;

/**
 * Monitors brightness sensor state and reports to openHAB.
 */
public class BrightnessMonitor extends AbstractAveragingDeviceMonitor {
    public BrightnessMonitor(Context ctx, SensorManager sensorManager, ServerConnection serverConnection) {
        super(ctx, sensorManager, serverConnection, ctx.getString(R.string.pref_brightness), "brightness", Sensor.TYPE_LIGHT);
    }

    @Override
    String getInfoString(Float value, String item, String state) {
        return mCtx.getString(R.string.brightness, mValue, mSensorItem, mSensorState);
    }
}
