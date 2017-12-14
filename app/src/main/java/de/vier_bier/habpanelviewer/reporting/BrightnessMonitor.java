package de.vier_bier.habpanelviewer.reporting;

import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

import de.vier_bier.habpanelviewer.openhab.ServerConnection;

/**
 * Monitors brightness sensor state and reports to openHAB.
 */
public class BrightnessMonitor extends SensorMonitor {
    private boolean mDoAverage;
    private int mInterval;

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
    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        if (mDoAverage != prefs.getBoolean("pref_brightness_average", true)) {
            mDoAverage = prefs.getBoolean("pref_brightness_average", true);
        }

        if (mInterval != Integer.parseInt(prefs.getString("pref_brightness_intervall", "60"))) {
            mInterval = Integer.parseInt(prefs.getString("pref_brightness_intervall", "60"));
        }

        super.updateFromPreferences(prefs);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int brightness = (int) event.values[0];
        if (mDoAverage) {
            mServerConnection.addStateToAverage(mSensorItem, brightness, mInterval);
        } else {
            mServerConnection.updateState(mSensorItem, String.valueOf(brightness));
        }
    }
}
