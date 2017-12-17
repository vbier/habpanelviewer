package de.vier_bier.habpanelviewer.reporting;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.concurrent.atomic.AtomicBoolean;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.openhab.StateUpdateListener;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Monitors battery state and reports to openHAB.
 */
public class BatteryMonitor implements StateUpdateListener {
    private Context mCtx;
    private ServerConnection mServerConnection;
    private ApplicationStatus mStatus;

    private BroadcastReceiver mBatteryReceiver;
    private boolean mBatteryEnabled;

    private String mBatteryLowItem;
    private String mBatteryChargingItem;
    private String mBatteryLevelItem;

    private BatteryPollingThread mPollBatteryLevel;

    public BatteryMonitor(Context context, ServerConnection serverConnection) {
        mCtx = context;
        mServerConnection = serverConnection;

        EventBus.getDefault().register(this);

        mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_LOW.equals(intent.getAction())
                        || Intent.ACTION_BATTERY_OKAY.equals(intent.getAction())) {
                    String newState = Intent.ACTION_BATTERY_LOW.equals(intent.getAction()) ? "CLOSED" : "OPEN";
                    mServerConnection.updateState(mBatteryLowItem, newState);
                } else {
                    String newState = Intent.ACTION_POWER_CONNECTED.equals(intent.getAction()) ? "CLOSED" : "OPEN";
                    mServerConnection.updateState(mBatteryChargingItem, newState);
                }
            }
        };

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        intentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        intentFilter.addAction(Intent.ACTION_BATTERY_LOW);
        intentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
        mCtx.registerReceiver(mBatteryReceiver, intentFilter);
    }

    public synchronized void terminate() {
        mCtx.unregisterReceiver(mBatteryReceiver);

        if (mPollBatteryLevel != null) {
            mPollBatteryLevel.stopPolling();
            mPollBatteryLevel = null;
        }
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

        if (mBatteryEnabled) {
            String state = mCtx.getString(R.string.enabled);
            if (!mBatteryLowItem.isEmpty()) {
                String lowState = mServerConnection.getState(mBatteryLowItem);
                state += "\n" + mCtx.getString(R.string.battLow) + ": " + "CLOSED".equals(lowState)
                        + " [" + mBatteryLowItem + "=" + lowState + "]";
            }
            if (!mBatteryChargingItem.isEmpty()) {
                String chargingState = mServerConnection.getState(mBatteryChargingItem);
                state += "\n" + mCtx.getString(R.string.battCharging) + ": " + "CLOSED".equals(chargingState)
                        + " [" + mBatteryChargingItem + "=" + chargingState + "]";
            }
            if (!mBatteryLevelItem.isEmpty()) {
                String levelState = mServerConnection.getState(mBatteryLevelItem);
                state += "\n" + mCtx.getString(R.string.battLevel) + ": " + levelState + "% ["
                        + mBatteryLevelItem + "=" + levelState + "]";
            }
            mStatus.set(mCtx.getString(R.string.pref_battery), state);
        } else {
            mStatus.set(mCtx.getString(R.string.pref_battery), mCtx.getString(R.string.disabled));
        }
    }

    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        if (mBatteryEnabled != prefs.getBoolean("pref_battery_enabled", false)) {
            mBatteryEnabled = !mBatteryEnabled;
        }

        if (!mBatteryEnabled && mPollBatteryLevel != null) {
            mPollBatteryLevel.stopPolling();
            mPollBatteryLevel = null;
        }

        mBatteryLowItem = prefs.getString("pref_battery_item", "");
        mBatteryChargingItem = prefs.getString("pref_battery_charging_item", "");
        mBatteryLevelItem = prefs.getString("pref_battery_level_item", "");

        mServerConnection.subscribeItems(this, mBatteryLowItem, mBatteryChargingItem, mBatteryLevelItem);

        boolean canPoll = !mBatteryLevelItem.isEmpty()
                || !mBatteryChargingItem.isEmpty() || !mBatteryLowItem.isEmpty();

        if (mBatteryEnabled) {
            if (!canPoll) {
                mPollBatteryLevel.stopPolling();
                mPollBatteryLevel = null;
            } else if (mPollBatteryLevel != null) {
                mPollBatteryLevel.pollNow();
            } else {
                mPollBatteryLevel = new BatteryPollingThread();
            }
        }
    }

    @Override
    public void itemUpdated(String name, String value) {
        addStatusItems();
    }

    private class BatteryPollingThread extends Thread {
        private final AtomicBoolean fRunning = new AtomicBoolean(true);
        private final AtomicBoolean fPollAll = new AtomicBoolean(true);

        public BatteryPollingThread() {
            super("BatteryPollingThread");
            setDaemon(true);
            start();
        }

        public void stopPolling() {
            synchronized (fRunning) {
                fRunning.set(false);
                fRunning.notifyAll();
            }
        }

        public void pollNow() {
            synchronized (fRunning) {
                fPollAll.set(true);
                fRunning.notifyAll();
            }
        }

        @Override
        public void run() {
            while (fRunning.get()) {
                updateBatteryValues();

                synchronized (fRunning) {
                    try {
                        fRunning.wait("CLOSED".equals(mServerConnection.getState(mBatteryChargingItem)) ? 5000 : 300000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        private void updateBatteryValues() {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = null;

            if (!mBatteryLevelItem.isEmpty() || !mBatteryLowItem.isEmpty()) {
                batteryStatus = mCtx.registerReceiver(null, ifilter);

                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int newBatteryLevelState = (int) ((level / (float) scale) * 100);

                mServerConnection.updateState(mBatteryLevelItem, String.valueOf(newBatteryLevelState));
            }

            if (fPollAll.getAndSet(false)) {
                if (batteryStatus == null) {
                    batteryStatus = mCtx.registerReceiver(null, ifilter);
                }

                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL;
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int newBatteryLevelState = (int) ((level / (float) scale) * 100);

                mServerConnection.updateState(mBatteryChargingItem, isCharging ? "CLOSED" : "OPEN");
                mServerConnection.updateState(mBatteryLowItem, newBatteryLevelState < 16 ? "CLOSED" : "OPEN");
            }
        }
    }
}
