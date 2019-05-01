package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.util.Log;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Monitors accelerometer sensor state and reports to openHAB.
 */
public class AccelerometerMonitor extends AbstractDeviceMonitor {
    private static final String TAG = "HPV-AccelerometerMon";

    private float mSensitivity = 3;
    private long mLastMotionTime;
    private Boolean mMotion = Boolean.FALSE;

    public AccelerometerMonitor(Context ctx, SensorManager sensorManager, ServerConnection serverConnection) {
        super(ctx, sensorManager, serverConnection, ctx.getString(R.string.pref_accelerometer), "accelerometer", Sensor.TYPE_LINEAR_ACCELERATION);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Shake detection
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        //Log.v(TAG, "onSensorChanged: x=" + x + ", y=" + y + ",z=" + z);

        float mAccel = (float) Math.sqrt(x * x + y * y + z * z);

        //Log.v(TAG, "onSensorChanged: mAccel=" + mAccel);
        if (mAccel > mSensitivity) {
            mLastMotionTime = System.currentTimeMillis();
            if (!mMotion) {
                mMotion = true;
                mServerConnection.updateState(mSensorItem, "CLOSED");
            }
        } else {
            if (mMotion && System.currentTimeMillis() - mLastMotionTime > 60000) {
                mMotion = false;
                mServerConnection.updateState(mSensorItem, "OPEN");
            }
        }
    }

    protected synchronized void addStatusItems(ApplicationStatus status) {
        if (mSensorEnabled) {
            String state = mCtx.getString(R.string.enabled);
            if (!mSensorItem.isEmpty()) {
                state += "\n" + mCtx.getString(R.string.deviceMoving, mMotion, mSensorItem, mSensorState);
            }
            state += "\n" + mCtx.getString(R.string.pref_accelerometerSensitivity) + ": " + mSensitivity;

            status.set(mCtx.getString(R.string.pref_accelerometer), state);
        } else {
            status.set(mCtx.getString(R.string.pref_accelerometer), mCtx.getString(R.string.disabled));
        }
    }

    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        super.updateFromPreferences(prefs);

        mSensitivity = Float.valueOf(prefs.getString("pref_" + mPreferenceKey + "_sensitivity", "3.0"));
    }
}
