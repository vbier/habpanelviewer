package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.IStateUpdateListener;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Abstract base class for device sensor monitors.
 */
public abstract class SensorMonitor implements SensorEventListener, IStateUpdateListener {
    final Context mCtx;
    private final SensorManager mSensorManager;
    final ServerConnection mServerConnection;
    final Sensor mSensor;

    private final String mPreferenceKey;
    boolean mSensorEnabled;

    String mSensorItem;
    String mSensorState;

    SensorMonitor(Context ctx, SensorManager sensorManager, ServerConnection serverConnection, String prefkey, int sensorType) throws SensorMissingException {
        mCtx = ctx;
        mSensorManager = sensorManager;
        mServerConnection = serverConnection;
        mPreferenceKey = prefkey;
        mSensor = mSensorManager.getDefaultSensor(sensorType);

        if (mSensor == null) {
            throw new SensorMissingException(mCtx.getString(R.string.deviceMissingSensor) + sensorType);
        }

        EventBus.getDefault().register(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        addStatusItems(status);
    }

    public synchronized void terminate() {
        EventBus.getDefault().unregister(this);
        mSensorManager.unregisterListener(this);
    }

    protected abstract void addStatusItems(ApplicationStatus status);

    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        if (mSensorEnabled != prefs.getBoolean("pref_" + mPreferenceKey + "_enabled", false)) {
            mSensorEnabled = !mSensorEnabled;

            if (mSensorEnabled) {
                mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                mSensorManager.unregisterListener(this);
            }
        }

        mSensorItem = prefs.getString("pref_" + mPreferenceKey + "_item", "");
        mServerConnection.subscribeItems(this, mSensorItem);
    }

    @Override
    public void itemUpdated(String name, String value) {
        mSensorState = value;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        Log.v("SensorMonitor", "onAccuracyChanged" + i);
    }
}

