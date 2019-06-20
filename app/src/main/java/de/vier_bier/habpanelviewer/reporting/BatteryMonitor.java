package de.vier_bier.habpanelviewer.reporting;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
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
 * Monitors battery state and reports to openHAB.
 */
public class BatteryMonitor implements IDeviceMonitor, IStateUpdateListener {
    private static final String TAG = "HPV-BatteryMonitor";

    private final Context mCtx;
    private final ServerConnection mServerConnection;

    private final BroadcastReceiver mBatteryReceiver;
    private boolean mBatteryEnabled;

    private String mBatteryLowItem;
    private String mBatteryChargingItem;
    private String mBatteryLevelItem;
    private String mBatteryTempItem;

    private boolean mBatteryLow;
    private boolean mBatteryCharging;
    private Integer mBatteryLevel;
    private Float mBatteryTemp;
    private String mBatteryLowState;
    private String mBatteryChargingState;
    private String mBatteryLevelState;
    private String mBatteryTempState;
    private final IntentFilter mIntentFilter;

    public BatteryMonitor(Context context, ServerConnection serverConnection) {
        mCtx = context;
        mServerConnection = serverConnection;

        EventBus.getDefault().register(this);

        mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_LOW.equals(intent.getAction())
                        || Intent.ACTION_BATTERY_OKAY.equals(intent.getAction())) {
                    mBatteryLow = Intent.ACTION_BATTERY_LOW.equals(intent.getAction());
                    mServerConnection.updateState(mBatteryLowItem, mBatteryLow ? "CLOSED" : "OPEN");
                } else if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    float temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0);
                    mBatteryTemp = temp / 10;
                    mServerConnection.updateState(mBatteryTempItem, String.valueOf(mBatteryTemp));
                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    mBatteryLevel = (int) ((level / (float) scale) * 100);
                    mServerConnection.updateState(mBatteryLevelItem, String.valueOf(mBatteryLevel));
                } else {
                    mBatteryCharging = Intent.ACTION_POWER_CONNECTED.equals(intent.getAction());
                    mServerConnection.updateState(mBatteryChargingItem, mBatteryCharging ? "CLOSED" : "OPEN");
                }
            }
        };

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        mIntentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        mIntentFilter.addAction(Intent.ACTION_BATTERY_LOW);
        mIntentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
        mIntentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
    }

    @Override
    public void disablePreferences(Intent intent) { }

    @Override
    public synchronized void terminate() {
        EventBus.getDefault().unregister(this);
        try {
            mCtx.unregisterReceiver(mBatteryReceiver);
            Log.d(TAG, "unregistering battery receiver...");
        } catch (IllegalArgumentException e) {
            // not registered
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        if (mBatteryEnabled) {
            String state = mCtx.getString(R.string.enabled);
            if (!mBatteryLowItem.isEmpty()) {
                state += "\n" + mCtx.getString(R.string.battLow, mBatteryLow, mBatteryLowItem, mBatteryLowState);
            }
            if (!mBatteryChargingItem.isEmpty()) {
                state += "\n" + mCtx.getString(R.string.battCharging, mBatteryCharging, mBatteryChargingItem, mBatteryChargingState);
            }
            if (!mBatteryLevelItem.isEmpty()) {
                state += "\n" + mCtx.getString(R.string.battLevel, mBatteryLevel, mBatteryLevelItem, mBatteryLevelState);
            }
            if (!mBatteryTempItem.isEmpty()) {
                state += "\n" + mCtx.getString(R.string.battTemp, mBatteryTemp, mBatteryTempItem, mBatteryTempState);
            }
            status.set(mCtx.getString(R.string.pref_battery), state);
        } else {
            status.set(mCtx.getString(R.string.pref_battery), mCtx.getString(R.string.disabled));
        }
    }

    @Override
    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        if (mBatteryEnabled != prefs.getBoolean(Constants.PREF_BATTERY_ENABLED, false)) {
            mBatteryEnabled = !mBatteryEnabled;

            if (mBatteryEnabled) {
                Log.d(TAG, "registering battery receiver...");
                mCtx.registerReceiver(mBatteryReceiver, mIntentFilter);
            } else {
                Log.d(TAG, "unregistering battery receiver...");
                mCtx.unregisterReceiver(mBatteryReceiver);
            }
        }

        mBatteryLowItem = prefs.getString(Constants.PREF_BATTERY_ITEM, "");
        mBatteryChargingItem = prefs.getString(Constants.PREF_BATTERY_CHARGING_ITEM, "");
        mBatteryLevelItem = prefs.getString(Constants.PREF_BATTERY_LEVEL_ITEM, "");
        mBatteryTempItem = prefs.getString(Constants.PREF_BATTERY_TEMP_ITEM, "");

        mServerConnection.subscribeItems(this, mBatteryLowItem, mBatteryChargingItem, mBatteryLevelItem, mBatteryTempItem);
    }

    @Override
    public void itemUpdated(String name, String value) {
        if (name.equals(mBatteryChargingItem)) {
            mBatteryChargingState = value;
        } else if (name.equals(mBatteryLevelItem)) {
            mBatteryLevelState = value;
        } else if (name.equals(mBatteryLowItem)) {
            mBatteryLowState = value;
        } else if (name.equals(mBatteryTempItem)) {
            mBatteryTempState = value;
        }
    }
}
