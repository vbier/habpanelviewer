package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;

/**
 * Monitors temperature sensor state and reports to openHAB.
 */
public class TemperatureMonitor extends AbstractAveragingDeviceMonitor {
    public TemperatureMonitor(Context ctx, SensorManager sensorManager, ServerConnection serverConnection) {
        super(ctx, sensorManager, serverConnection, ctx.getString(R.string.pref_temperature), "temperature", Sensor.TYPE_AMBIENT_TEMPERATURE);
    }


    @Override
    String getInfoString(Integer value, String item, String state) {
        return mCtx.getString(R.string.temperature, mValue, mSensorItem, mSensorState);
    }
}
