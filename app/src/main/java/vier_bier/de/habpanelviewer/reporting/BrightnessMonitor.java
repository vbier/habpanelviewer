package vier_bier.de.habpanelviewer.reporting;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

import vier_bier.de.habpanelviewer.openhab.ServerConnection;

/**
 * Monitors brightness sensor state and reports to openHAB.
 */
public class BrightnessMonitor extends SensorMonitor {
    public BrightnessMonitor(SensorManager sensorManager, ServerConnection serverConnection) throws SensorMissingException {
        super(sensorManager, serverConnection, "brightness", Sensor.TYPE_LIGHT);
    }

    protected synchronized void addStatusItems() {
        if (mStatus == null) {
            return;
        }

        if (mSensorEnabled) {
            String state = "reporting enabled";
            if (!mSensorItem.isEmpty()) {
                final String brightness = mServerConnection.getState(mSensorItem);
                state += "\nBrightness : " + brightness + " lx [" + mSensorItem + "=" + brightness + "]";
            }

            mStatus.set("Brightness Sensor", state);
        } else {
            mStatus.set("Brightness Sensor", "reporting disabled");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int brightness = (int) event.values[0];
        mServerConnection.updateState(mSensorItem, String.valueOf(brightness));
    }
}
