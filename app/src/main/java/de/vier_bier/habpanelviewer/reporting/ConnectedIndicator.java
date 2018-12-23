package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.util.Log;

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
    private static final String TAG = "HPV-ConnectedIndicator";

    private boolean mStartEnabled;
    private boolean mEnabled;

    private String mStartStatusItem;
    private String mStatusItem;
    private int mInterval;

    private final Context mCtx;
    private final ServerConnection mServerConnection;
    private ConnectedReportingThread mReportConnection;

    private long mStatus;
    private long mStartStatus = -1;
    private String mStatusState;
    private String mStartStatusState;

    public ConnectedIndicator(Context ctx, ServerConnection serverConnection) {
        mCtx = ctx;
        mServerConnection = serverConnection;

        EventBus.getDefault().register(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        if (mEnabled) {
            String state = mCtx.getString(R.string.enabled);

            Resources res = mCtx.getResources();
            state += "\n" + res.getQuantityString(R.plurals.updateInterval, mInterval, mInterval);

            if (!mStatusItem.isEmpty()) {
                state += "\n" + SimpleDateFormat.getDateTimeInstance().format(new Date(mStatus))
                        + " [" + mStatusItem + "=" + mStatusState + "]";
            }

            status.set(mCtx.getString(R.string.connectedIndicator), state);
        } else {
            status.set(mCtx.getString(R.string.connectedIndicator), mCtx.getString(R.string.disabled));
        }

        if (mStartEnabled) {
            String state = mCtx.getString(R.string.enabled);

            if (!mStartStatusItem.isEmpty()) {
                state += "\n" + SimpleDateFormat.getDateTimeInstance().format(new Date(mStartStatus))
                        + " [" + mStartStatusItem + "=" + mStartStatusState + "]";
            }

            status.set(mCtx.getString(R.string.startupIndicator), state);
        } else {
            status.set(mCtx.getString(R.string.startupIndicator), mCtx.getString(R.string.disabled));
        }
    }

    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        int interval;
        try {
            interval = Integer.parseInt(prefs.getString("pref_connected_interval", "60"));
        } catch (NumberFormatException e) {
            Log.e(TAG, "preferences contain invalid connected interval: "
                    + prefs.getString("pref_connected_interval", "60"));
            interval = 60;
        }
        boolean intervalChanged = mInterval != interval;

        if (intervalChanged) {
            mInterval = interval;
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

        mStartEnabled = prefs.getBoolean("pref_startup_enabled", false);
        mStartStatusItem = prefs.getString("pref_startup_item", "");
        mStatusItem = prefs.getString("pref_connected_item", "");

        if (mStartEnabled && mStartStatus == -1) {
            mStartStatus = System.currentTimeMillis();
            mServerConnection.updateState(mStartStatusItem, String.valueOf(mStartStatus));
        }

        if (intervalChanged && !started && mReportConnection != null) {
            mReportConnection.reportNow();
        }

        mServerConnection.subscribeItems(this, mStatusItem, mStartStatusItem);
    }

    @Override
    public void itemUpdated(String name, String value) {
        if (name.equals(mStatusItem)) {
            mStatusState = value;
        } else if (name.equals(mStartStatusItem)) {
            mStartStatusState = value;
        }
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
