package de.vier_bier.habpanelviewer.reporting;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.IStateUpdateListener;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

import static android.content.Context.POWER_SERVICE;

public class ScreenMonitor implements IDeviceMonitor, IStateUpdateListener {
    private static final String TAG = "HPV-ScreenMonitor";

    private final Context mCtx;
    private final ServerConnection mServerConnection;
    private final ScreenListener mListener;

    private BroadcastReceiver mScreenReceiver;
    private boolean mMonitorEnabled;
    private boolean mReceiverRegistered;

    private String mScreenOnItem;
    private String mScreenOnState;
    private boolean mScreenOn;
    private IntentFilter mIntentFilter;

    public ScreenMonitor(Context context, ServerConnection serverConnection, ScreenListener listener) {
        mCtx = context;
        mServerConnection = serverConnection;
        mListener = listener;

        EventBus.getDefault().register(this);

        mScreenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mScreenOn = Intent.ACTION_SCREEN_ON.equals(intent.getAction());
                if (mListener.isActive() && mScreenOn) {
                    mListener.screenOn();
                }

                if (mMonitorEnabled && mScreenOnItem != null) {
                    mServerConnection.updateState(mScreenOnItem, mScreenOn ? "CLOSED" : "OPEN");
                }
            }
        };

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(Intent.ACTION_SCREEN_ON);
        mIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
    }

    @Override
    public void disablePreferences(Intent intent) { }

    @Override
    public synchronized void terminate() {
        EventBus.getDefault().unregister(this);
        try {
            mCtx.unregisterReceiver(mScreenReceiver);
            Log.d(TAG, "unregistering screen receiver...");
        } catch (IllegalArgumentException e) {
            // not registered
        }
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

    @Override
    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        mScreenOnItem = prefs.getString("pref_screen_item", "");

        if (mReceiverRegistered != (prefs.getBoolean("pref_screen_enabled", false) || mListener.isActive())) {
            mReceiverRegistered = !mReceiverRegistered;

            mMonitorEnabled = prefs.getBoolean("pref_screen_enabled", false);

            if (mReceiverRegistered) {
                PowerManager powerManager = (PowerManager) mCtx.getSystemService(POWER_SERVICE);
                mScreenOn = powerManager.isScreenOn();

                if (mMonitorEnabled && mScreenOnItem != null) {
                    mServerConnection.updateState(mScreenOnItem, mScreenOn ? "CLOSED" : "OPEN");
                }

                Log.d(TAG, "registering screen receiver...");
                mCtx.registerReceiver(mScreenReceiver, mIntentFilter);
            } else {
                Log.d(TAG, "unregistering screen receiver...");
                mCtx.unregisterReceiver(mScreenReceiver);
            }
        } else if (mMonitorEnabled != prefs.getBoolean("pref_screen_enabled", false)) {
            mMonitorEnabled = !mMonitorEnabled;

            if (mMonitorEnabled && mScreenOnItem != null) {
                mServerConnection.updateState(mScreenOnItem, mScreenOn ? "CLOSED" : "OPEN");
            }
        }

        mServerConnection.subscribeItems(this, mScreenOnItem);
    }

    public interface ScreenListener {
        void screenOn();
        boolean isActive();
    }
}
