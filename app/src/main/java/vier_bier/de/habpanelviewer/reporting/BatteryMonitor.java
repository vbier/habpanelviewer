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

    public BatteryMonitor(Context context) {
        mCtx = context;
        EventBus.getDefault().register(this);

        mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mBatteryLowState = Intent.ACTION_BATTERY_LOW.equals(intent.getAction()) ? "CLOSED" : "OPEN";
                addStatusItems();

                if (mBatteryEnabled) {
                    if (!"".equals(mServerURL)) {
                        SetItemStateTask t = new SetItemStateTask(mServerURL, mIgnoreCertErrors);
                        t.execute(new SetItemStateTask.ItemState(mBatteryLowItem, mBatteryLowState));
                    }
                }
            }
        };

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_BATTERY_LOW);
        intentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
        mCtx.registerReceiver(mBatteryReceiver, intentFilter);
    }

    public void terminate() {
        mCtx.unregisterReceiver(mBatteryReceiver);
    }

    @Override
    public void updateState(String name, String value) {
        if (name.equals(mBatteryLowItem)) {
            mBatteryLowState = value;
            addStatusItems();
        }
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
            mStatus.set("Battery Reporting", "enabled\n" + mBatteryLowItem + "=" + mBatteryLowState);
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

        mServerURL = prefs.getString("pref_url", "");
        mIgnoreCertErrors = prefs.getBoolean("pref_ignore_ssl_errors", false);

        addStatusItems();
    }
}
