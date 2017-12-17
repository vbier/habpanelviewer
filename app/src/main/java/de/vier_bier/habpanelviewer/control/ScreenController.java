package de.vier_bier.habpanelviewer.control;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.openhab.StateUpdateListener;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Controller for the screen backlight.
 */
public class ScreenController implements StateUpdateListener {
    private ServerConnection mServerConnection;
    private final PowerManager.WakeLock screenOnLock;
    private final Activity activity;

    private boolean enabled;
    private boolean keepOn;
    private String screenOnItemName;
    private Pattern screenOnPattern;

    private ApplicationStatus mStatus;

    public ScreenController(PowerManager pwrManager, Activity activity, ServerConnection serverConnection) {
        mServerConnection = serverConnection;
        this.activity = activity;
        EventBus.getDefault().register(this);

        screenOnLock = pwrManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "HabpanelViewer");
    }

    public void screenOff() {
        Log.v("ScreenController", "screenOff");
        activity.findViewById(R.id.activity_main_webview).setKeepScreenOn(false);
    }

    private synchronized void screenOn() {
        if (!screenOnLock.isHeld()) {
            screenOnLock.acquire();
            screenOnLock.release();
        }

        if (keepOn) {
            Log.v("ScreenController", "screenOn");
            activity.findViewById(R.id.activity_main_webview).setKeepScreenOn(true);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        mStatus = status;
        addStatusItems();
    }

    public void updateFromPreferences(SharedPreferences prefs) {
        screenOnPattern = null;
        screenOnItemName = prefs.getString("pref_screen_item", "");

        enabled = prefs.getBoolean("pref_screen_enabled", false);
        keepOn = prefs.getBoolean("pref_screen_stay_enabled", false);

        String onRegexpStr = prefs.getString("pref_screen_on_regex", "");
        if (!onRegexpStr.isEmpty()) {
            try {
                screenOnPattern = Pattern.compile(onRegexpStr);
            } catch (PatternSyntaxException e) {
                // is handled in the preferences
            }
        }

        mServerConnection.subscribeItems(this, screenOnItemName);
    }

    private boolean isEnabled() {
        return enabled;
    }

    private void addStatusItems() {
        if (mStatus == null) {
            return;
        }

        if (isEnabled()) {
            mStatus.set(activity.getString(R.string.pref_screen), activity.getString(R.string.enabled)
                    + "\n" + screenOnItemName + "=" + mServerConnection.getState(screenOnItemName));
        } else {
            mStatus.set(activity.getString(R.string.pref_screen), activity.getString(R.string.disabled));
        }
    }

    @Override
    public void itemUpdated(String name, final String value) {
        Log.i("Habpanelview", "screen on item state=" + value);
        addStatusItems();

        activity.runOnUiThread(new Runnable() {
            public void run() {
                if (screenOnPattern != null && value != null && screenOnPattern.matcher(value).matches()) {
                    screenOn();
                } else {
                    screenOff();
                }
            }
        });
    }
}
