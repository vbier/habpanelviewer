package de.vier_bier.habpanelviewer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.jakewharton.processphoenix.ProcessPhoenix;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * UncaughtExceptionHandler that restarts the app in case of exceptions.
 */
class AppRestartingExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "HPV-AppRestartingExHa";

    private final MainActivity mCtx;
    private final int mCount;

    private int mMaxRestarts;
    private boolean mRestartEnabled;

    private final Thread.UncaughtExceptionHandler mDefaultHandler;

    AppRestartingExceptionHandler(MainActivity context, Thread.UncaughtExceptionHandler defaultHandler, int restartCount) {
        mCtx = context;
        mDefaultHandler = defaultHandler;
        mCount = restartCount;

        EventBus.getDefault().register(this);
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        if (mRestartEnabled) {
            String state = mCtx.getString(R.string.enabled);
            state += "\n" + mCtx.getString(R.string.restartCounter, mCount, mMaxRestarts);

            status.set(mCtx.getString(R.string.pref_restart), state);
        } else {
            status.set(mCtx.getString(R.string.pref_restart), mCtx.getString(R.string.disabled));
        }
    }

    public void uncaughtException(@NotNull Thread thread, @NotNull Throwable exception) {
        Log.e(TAG, "Uncaught exception", exception);

        // make sure to close the camera
        final CountDownLatch l = new CountDownLatch(1);
        mCtx.getCamera().terminate(l);
        try {
            l.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "failed to terminate camera");
        }

        if (mCount < mMaxRestarts && mRestartEnabled) {
            restartApp(mCtx, mCount);
        } else {
            mDefaultHandler.uncaughtException(thread, exception);
        }
    }

    private static void restartApp(MainActivity context, int count) {
        Intent mStartActivity = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (mStartActivity != null) {
            mStartActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (count != -1) {
                mStartActivity.putExtra(Constants.INTENT_FLAG_CRASH, true);
                mStartActivity.putExtra(Constants.INTENT_FLAG_RESTART_COUNT, count + 1);
            }

            context.destroy();
            ProcessPhoenix.triggerRebirth(context, mStartActivity);
        }
    }

    public void updateFromPreferences(SharedPreferences prefs) {
        try {
            mMaxRestarts = Integer.parseInt(prefs.getString(Constants.PREF_MAX_RESTARTS, "5"));
        } catch (NumberFormatException e) {
            Log.e(TAG, "could not parse pref_max_restarts value "
                    + prefs.getString(Constants.PREF_MAX_RESTARTS, "5") + ". using default 5");
            mMaxRestarts = 5;
        }

        mRestartEnabled = prefs.getBoolean(Constants.PREF_RESTART_ENABLED, false);
    }

    void disable() {
        Thread.setDefaultUncaughtExceptionHandler(mDefaultHandler);
    }
}
