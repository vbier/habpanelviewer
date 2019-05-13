package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.vier_bier.habpanelviewer.Constants;
import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.IStateUpdateListener;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Abstract base class for device sensor monitors.
 */
public abstract class AbstractDeviceMonitor implements IDeviceMonitor, SensorEventListener, IStateUpdateListener {
    private static final String TAG = "HPV-AbstractDevMon";

    final Context mCtx;
    private final SensorManager mSensorManager;
    final ServerConnection mServerConnection;
    final Sensor mSensor;

    final String mPreferenceKey;
    final String mSensorName;
    boolean mSensorEnabled;

    String mSensorItem;
    String mSensorState;

    AbstractDeviceMonitor(Context ctx, SensorManager sensorManager, ServerConnection serverConnection,
                          String sensorName, String prefkey, int sensorType) {
        mCtx = ctx;
        mSensorManager = sensorManager;
        mServerConnection = serverConnection;
        mPreferenceKey = prefkey;
        mSensor = mSensorManager.getDefaultSensor(sensorType);
        mSensorName = sensorName;

        EventBus.getDefault().register(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        if (mSensor == null) {
            status.set(mSensorName, mCtx.getString(R.string.unavailable));
        }
        addStatusItems(status);
    }

    public synchronized void terminate() {
        EventBus.getDefault().unregister(this);
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void disablePreferences(Intent intent) {
        intent.putExtra(mPreferenceKey + Constants.PREF_SUFFIX_ENABLED, mSensor != null);
    }

    protected abstract void addStatusItems(ApplicationStatus status);

    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        if (mSensor != null) {
            if (mSensorEnabled != prefs.getBoolean(Constants.PREF_PREFIX + mPreferenceKey + Constants.PREF_SUFFIX_ENABLED, false)) {
                mSensorEnabled = !mSensorEnabled;

                if (mSensorEnabled) {
                    mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
                } else {
                    mSensorManager.unregisterListener(this);
                }
            }

            mSensorItem = prefs.getString(Constants.PREF_PREFIX + mPreferenceKey + Constants.PREF_SUFFIX_ITEM, "");
            mServerConnection.subscribeItems(this, mSensorItem);
        }
    }

    @Override
    public void itemUpdated(String name, String value) {
        mSensorState = value;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        Log.v(TAG, "onAccuracyChanged: " + i);
    }
}

