package de.vier_bier.habpanelviewer;

import android.content.Context;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Holds information about online/offline times.
 */
public class ConnectionStatistics {
    private final Context mCtx;
    private long mStartTime = System.currentTimeMillis();
    private State mState = State.DISCONNECTED;

    private long mLastOnlineTime = -1;
    private long mLastOfflineTime = mStartTime;
    private long mOfflinePeriods = 0;
    private long mOfflineMillis = 0;
    private long mOfflineMaxMillis = 0;
    private long mOfflineAverage = 0;
    private long mOnlinePeriods = 0;
    private long mOnlineMillis = 0;
    private long mOnlineMaxMillis = 0;
    private long mOnlineAverage = 0;

    ConnectionStatistics(Context context) {
        mCtx = context;
        EventBus.getDefault().register(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        long now = System.currentTimeMillis();

        long currentOnlineTime = mState == State.CONNECTED ? now - mLastOnlineTime : 0;
        long currentOfflineTime = mState == State.DISCONNECTED ? now - mLastOfflineTime : 0;
        long averageOnlineTime = mState == State.CONNECTED ? (mOnlineAverage * mOnlinePeriods + currentOnlineTime) / (mOnlinePeriods + 1) : mOnlineAverage;
        long averageOfflineTime = mState == State.DISCONNECTED ? (mOfflineAverage * mOfflinePeriods + currentOfflineTime) / (mOfflinePeriods + 1) : mOfflineAverage;

        status.set(mCtx.getString(R.string.connection_statistics),
                mCtx.getString(R.string.connection_details,
                        toDuration(now - mStartTime),
                        toDuration(mOnlineMillis + currentOnlineTime),
                        (mOnlinePeriods + (mState == State.CONNECTED ? 1 : 0)),
                        toDuration(Math.max(currentOnlineTime, mOnlineMaxMillis)),
                        toDuration(averageOnlineTime),
                        toDuration(mOfflineMillis + currentOfflineTime),
                        (mOfflinePeriods + (mState == State.DISCONNECTED ? 1 : 0)),
                        toDuration(Math.max(currentOfflineTime, mOfflineMaxMillis)),
                        toDuration(averageOfflineTime)));
    }

    private String toDuration(long durationMillis) {
        if (durationMillis < 1000) {
            return "-";
        }

        String retVal = "";

        // days
        if (durationMillis > 86400000) {
            long days = durationMillis / 86400000;

            retVal += mCtx.getString(R.string.days, days);
            durationMillis %= 86400000;
        }

        // hours
        if (durationMillis > 3600000) {
            long hours = durationMillis / 3600000;

            retVal += mCtx.getString(R.string.hours, hours);
            durationMillis %= 3600000;
        }

        // minutes
        if (durationMillis > 60000) {
            long minutes = durationMillis / 60000;

            retVal += mCtx.getString(R.string.minutes, minutes);
            durationMillis %= 60000;
        }

        // seconds
        if (durationMillis > 1000) {
            long seconds = durationMillis / 1000;

            retVal += mCtx.getString(R.string.seconds, seconds);
        }

        return retVal.substring(0, retVal.length() - 1);
    }

    synchronized void disconnected() {
        if (mState == State.CONNECTED) {
            mLastOfflineTime = System.currentTimeMillis();

            long duration = mLastOfflineTime - mLastOnlineTime;
            mOnlineMillis += duration;

            if (duration > mOnlineMaxMillis) {
                mOnlineMaxMillis = duration;
            }

            mOnlineAverage = (mOnlineAverage * mOnlinePeriods + duration) / ++mOnlinePeriods;
            mState = State.DISCONNECTED;
        }
    }

    synchronized void connected() {
        if (mState == State.DISCONNECTED) {
            mLastOnlineTime = System.currentTimeMillis();

            if (mLastOnlineTime > -1) {
                long duration = mLastOnlineTime - mLastOfflineTime;
                mOfflineMillis += duration;

                if (duration > mOfflineMaxMillis) {
                    mOfflineMaxMillis = duration;
                }

                mOfflineAverage = (mOfflineAverage * mOfflinePeriods + duration) / ++mOfflinePeriods;
                mState = State.CONNECTED;
            }
        }
    }

    public void terminate() {
        EventBus.getDefault().unregister(this);
    }

    private enum State {
        CONNECTED, DISCONNECTED
    }
}
