package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

public abstract class AbstractAveragingDeviceMonitor extends AbstractDeviceMonitor {
    private boolean mDoAverage;
    private int mInterval;
    Float mValue;

    AbstractAveragingDeviceMonitor(Context ctx, SensorManager sensorManager, ServerConnection serverConnection,
                          String sensorName, String prefkey, int sensorType) {
        super(ctx, sensorManager, serverConnection, sensorName, prefkey, sensorType);
    }

    protected synchronized void addStatusItems(ApplicationStatus status) {
        if (mSensorEnabled) {
            String state = mCtx.getString(R.string.enabled);
            if (mDoAverage) {
                Resources res = mCtx.getResources();
                state += "\n" + res.getQuantityString(R.plurals.updateInterval, mInterval, mInterval);
            }
            if (!mSensorItem.isEmpty()) {
                state += "\n" + getInfoString(mValue, mSensorItem, mSensorState);
            }

            status.set(mSensorName, state);
        } else {
            status.set(mSensorName, mCtx.getString(R.string.disabled));
        }
    }

    abstract String getInfoString(Float value, String item, String state);

    @Override
    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        boolean removeFromAverage = false;
        if (mDoAverage != prefs.getBoolean("pref_" + mPreferenceKey + "_average", true)) {
            mDoAverage = prefs.getBoolean("pref_" + mPreferenceKey + "_average", true);

            if (!mDoAverage) {
                removeFromAverage = true;
            }
        }

        if (mInterval != Integer.parseInt(prefs.getString("pref_" + mPreferenceKey + "_intervall", "60"))) {
            mInterval = Integer.parseInt(prefs.getString("pref_" + mPreferenceKey + "_intervall", "60"));
        }
        boolean wasEnabled = mSensorEnabled;
        super.updateFromPreferences(prefs);

        // if sensor or averaging turned off remove current average so it is not propagated
        if ((!mSensorEnabled && wasEnabled) || (wasEnabled && removeFromAverage)) {
            mServerConnection.removeFromAverage(mSensorItem);
        }
    }
    @Override
    public void onSensorChanged(SensorEvent event) {
        mValue = event.values[0];

        if (mDoAverage) {
            mServerConnection.addStateToAverage(mSensorItem, mValue, mInterval);
        } else {
            mServerConnection.updateState(mSensorItem, String.valueOf(mValue));
        }
    }
}
