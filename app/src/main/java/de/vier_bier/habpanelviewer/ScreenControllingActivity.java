package de.vier_bier.habpanelviewer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import de.vier_bier.habpanelviewer.openhab.ServerConnection;

/**
 * Activity that support controlling screen state.
 */
public abstract class ScreenControllingActivity extends AppCompatActivity {
    private static final String TAG = "HPV-ScreenControllingAc";

    private static final String ACTION_KEEP_SCREEN_ON = "ACTION_KEEP_SCREEN_ON";
    private static final String FLAG_KEEP_SCREEN_ON = "keepScreenOn";
    private static boolean mKeepScreenOn = false;

    private static final String ACTION_SET_BRIGHTNESS = "ACTION_SET_BRIGHTNESS";
    private static final String FLAG_BRIGHTNESS = "brightness";
    private static float mBrightness = -1;

    private static boolean mTouchEnabled;
    private static String mTouchItem;
    private static long mTouchTime;
    private static int mTouchTimeout;

    public static void setBrightness(Context ctx, float brightness) {
        Log.d(TAG, "sending brightness intent: " + brightness);
        mBrightness = brightness;

        Intent i = new Intent(ACTION_SET_BRIGHTNESS);
        i.putExtra(FLAG_BRIGHTNESS, brightness);
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(i);
    }

    public static void setKeepScreenOn(Context ctx, boolean keepOn) {
        Log.d(TAG, "sending keepOn intent: " + keepOn);
        mKeepScreenOn = keepOn;

        Intent i = new Intent(ACTION_KEEP_SCREEN_ON);
        i.putExtra(FLAG_KEEP_SCREEN_ON, keepOn);
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        setTheme(UiUtil.getThemeId(prefs.getString("pref_theme", "dark")));
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

        mTouchEnabled = prefs.getBoolean("pref_usage_enabled", false);
        mTouchItem = prefs.getString("pref_usage_item", "");
        mTouchTimeout = Integer.parseInt(prefs.getString("pref_usage_timeout", "60000"));

        IntentFilter f = new IntentFilter();
        f.addAction(ACTION_KEEP_SCREEN_ON);
        f.addAction(ACTION_SET_BRIGHTNESS);
        LocalBroadcastManager.getInstance(this).registerReceiver(onEvent, f);
        Log.d(TAG, "registered receiver");

        Log.d(TAG, "onStart: set keep on: " + mKeepScreenOn);
        getScreenOnView().setKeepScreenOn(mKeepScreenOn);

        Log.d(TAG, "onStart: set brightness: " + mBrightness);
        setBrightness(mBrightness);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean value = super.dispatchTouchEvent(ev);

        if (mTouchEnabled && ev.getActionMasked() == MotionEvent.ACTION_DOWN
                && mTouchTime + mTouchTimeout * 1000 < System.currentTimeMillis()) {
            Intent i = new Intent(ServerConnection.ACTION_SET_WITH_TIMEOUT);
            i.putExtra(ServerConnection.FLAG_ITEM_NAME, mTouchItem);
            i.putExtra(ServerConnection.FLAG_ITEM_STATE, "CLOSED");
            i.putExtra(ServerConnection.FLAG_ITEM_TIMEOUT_STATE, "OPEN");
            i.putExtra(ServerConnection.FLAG_TIMEOUT, mTouchTimeout);
            LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(i);

            mTouchTime = System.currentTimeMillis();
        }
        return value;
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(onEvent);
        Log.d(TAG, "receiver unregistered");

        super.onStop();
    }

    protected abstract View getScreenOnView();

    private final BroadcastReceiver onEvent = new BroadcastReceiver() {
        public void onReceive(Context ctx, Intent i) {
            if (ACTION_KEEP_SCREEN_ON.equals(i.getAction())) {
                final boolean keepOn = i.getBooleanExtra(FLAG_KEEP_SCREEN_ON, false);

                runOnUiThread(() -> {
                    Log.d(TAG, "onReceive: set keep on: " + keepOn);
                    getScreenOnView().setKeepScreenOn(keepOn);
                });
            } else if (ACTION_SET_BRIGHTNESS.equals(i.getAction())) {
                final float brightness = i.getFloatExtra(FLAG_BRIGHTNESS, 1.0f);

                runOnUiThread(() -> {
                    Log.d(TAG, "onReceive: set brightness: " + brightness);
                    setBrightness(brightness);
                });

            }
        }
    };

    private void setBrightness(float brightness) {
        final WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = brightness;
        getWindow().setAttributes(layout);
    }
}
