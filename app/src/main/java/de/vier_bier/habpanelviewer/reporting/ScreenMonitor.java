package de.vier_bier.habpanelviewer.reporting;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.PowerManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.IStateUpdateListener;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

import static android.content.Context.POWER_SERVICE;

public class ScreenMonitor implements IStateUpdateListener {
    private final Context mCtx;
    private final ServerConnection mServerConnection;

    private BroadcastReceiver mScreenReceiver;
    private boolean mMonitorEnabled;

    private String mScreenOnItem;
    private String mScreenOnState;
    private boolean mScreenOn;

    public ScreenMonitor(Context context, ServerConnection serverConnection) {
        mCtx = context;
        mServerConnection = serverConnection;

        EventBus.getDefault().register(this);

        mScreenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mScreenOn = Intent.ACTION_SCREEN_ON.equals(intent.getAction());
                mServerConnection.updateState(mScreenOnItem, mScreenOn ? "CLOSED" : "OPEN");
            }
        };

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        mCtx.registerReceiver(mScreenReceiver, intentFilter);
    }

    public synchronized void terminate() {
        EventBus.getDefault().unregister(this);
        mCtx.unregisterReceiver(mScreenReceiver);
        mScreenReceiver = null;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        if (mMonitorEnabled) {
            String state = mCtx.getString(R.string.enabled);
            if (!mScreenOnItem.isEmpty()) {
                state += "\n" + mCtx.getString(R.string.screenOn, mScreenOn, mScreenOnItem, mScreenOnState);
            }
            status.set(mCtx.getString(R.string.pref_screen), state);
        } else {
            status.set(mCtx.getString(R.string.pref_screen), mCtx.getString(R.string.disabled));
        }
    }

    @Override
    public void itemUpdated(String name, String value) {
        if (name.equals(mScreenOnItem)) {
            mScreenOnState = value;
        }
    }

    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        mScreenOnItem = prefs.getString("pref_screen_item", "");

        if (mMonitorEnabled != prefs.getBoolean("pref_screen_enabled", false)) {
            mMonitorEnabled = !mMonitorEnabled;

            if (mMonitorEnabled && mScreenOnItem != null) {
                PowerManager powerManager = (PowerManager) mCtx.getSystemService(POWER_SERVICE);
                mScreenOn = powerManager.isScreenOn();
                mServerConnection.updateState(mScreenOnItem, mScreenOn ? "CLOSED" : "OPEN");
            }
        }


        mServerConnection.subscribeItems(this, mScreenOnItem);
    }
}
