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
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * Activity that support controlling screen state.
 */
public abstract class ScreenControllingActivity extends AppCompatActivity {
    private static boolean mKeepScreenOn = false;

    private static float mBrightness = -1;

    private static boolean mTouchEnabled;
    private static String mTouchItem;
    private static long mTouchTime;
    private static int mTouchTimeout;

    public static void setBrightness(Context ctx, float brightness) {
        mBrightness = brightness;

        Intent i = new Intent(Constants.INTENT_ACTION_SET_BRIGHTNESS);
        i.putExtra(Constants.INTENT_FLAG_BRIGHTNESS, brightness);
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(i);
    }

    public static void setKeepScreenOn(Context ctx, boolean keepOn) {
        mKeepScreenOn = keepOn;

        Intent i = new Intent(Constants.INTENT_ACTION_KEEP_SCREEN_ON);
        i.putExtra(Constants.INTENT_FLAG_KEEP_SCREEN_ON, keepOn);
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        setTheme(UiUtil.getThemeId(prefs.getString(Constants.PREF_THEME, "dark")));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    protected void onStart() {
        super.onStart();

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean showOnLockScreen = prefs.getBoolean(Constants.PREF_SHOW_ON_LOCK_SCREEN, false);
        if (showOnLockScreen) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        mTouchEnabled = prefs.getBoolean(Constants.PREF_USAGE_ENABLED, false);
        mTouchItem = prefs.getString(Constants.PREF_USAGE_ITEM, "");
        mTouchTimeout = Integer.parseInt(prefs.getString(Constants.PREF_USAGE_TIMEOUT, "60000"));

        IntentFilter f = new IntentFilter();
        f.addAction(Constants.INTENT_ACTION_KEEP_SCREEN_ON);
        f.addAction(Constants.INTENT_ACTION_SET_BRIGHTNESS);
        LocalBroadcastManager.getInstance(this).registerReceiver(onEvent, f);

        setKeepScreenOn(mKeepScreenOn);
        setBrightness(mBrightness);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean value = super.dispatchTouchEvent(ev);

        if (mTouchEnabled && ev.getActionMasked() == MotionEvent.ACTION_DOWN
                && mTouchTime + mTouchTimeout * 1000 < System.currentTimeMillis()) {
            Intent i = new Intent(Constants.INTENT_ACTION_SET_WITH_TIMEOUT);
            i.putExtra(Constants.INTENT_FLAG_ITEM_NAME, mTouchItem);
            i.putExtra(Constants.INTENT_FLAG_ITEM_STATE, "CLOSED");
            i.putExtra(Constants.INTENT_FLAG_ITEM_TIMEOUT_STATE, "OPEN");
            i.putExtra(Constants.INTENT_FLAG_TIMEOUT, mTouchTimeout);
            LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(i);

            mTouchTime = System.currentTimeMillis();
        }
        return value;
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(onEvent);

        super.onStop();
    }

    protected abstract View getScreenOnView();

    private final BroadcastReceiver onEvent = new BroadcastReceiver() {
        public void onReceive(Context ctx, Intent i) {
            if (Constants.INTENT_ACTION_KEEP_SCREEN_ON.equals(i.getAction())) {
                final boolean keepOn = i.getBooleanExtra(Constants.INTENT_FLAG_KEEP_SCREEN_ON, false);

                runOnUiThread(() -> setKeepScreenOn(keepOn));
            } else if (Constants.INTENT_ACTION_SET_BRIGHTNESS.equals(i.getAction())) {
                final float brightness = i.getFloatExtra(Constants.INTENT_FLAG_BRIGHTNESS, 1.0f);

                runOnUiThread(() -> setBrightness(brightness));
            }
        }
    };

    private void setKeepScreenOn(boolean keepOn) {
        getScreenOnView().setKeepScreenOn(keepOn);
        if (keepOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void setBrightness(float brightness) {
        final WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = brightness;
        getWindow().setAttributes(layout);
    }
}
