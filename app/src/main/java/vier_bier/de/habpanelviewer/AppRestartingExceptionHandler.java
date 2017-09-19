package vier_bier.de.habpanelviewer;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by volla on 13.09.17.
 */

public class AppRestartingExceptionHandler implements Thread.UncaughtExceptionHandler {
    private final Activity myContext;
    private final int count;

    public AppRestartingExceptionHandler(Activity context, int restartCount) {
        myContext = context;
        count = restartCount;
    }

    public void uncaughtException(Thread thread, Throwable exception) {
        Log.e("Habpanelview", "Uncaught exception", exception);

        Intent mStartActivity = myContext.getPackageManager().getLaunchIntentForPackage("vier_bier.de.habpanelviewer");
        mStartActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mStartActivity.putExtra("crash", true);
        mStartActivity.putExtra("restartCount", count + 1);

        int mPendingIntentId = 123456;
        PendingIntent mPendingIntent = PendingIntent.getActivity(myContext, mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager mgr = (AlarmManager) myContext.getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 500, mPendingIntent);

        myContext.finishAndRemoveTask();
        System.exit(0);
    }
}
