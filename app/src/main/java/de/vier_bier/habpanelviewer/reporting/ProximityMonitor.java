package de.vier_bier.habpanelviewer.reporting;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.util.Log;

import de.vier_bier.habpanelviewer.openhab.ServerConnection;

/**
 * Monitors proximity sensor state and reports to openHAB.
 */
public class ProximityMonitor extends SensorMonitor {
    private boolean mProximity;

    public ProximityMonitor(SensorManager sensorManager, ServerConnection serverConnection) throws SensorMissingException {
        super(sensorManager, serverConnection, "proximity", Sensor.TYPE_PROXIMITY);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float distance = event.values[0];

        Log.v("ProximityMonitor", "onSensorChanged" + distance);

        if (distance < mSensor.getMaximumRange()) {
            if (!mProximity) {
                mProximity = true;
                mServerConnection.updateState(mSensorItem, "CLOSED");
            }
        } else if (mProximity) {
            mProximity = false;
            mServerConnection.updateState(mSensorItem, "OPEN");
        }
    }

    protected synchronized void addStatusItems() {
        if (mStatus == null) {
            return;
        }

        if (mSensorEnabled) {
            String state = "reporting enabled";
            if (!mSensorItem.isEmpty()) {
                state += "\nObject close : " + mProximity + " [" + mSensorItem + "=" + mServerConnection.getState(mSensorItem) + "]";
            }

            state += "\nSensor max. range is " + mSensor.getMaximumRange() + ", resolution is " + mSensor.getResolution();

            mStatus.set("Proximity Sensor", state);
        } else {
            mStatus.set("Proximity Sensor", "reporting disabled");
        }
    }
}
