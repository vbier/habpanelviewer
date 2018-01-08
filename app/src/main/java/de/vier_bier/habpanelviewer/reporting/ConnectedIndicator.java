package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.content.SharedPreferences;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.concurrent.atomic.AtomicBoolean;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.openhab.StateUpdateListener;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Reports the current time to openHAB as an indicator for being connected.
 */
public class ConnectedIndicator implements StateUpdateListener {
    private boolean mEnabled;
    private String mStatusItem;
    private int mInterval;

    private Context mCtx;
    private ServerConnection mServerConnection;
    private ConnectedReportingThread mReportConnection;
    private ApplicationStatus mStatus;

    private long fStatus;
    private String mStatusState;

    public ConnectedIndicator(Context ctx, ServerConnection serverConnection) {
        mCtx = ctx;
        mServerConnection = serverConnection;

        EventBus.getDefault().register(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        mStatus = status;
        addStatusItems();
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

        if (intervalChanged && !started && mReportConnection != null) {
            mReportConnection.reportNow();
        }

        mStatusItem = prefs.getString("pref_connected_item", "");
        mServerConnection.subscribeItems(this, mStatusItem);
    }

    @Override
    public void itemUpdated(String name, String value) {
        mStatusState = value;
        addStatusItems();
    }

    protected synchronized void addStatusItems() {
        if (mStatus == null) {
            return;
        }

        if (mEnabled) {
            String state = mCtx.getString(R.string.enabled);
            if (!mStatusItem.isEmpty()) {
                state += "\n" + mStatusItem + "=" + mStatusState;
            }

            mStatus.set(mCtx.getString(R.string.pref_connected), state);
        } else {
            mStatus.set(mCtx.getString(R.string.pref_connected), mCtx.getString(R.string.disabled));
        }
    }

    public synchronized void terminate() {
        if (mReportConnection != null) {
            mReportConnection.stopReporting();
            mReportConnection = null;
        }
    }

    private class ConnectedReportingThread extends Thread {
        private AtomicBoolean fRunning = new AtomicBoolean(true);

        public ConnectedReportingThread() {
            super("ConnectedReportingThread");
            setDaemon(true);
            start();
        }

        public void stopReporting() {
            synchronized (fRunning) {
                fRunning.set(false);
                fRunning.notifyAll();
            }
        }

        public void reportNow() {
            synchronized (fRunning) {
                fRunning.notifyAll();
            }
        }

        @Override
        public void run() {
            while (fRunning.get()) {
                fStatus = System.currentTimeMillis();
                mServerConnection.updateState(mStatusItem, String.valueOf(fStatus));

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
