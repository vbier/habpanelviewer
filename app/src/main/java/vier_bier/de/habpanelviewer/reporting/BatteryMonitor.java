package vier_bier.de.habpanelviewer.reporting;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

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

    public BatteryMonitor(Context context) {
        mCtx = context;
        EventBus.getDefault().register(this);

        mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_LOW.equals(intent.getAction())
                        || Intent.ACTION_BATTERY_OKAY.equals(intent.getAction())) {
                    //TODO.vb. track charging state
                    mBatteryLowState = Intent.ACTION_BATTERY_LOW.equals(intent.getAction()) ? "CLOSED" : "OPEN";
                    addStatusItems();

                    if (mBatteryEnabled) {
                        if (!"".equals(mServerURL) && !"".equals(mBatteryLowItem)) {
                            SetItemStateTask t = new SetItemStateTask(mServerURL, mIgnoreCertErrors);
                            t.execute(new SetItemStateTask.ItemState(mBatteryLowItem, mBatteryLowState));
                        }
                    }
                } else {
                    mBatteryChargingState = Intent.ACTION_POWER_CONNECTED.equals(intent.getAction()) ? "CLOSED" : "OPEN";
                    addStatusItems();

                    if (mBatteryEnabled) {
                        if (!"".equals(mServerURL) && !"".equals(mBatteryChargingItem)) {
                            SetItemStateTask t = new SetItemStateTask(mServerURL, mIgnoreCertErrors);
                            t.execute(new SetItemStateTask.ItemState(mBatteryChargingItem, mBatteryChargingState));
                        }
                    }
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

    public void terminate() {
        mCtx.unregisterReceiver(mBatteryReceiver);
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

    private void addStatusItems() {
        if (mStatus == null) {
            return;
        }

        if (mBatteryEnabled) {
            String state = "enabled";
            if (!"".equals(mBatteryLowItem)) {
                state += "\n" + mBatteryLowItem + "=" + mBatteryLowState;
            }
            if (!"".equals(mBatteryChargingItem)) {
                state += "\n" + mBatteryChargingItem + "=" + mBatteryChargingState;
            }
            mStatus.set("Battery Reporting", state);
        } else {
            mStatus.set("Battery Reporting", "disabled");
        }
    }

    public void updateFromPreferences(SharedPreferences prefs) {
        mBatteryEnabled = prefs.getBoolean("pref_battery_enabled", false);

        if (mBatteryLowItem == null || !mBatteryLowItem.equalsIgnoreCase(prefs.getString("pref_battery_item", ""))) {
            mBatteryLowItem = prefs.getString("pref_battery_item", "");
            mBatteryLowState = null;
        }

        if (mBatteryChargingItem == null || !mBatteryChargingItem.equalsIgnoreCase(prefs.getString("pref_battery_charging_item", ""))) {
            mBatteryChargingItem = prefs.getString("pref_battery_charging_item", "");
            mBatteryChargingState = null;
        }

        mServerURL = prefs.getString("pref_url", "");
        mIgnoreCertErrors = prefs.getBoolean("pref_ignore_ssl_errors", false);

        addStatusItems();
    }
}
