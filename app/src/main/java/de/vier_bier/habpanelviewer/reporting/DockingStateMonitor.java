package de.vier_bier.habpanelviewer.reporting;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.IStateUpdateListener;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Monitors docking state and reports to openHAB.
 */
public class DockingStateMonitor implements IDeviceMonitor, IStateUpdateListener {
    private static final String TAG = "HPV-DockingStateMonitor";

    private final Context mCtx;
    private final ServerConnection mServerConnection;

    private final BroadcastReceiver mDockStateReceiver;
    private boolean mDockStateEnabled;

    private String mDockStateItem;

    private boolean mDocked;
    private String mDockedState;

    private final IntentFilter mIntentFilter;

    public DockingStateMonitor(Context context, ServerConnection serverConnection) {
        mCtx = context;
        mServerConnection = serverConnection;

        EventBus.getDefault().register(this);

        mDockStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent dockStatus) {
                Log.d(TAG, "onReceive: dockStatus=" + dockStatus);

                int dockState = dockStatus.getIntExtra(Intent.EXTRA_DOCK_STATE, -1);
                Log.d(TAG, "onReceive: dockState=" + dockStatus);

                mDocked = dockState != Intent.EXTRA_DOCK_STATE_UNDOCKED;

                mServerConnection.updateState(mDockStateItem, mDocked ? "CLOSED" : "OPEN");
            }
        };

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(Intent.ACTION_DOCK_EVENT);
    }

    @Override
    public void disablePreferences(Intent intent) { }

    @Override
    public synchronized void terminate() {
        EventBus.getDefault().unregister(this);
        try {
            mCtx.unregisterReceiver(mDockStateReceiver);
            Log.d(TAG, "unregistering docking state receiver...");
        } catch (IllegalArgumentException e) {
            // not registered
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        if (mDockStateEnabled) {
            String state = mCtx.getString(R.string.enabled);
            if (!mDockStateItem.isEmpty()) {
                state += "\n" + mCtx.getString(R.string.docked, mDocked, mDockStateItem, mDockedState);
            }
            status.set(mCtx.getString(R.string.pref_dockingState), state);
        } else {
            status.set(mCtx.getString(R.string.pref_dockingState), mCtx.getString(R.string.disabled));
        }
    }

    @Override
    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        mDockStateItem = prefs.getString("pref_docking_state_item", "");

        if (mDockStateEnabled != prefs.getBoolean("pref_docking_state_enabled", false)) {
            mDockStateEnabled = !mDockStateEnabled;

            if (mDockStateEnabled) {
                Log.d(TAG, "registering docking state receiver...");
                mCtx.registerReceiver(mDockStateReceiver, mIntentFilter);

                Intent dockStatus = mCtx.registerReceiver(null, mIntentFilter);
                Log.d(TAG, "updateFromPreferences: dockStatus=" + dockStatus);

                int dockState = (dockStatus == null ? Intent.EXTRA_DOCK_STATE_UNDOCKED :
                        dockStatus.getIntExtra(Intent.EXTRA_DOCK_STATE, -1));
                Log.d(TAG, "updateFromPreferences: dockState=" + dockStatus);

                mDocked = dockState != Intent.EXTRA_DOCK_STATE_UNDOCKED;
                mServerConnection.updateState(mDockStateItem, mDocked ? "CLOSED" : "OPEN");
            } else {
                Log.d(TAG, "unregistering docking state receiver...");
                mCtx.unregisterReceiver(mDockStateReceiver);
            }
        }

        mServerConnection.subscribeItems(this, mDockStateItem);
    }

    @Override
    public void itemUpdated(String name, String value) {
        if (name.equals(mDockStateItem)) {
            mDockedState = value;
        }
    }
}
