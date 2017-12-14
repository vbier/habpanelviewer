package de.vier_bier.habpanelviewer.reporting;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

import de.vier_bier.habpanelviewer.openhab.ServerConnection;

/**
 * Monitors temperature sensor state and reports to openHAB.
 */
public class TemperatureMonitor extends SensorMonitor {
    public TemperatureMonitor(SensorManager sensorManager, ServerConnection serverConnection) throws SensorMissingException {
        super(sensorManager, serverConnection, "temperature", Sensor.TYPE_AMBIENT_TEMPERATURE);
    }

    protected synchronized void addStatusItems() {
        if (mStatus == null) {
            return;
        }

        if (mSensorEnabled) {
            String state = "reporting enabled";
            if (!mSensorItem.isEmpty()) {
                final String value = mServerConnection.getState(mSensorItem);
                state += "\nTemperature : " + value + " °C [" + mSensorItem + "=" + value + "]";
            }

            mStatus.set("Temperature Sensor", state);
        } else {
            mStatus.set("Temperature Sensor", "reporting disabled");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float value = event.values[0];
        mServerConnection.updateState(mSensorItem, String.valueOf(value));
    }
}
