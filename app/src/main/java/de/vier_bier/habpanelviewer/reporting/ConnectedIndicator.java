package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.content.SharedPreferences;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.IStateUpdateListener;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Reports the current time to openHAB as an indicator for being connected.
 */
public class ConnectedIndicator implements IStateUpdateListener {
    private boolean mEnabled;
    private String mStatusItem;
    private int mInterval;

    private final Context mCtx;
    private final ServerConnection mServerConnection;
    private ConnectedReportingThread mReportConnection;

    private long mStatus;
    private String mStatusState;

    public ConnectedIndicator(Context ctx, ServerConnection serverConnection) {
        mCtx = ctx;
        mServerConnection = serverConnection;

        EventBus.getDefault().register(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        if (mEnabled) {
            String state = mCtx.getString(R.string.enabled);
            state += "\n" + mCtx.getString(R.string.updateInterval, mInterval);

            if (!mStatusItem.isEmpty()) {
                state += "\n" + SimpleDateFormat.getDateTimeInstance().format(new Date(mStatus))
                        + " [" + mStatusItem + "=" + mStatusState + "]";
            }

            status.set(mCtx.getString(R.string.pref_connected), state);
        } else {
            status.set(mCtx.getString(R.string.pref_connected), mCtx.getString(R.string.disabled));
        }
    }

    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        boolean intervalChanged = mInterval != Integer.parseInt(prefs.getString("pref_connected_interval", "60"));

        if (intervalChanged) {
            mInterval = Integer.parseInt(prefs.getString("pref_connected_interval", "60"));
        }

        boolean started = false;
        if (mEnabled != prefs.getBoolean("pref_connected_enabled", false)) {
            mEnabled = !mEnabled;

            if (!mEnabled) {
                terminate();
            }

            if (mEnabled && mReportConnection == null) {
                mReportConnection = new ConnectedReportingThread();
                started = true;
            }
        }

        mStatusItem = prefs.getString("pref_connected_item", "");

        if (intervalChanged && !started && mReportConnection != null) {
            mReportConnection.reportNow();
        }

        mServerConnection.subscribeItems(this, mStatusItem);
    }

    @Override
    public void itemUpdated(String name, String value) {
        mStatusState = value;
    }

    public synchronized void terminate() {
        EventBus.getDefault().unregister(this);

        if (mReportConnection != null) {
            mReportConnection.stopReporting();
            mReportConnection = null;
        }
    }

    private class ConnectedReportingThread extends Thread {
        private final AtomicBoolean fRunning = new AtomicBoolean(true);

        ConnectedReportingThread() {
            super("ConnectedReportingThread");
            setDaemon(true);
            start();
        }

        void stopReporting() {
            synchronized (fRunning) {
                fRunning.set(false);
                fRunning.notifyAll();
            }
        }

        void reportNow() {
            synchronized (fRunning) {
                fRunning.notifyAll();
            }
        }

        @Override
        public void run() {
            while (fRunning.get()) {
                mStatus = System.currentTimeMillis();
                mServerConnection.updateState(mStatusItem, String.valueOf(mStatus));

                synchronized (fRunning) {
                    try {
                        fRunning.wait(mInterval * 1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
}
