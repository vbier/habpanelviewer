package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.util.Log;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Monitors proximity sensor state and reports to openHAB.
 */
public class ProximityMonitor extends AbstractDeviceMonitor {
    private static final String TAG = "HPV-ProximityMonitor";

    private Boolean mProximity;

    public ProximityMonitor(Context ctx, SensorManager sensorManager, ServerConnection serverConnection) {
        super(ctx, sensorManager, serverConnection, ctx.getString(R.string.pref_proximity), "proximity", Sensor.TYPE_PROXIMITY);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float distance = event.values[0];

        Log.v(TAG, "onSensorChanged" + distance);

        if (distance < mSensor.getMaximumRange()) {
            if (mProximity == null || !mProximity) {
                mProximity = true;
                mServerConnection.updateState(mSensorItem, "CLOSED");
            }
        } else if (mProximity == null || mProximity) {
            mProximity = false;
            mServerConnection.updateState(mSensorItem, "OPEN");
        }
    }

    protected synchronized void addStatusItems(ApplicationStatus status) {
        if (mSensorEnabled) {
            String state = mCtx.getString(R.string.enabled);
            if (!mSensorItem.isEmpty()) {
                state += "\n" + mCtx.getString(R.string.objectClose, mProximity, mSensorItem, mSensorState);
            }

            state += "\n" + mCtx.getString(R.string.maxRangeResolution, mSensor.getMaximumRange(), mSensor.getResolution());

            status.set(mCtx.getString(R.string.pref_proximity), state);
        } else {
            status.set(mCtx.getString(R.string.pref_proximity), mCtx.getString(R.string.disabled));
        }
    }
}
