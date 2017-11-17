package vier_bier.de.habpanelviewer.reporting;

import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import vier_bier.de.habpanelviewer.openhab.SetItemStateTask;
import vier_bier.de.habpanelviewer.openhab.StateListener;
import vier_bier.de.habpanelviewer.status.ApplicationStatus;

/**
 * Monitors proximity sensor state and reports to openHAB.
 */
public class ProximityMonitor implements SensorEventListener, StateListener {
    private ApplicationStatus mStatus;

    private SensorManager mSensorManager;
    private Sensor mSensor;

    private String mServerURL;
    private boolean mIgnoreCertErrors;

    private boolean mProximityEnabled;
    private String mProximityItem;
    private String mProximityItemState;
    private boolean mProximity;

    public ProximityMonitor(SensorManager sensorManager) {
        EventBus.getDefault().register(this);

        mSensorManager = sensorManager;
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
    }

    public synchronized void terminate() {
        mSensorManager.unregisterListener(this);
    }

    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        mServerURL = prefs.getString("pref_url", "");
        mIgnoreCertErrors = prefs.getBoolean("pref_ignore_ssl_errors", false);

        if (mProximityEnabled != prefs.getBoolean("pref_proximity_enabled", false)) {
            mProximityEnabled = !mProximityEnabled;

            if (mProximityEnabled) {
                mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                mSensorManager.unregisterListener(this);
            }
        }

        if (mProximityItem == null || !mProximityItem.equalsIgnoreCase(prefs.getString("pref_proximity_item", ""))) {
            mProximityItem = prefs.getString("pref_proximity_item", "");
            mProximity = false;
            mProximityItemState = null;
        }

        propagateState();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float distance = event.values[0];

        Log.v("ProximityMonitor", "onSensorChanged" + distance);

        if (distance < mSensor.getMaximumRange()) {
            if (!mProximity) {
                mProximity = true;
                propagateState();
            }
        } else if (mProximity) {
            mProximity = false;
            propagateState();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        Log.v("ProximityMonitor", "onAccuracyChanged" + i);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        mStatus = status;
        addStatusItems();
    }

    private synchronized void addStatusItems() {
        if (mStatus == null) {
            return;
        }

        if (mProximityEnabled) {
            String state = "reporting enabled";
            if (!mProximityItem.isEmpty()) {
                state += "\nObject close : " + mProximity + " [" + mProximityItem + "=" + mProximityItemState + "]";
            }

            state += "\nSensor max. range is " + mSensor.getMaximumRange() + ", resolution is " + mSensor.getResolution();

            mStatus.set("Proximity Sensor", state);
        } else {
            mStatus.set("Proximity Sensor", "reporting disabled");
        }
    }

    private void propagateState() {
        if (!mProximityItem.isEmpty()) {
            SetItemStateTask t = new SetItemStateTask(mServerURL, mIgnoreCertErrors);
            t.execute(new SetItemStateTask.ItemState(mProximityItem, mProximity ? "CLOSED" : "OPEN"));
        }
    }

    @Override
    public void updateState(String name, String value) {
        if (name.equals(mProximityItem)) {
            mProximityItemState = value;

            addStatusItems();
        }
    }
}
