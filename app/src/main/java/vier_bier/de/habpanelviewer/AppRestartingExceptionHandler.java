package vier_bier.de.habpanelviewer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * UncaughtExceptionHandler that restarts the app in case of exceptions.
 */
class AppRestartingExceptionHandler implements Thread.UncaughtExceptionHandler {
    private final MainActivity myContext;
    private final int count;

    AppRestartingExceptionHandler(MainActivity context, int restartCount) {
        myContext = context;
        count = restartCount;
    }

    public void uncaughtException(Thread thread, Throwable exception) {
        Log.e("Habpanelview", "Uncaught exception", exception);

        restartApp(myContext, count);
    }

    static void restartApp(MainActivity context, int count) {
        Intent mStartActivity = context.getPackageManager().getLaunchIntentForPackage("vier_bier.de.habpanelviewer");
        mStartActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (count != -1) {
            mStartActivity.putExtra("crash", true);
            mStartActivity.putExtra("restartCount", count + 1);
        }

        int mPendingIntentId = 123456;
        PendingIntent mPendingIntent = PendingIntent.getActivity(context, mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 500, mPendingIntent);

        context.destroy();
        context.finishAndRemoveTask();

        System.exit(0);
    }
}
