package vier_bier.de.habpanelviewer.reporting;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

import vier_bier.de.habpanelviewer.openhab.ServerConnection;

/**
 * Monitors pressure sensor state and reports to openHAB.
 */
public class PressureMonitor extends SensorMonitor {
    public PressureMonitor(SensorManager sensorManager, ServerConnection serverConnection) throws SensorMissingException {
        super(sensorManager, serverConnection, "pressure", Sensor.TYPE_PRESSURE);
    }

    protected synchronized void addStatusItems() {
        if (mStatus == null) {
            return;
        }

        if (mSensorEnabled) {
            String state = "reporting enabled";
            if (!mSensorItem.isEmpty()) {
                final String pressure = mServerConnection.getState(mSensorItem);
                state += "\nPressure : " + pressure + " hPa or mbar [" + mSensorItem + "=" + pressure + "]";
            }

            mStatus.set("Pressure Sensor", state);
        } else {
            mStatus.set("Pressure Sensor", "reporting disabled");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int pressure = (int) event.values[0];
        mServerConnection.updateState(mSensorItem, String.valueOf(pressure));
    }
}