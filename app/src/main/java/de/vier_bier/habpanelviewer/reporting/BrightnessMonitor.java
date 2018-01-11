package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Monitors brightness sensor state and reports to openHAB.
 */
public class BrightnessMonitor extends SensorMonitor {
    private boolean mDoAverage;
    private int mInterval;
    private Integer mBrightness;

    public BrightnessMonitor(Context ctx, SensorManager sensorManager, ServerConnection serverConnection) throws SensorMissingException {
        super(ctx, sensorManager, serverConnection, "brightness", Sensor.TYPE_LIGHT);
    }

    protected synchronized void addStatusItems(ApplicationStatus status) {
        if (mSensorEnabled) {
            String state = mCtx.getString(R.string.enabled);
            if (mDoAverage) {
                state += "\n" + mCtx.getString(R.string.updateInterval, mInterval);
            }
            if (!mSensorItem.isEmpty()) {
                state += "\n" + mCtx.getString(R.string.brightness, mBrightness, mSensorItem, mSensorState);
            }

            status.set(mCtx.getString(R.string.pref_brightness), state);
        } else {
            status.set(mCtx.getString(R.string.pref_brightness), mCtx.getString(R.string.disabled));
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
        boolean sendUpdate = mBrightness == null || !mDoAverage;

        mBrightness = (int) event.values[0];
        if (mDoAverage) {
            mServerConnection.addStateToAverage(mSensorItem, mBrightness, mInterval);
        }

        if (sendUpdate) {
            mServerConnection.updateState(mSensorItem, String.valueOf(mBrightness));
        }
    }
}
