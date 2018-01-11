package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Monitors temperature sensor state and reports to openHAB.
 */
public class TemperatureMonitor extends SensorMonitor {
    private float fTemperature;

    public TemperatureMonitor(Context ctx, SensorManager sensorManager, ServerConnection serverConnection) throws SensorMissingException {
        super(ctx, sensorManager, serverConnection, "temperature", Sensor.TYPE_AMBIENT_TEMPERATURE);
    }

    protected synchronized void addStatusItems(ApplicationStatus status) {
        if (mSensorEnabled) {
            String state = mCtx.getString(R.string.enabled);
            if (!mSensorItem.isEmpty()) {
                state += "\n" + mCtx.getString(R.string.temperature, fTemperature, mSensorItem, mSensorState);
            }

            status.set(mCtx.getString(R.string.pref_temperature), state);
        } else {
            status.set(mCtx.getString(R.string.pref_temperature), mCtx.getString(R.string.disabled));
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        fTemperature = event.values[0];
        mServerConnection.updateState(mSensorItem, String.valueOf(fTemperature));
    }
}
