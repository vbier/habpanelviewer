package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.util.Log;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;

/**
 * Monitors proximity sensor state and reports to openHAB.
 */
public class ProximityMonitor extends SensorMonitor {
    private boolean mProximity;

    public ProximityMonitor(Context ctx, SensorManager sensorManager, ServerConnection serverConnection) throws SensorMissingException {
        super(ctx, sensorManager, serverConnection, "proximity", Sensor.TYPE_PROXIMITY);
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
            String state = mCtx.getString(R.string.enabled);
            if (!mSensorItem.isEmpty()) {
                state += "\n" + mCtx.getString(R.string.objectClose) + " : " + mProximity + " [" + mSensorItem + "=" + mServerConnection.getState(mSensorItem) + "]";
            }

            state += "\n" + mCtx.getString(R.string.maxRangeResolution, mSensor.getMaximumRange(), mSensor.getResolution());

            mStatus.set(mCtx.getString(R.string.pref_proximity), state);
        } else {
            mStatus.set(mCtx.getString(R.string.pref_proximity), mCtx.getString(R.string.disabled));
        }
    }
}
