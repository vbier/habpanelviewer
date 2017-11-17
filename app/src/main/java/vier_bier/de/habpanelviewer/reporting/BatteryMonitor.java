package vier_bier.de.habpanelviewer.reporting;

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

import vier_bier.de.habpanelviewer.openhab.SetItemStateTask;
import vier_bier.de.habpanelviewer.openhab.StateListener;
import vier_bier.de.habpanelviewer.status.ApplicationStatus;

/**
 * Monitors battery state
 */
public class BatteryMonitor implements StateListener {
    private Context mCtx;
    private ApplicationStatus mStatus;

    private String mServerURL;
    private boolean mIgnoreCertErrors;

    private BroadcastReceiver mBatteryReceiver;
    private boolean mBatteryEnabled;

    private String mBatteryLowItem;
    private String mBatteryLowState;
    private String mBatteryChargingItem;
    private String mBatteryChargingState;
    private String mBatteryLevelItem;
    private int mBatteryLevelState;

    private BatteryPollingThread mPollBatteryLevel;

    public BatteryMonitor(Context context) {
        mCtx = context;
        EventBus.getDefault().register(this);

        mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_LOW.equals(intent.getAction())
                        || Intent.ACTION_BATTERY_OKAY.equals(intent.getAction())) {
                    mBatteryLowState = Intent.ACTION_BATTERY_LOW.equals(intent.getAction()) ? "CLOSED" : "OPEN";

                    if (mBatteryEnabled) {
                        updateState(mBatteryLowItem, mBatteryLowState);
                    }
                } else {
                    mBatteryChargingState = Intent.ACTION_POWER_CONNECTED.equals(intent.getAction()) ? "CLOSED" : "OPEN";

                    if (mBatteryEnabled) {
                        updateState(mBatteryChargingItem, mBatteryChargingState);
                    }
                }

                addStatusItems();
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

    @Override
    public void updateState(String name, String value) {
        if (name.equals(mBatteryLowItem)
                && (mBatteryLowState == null || !mBatteryLowState.equals(value))) {
            mBatteryLowState = value;
        } else if (name.equals(mBatteryChargingItem)
                && (mBatteryChargingState == null || !mBatteryChargingState.equals(value))) {
            mBatteryChargingState = value;
        } else {
            return;
        }

        addStatusItems();
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
            String state = "enabled";
            if (!"".equals(mBatteryLowItem)) {
                state += "\nBattery low: " + "CLOSED".equals(mBatteryLowState) + " [" + mBatteryLowItem + "=" + mBatteryLowState + "]";
            }
            if (!"".equals(mBatteryChargingItem)) {
                state += "\nBattery charging: " + "CLOSED".equals(mBatteryChargingItem) + " [" + mBatteryChargingItem + "=" + mBatteryChargingState + "]";
            }
            if (!"".equals(mBatteryLevelItem)) {
                state += "\nBattery level: " + mBatteryLevelState + "% [" + mBatteryLevelItem + "=" + mBatteryLevelState + "]";
            }
            mStatus.set("Battery Reporting", state);
        } else {
            mStatus.set("Battery Reporting", "disabled");
        }
    }

    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        mServerURL = prefs.getString("pref_url", "");
        mIgnoreCertErrors = prefs.getBoolean("pref_ignore_ssl_errors", false);

        if (mBatteryEnabled != prefs.getBoolean("pref_battery_enabled", false)) {
            mBatteryEnabled = !mBatteryEnabled;
        }

        if ((!mBatteryEnabled || mServerURL.isEmpty()) && mPollBatteryLevel != null) {
            mPollBatteryLevel.stopPolling();
            mPollBatteryLevel = null;
        }

        if (mBatteryLowItem == null || !mBatteryLowItem.equalsIgnoreCase(prefs.getString("pref_battery_item", ""))) {
            mBatteryLowItem = prefs.getString("pref_battery_item", "");
            mBatteryLowState = null;
        }

        if (mBatteryChargingItem == null || !mBatteryChargingItem.equalsIgnoreCase(prefs.getString("pref_battery_charging_item", ""))) {
            mBatteryChargingItem = prefs.getString("pref_battery_charging_item", "");
            mBatteryChargingState = null;
        }

        if (mBatteryLevelItem == null || !mBatteryLevelItem.equalsIgnoreCase(prefs.getString("pref_battery_level_item", ""))) {
            mBatteryLevelItem = prefs.getString("pref_battery_level_item", "");
            mBatteryLevelState = -1;
        }

        boolean canPoll = !mServerURL.isEmpty() && (!mBatteryLevelItem.isEmpty()
                || !mBatteryChargingItem.isEmpty() || !mBatteryLowItem.isEmpty());

        if (mBatteryEnabled) {
            if (!canPoll) {
                mPollBatteryLevel.stopPolling();
                mPollBatteryLevel = null;
            } else if (mPollBatteryLevel != null) {
                mPollBatteryLevel.pollNow();
            } else {
                mPollBatteryLevel = new BatteryPollingThread();
                mPollBatteryLevel.start();
            }
        }
    }

    private class BatteryPollingThread extends Thread {
        private final AtomicBoolean fRunning = new AtomicBoolean(true);
        private final AtomicBoolean fPollAll = new AtomicBoolean(true);

        public void stopPolling() {
            synchronized (fRunning) {
                fRunning.getAndSet(true);
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
                        fRunning.wait("CLOSED".equals(mBatteryChargingState) ? 5000 : 300000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        private void updateState(String item, String state) {
            if (!item.isEmpty() && state != null) {
                SetItemStateTask t = new SetItemStateTask(mServerURL, mIgnoreCertErrors);
                t.execute(new SetItemStateTask.ItemState(item, state));
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

                if (mBatteryLevelState != newBatteryLevelState) {
                    mBatteryLevelState = newBatteryLevelState;

                    updateState(mBatteryLevelItem, String.valueOf(mBatteryLevelState));
                }
            }

            if (fPollAll.getAndSet(false)) {
                if (batteryStatus == null) {
                    batteryStatus = mCtx.registerReceiver(null, ifilter);
                }

                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL;

                if (!mBatteryChargingItem.isEmpty()) {
                    mBatteryChargingState = isCharging ? "CLOSED" : "OPEN";
                    updateState(mBatteryChargingItem, mBatteryChargingState);
                }

                if (!mBatteryLowItem.isEmpty()) {
                    mBatteryLowState = mBatteryLevelState < 16 ? "CLOSED" : "OPEN";
                    updateState(mBatteryLowItem, mBatteryLowState);
                }
            }

            addStatusItems();
        }
    }
}
