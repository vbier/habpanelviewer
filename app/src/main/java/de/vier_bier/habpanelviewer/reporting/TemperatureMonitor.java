package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;

/**
 * Monitors temperature sensor state and reports to openHAB.
 */
public class TemperatureMonitor extends SensorMonitor {
    public TemperatureMonitor(Context ctx, SensorManager sensorManager, ServerConnection serverConnection) throws SensorMissingException {
        super(ctx, sensorManager, serverConnection, "temperature", Sensor.TYPE_AMBIENT_TEMPERATURE);
    }

    protected synchronized void addStatusItems() {
        if (mStatus == null) {
            return;
        }

        if (mSensorEnabled) {
            String state = mCtx.getString(R.string.enabled);
            if (!mSensorItem.isEmpty()) {
                final String value = mServerConnection.getState(mSensorItem);
                state += "\n" + mCtx.getString(R.string.temperature) + " : " + value + " °C [" + mSensorItem + "=" + value + "]";
            }

            mStatus.set(mCtx.getString(R.string.pref_temperature), state);
        } else {
            mStatus.set(mCtx.getString(R.string.pref_temperature), mCtx.getString(R.string.disabled));
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float value = event.values[0];
        mServerConnection.updateState(mSensorItem, String.valueOf(value));
    }
}
