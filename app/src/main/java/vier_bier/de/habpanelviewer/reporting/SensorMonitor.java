package vier_bier.de.habpanelviewer.reporting;

import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import vier_bier.de.habpanelviewer.openhab.ServerConnection;
import vier_bier.de.habpanelviewer.openhab.SubscriptionListener;
import vier_bier.de.habpanelviewer.status.ApplicationStatus;

/**
 * Abstract base class for device sensor monitors.
 */
public abstract class SensorMonitor implements SensorEventListener, SubscriptionListener {
    private SensorManager mSensorManager;
    ServerConnection mServerConnection;
    Sensor mSensor;
    protected ApplicationStatus mStatus;

    private String mPreferenceKey;
    boolean mSensorEnabled;
    String mSensorItem;

    SensorMonitor(SensorManager sensorManager, ServerConnection serverConnection, String prefkey, int sensorType) throws SensorMissingException {
        mSensorManager = sensorManager;
        mServerConnection = serverConnection;
        mPreferenceKey = prefkey;
        mSensor = mSensorManager.getDefaultSensor(sensorType);

        if (mSensor == null) {
            throw new SensorMissingException(sensorType);
        }

        EventBus.getDefault().register(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        mStatus = status;
        addStatusItems();
    }

    public synchronized void terminate() {
        mSensorManager.unregisterListener(this);
    }

    protected abstract void addStatusItems();

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
        addStatusItems();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        Log.v("SensorMonitor", "onAccuracyChanged" + i);
    }
}

