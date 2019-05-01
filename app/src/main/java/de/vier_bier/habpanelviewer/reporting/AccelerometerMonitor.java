package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

import java.util.Arrays;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Monitors accelerometer sensor state and reports to openHAB.
 */
public class AccelerometerMonitor extends AbstractDeviceMonitor {
    private static final String TAG = "HPV-AccelerometerMon";

    private float mSensitivity = 1f;
    private String mSensitivityStr = "1.0";
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
            state += "\n" + mCtx.getString(R.string.pref_accelerometerSensitivity) + ": " + sensText();

            status.set(mCtx.getString(R.string.pref_accelerometer), state);
        } else {
            status.set(mCtx.getString(R.string.pref_accelerometer), mCtx.getString(R.string.disabled));
        }
    }

    private String sensText() {
        String[] sensVals = mCtx.getResources().getStringArray(R.array.sensitivityValues);
        String[] sensitivities = mCtx.getResources().getStringArray(R.array.sensitivityNames);

        int idx = Arrays.asList(sensVals).indexOf(mSensitivityStr);
        return idx == -1 ? "??" : sensitivities[idx];
    }

    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        super.updateFromPreferences(prefs);

        mSensitivityStr = prefs.getString("pref_" + mPreferenceKey + "_sensitivity", "3.0");
        mSensitivity = Float.valueOf(mSensitivityStr);
    }
}
