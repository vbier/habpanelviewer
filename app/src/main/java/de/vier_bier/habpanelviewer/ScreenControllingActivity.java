package de.vier_bier.habpanelviewer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

/**
 * Activity that support controlling screen state.
 */
public abstract class ScreenControllingActivity extends Activity {
    public static final String ACTION_KEEP_SCREEN_ON = "ACTION_KEEP_SCREEN_ON";
    private static final String FLAG_KEEP_SCREEN_ON = "keepScreenOn";

    public static void setKeepScreenOn(Context ctx, boolean keepOn) {
        Log.d("ScreenControl", "sending intent: " + keepOn);

        Intent i = new Intent(ACTION_KEEP_SCREEN_ON);
        i.putExtra(FLAG_KEEP_SCREEN_ON, keepOn);
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    protected void onStart() {
        super.onStart();

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean showOnLockScreen = prefs.getBoolean("pref_show_on_lock_screen", false);
        if (showOnLockScreen) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        IntentFilter f = new IntentFilter(ACTION_KEEP_SCREEN_ON);
        LocalBroadcastManager.getInstance(this).registerReceiver(onEvent, f);
        Log.d("ScreenControl", "register receiver");

        boolean keepOn = getIntent().getBooleanExtra(FLAG_KEEP_SCREEN_ON, false);
        Log.d("ScreenControl", "onStart: set keep on: " + keepOn);
        getScreenOnView().setKeepScreenOn(keepOn);
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(onEvent);
        Log.d("ScreenControl", "receiver unregistered");

        super.onStop();
    }

    public abstract View getScreenOnView();

    private BroadcastReceiver onEvent = new BroadcastReceiver() {
        public void onReceive(Context ctx, Intent i) {
            final boolean keepOn = i.getBooleanExtra(FLAG_KEEP_SCREEN_ON, false);

            runOnUiThread(() -> {
                Log.d("ScreenControl", "onReceive: set keep on: " + keepOn);
                getScreenOnView().setKeepScreenOn(keepOn);
            });
        }
    };
}
