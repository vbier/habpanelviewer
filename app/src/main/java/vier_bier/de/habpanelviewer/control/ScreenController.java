package vier_bier.de.habpanelviewer.control;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import vier_bier.de.habpanelviewer.R;
import vier_bier.de.habpanelviewer.openhab.StateListener;
import vier_bier.de.habpanelviewer.status.ApplicationStatus;

/**
 * Controller for the screen backlight.
 */
public class ScreenController implements StateListener {
    private final PowerManager.WakeLock screenOnLock;
    private final Activity activity;

    private boolean enabled;
    private boolean keepOn;
    private String screenOnItemName;
    private String screenOnItemState;

    private Pattern screenOnPattern;

    private ApplicationStatus mStatus;

    public ScreenController(PowerManager pwrManager, Activity activity) {
        this.activity = activity;
        EventBus.getDefault().register(this);

        screenOnLock = pwrManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "HabpanelViewer");
    }

    public void screenOff() {
        activity.findViewById(R.id.activity_main_webview).setKeepScreenOn(false);
    }

    public synchronized void screenOn() {
        if (!screenOnLock.isHeld()) {
            screenOnLock.acquire();
            screenOnLock.release();
        }

        if (keepOn) {
            activity.findViewById(R.id.activity_main_webview).setKeepScreenOn(true);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        mStatus = status;
        addStatusItems();
    }

    @Override
    public void updateState(String name, String state) {
        if (name.equals(screenOnItemName)) {
            if (screenOnItemState != null && screenOnItemState.equals(state)) {
                Log.i("Habpanelview", "unchanged screen on item state=" + state);
                return;
            }

            Log.i("Habpanelview", "screen on item state=" + state + ", old state=" + screenOnItemState);
            screenOnItemState = state;
            addStatusItems();

            activity.runOnUiThread(new Runnable() {
                public void run() {
                    if (screenOnPattern != null && screenOnItemState != null && screenOnPattern.matcher(screenOnItemState).matches()) {
                        screenOn();
                    } else {
                        screenOff();
                    }
                }
            });
        }
    }

    public void updateFromPreferences(SharedPreferences prefs) {
        screenOnPattern = null;
        if (screenOnItemName == null || !screenOnItemName.equalsIgnoreCase(prefs.getString("pref_screen_item", ""))) {
            screenOnItemName = prefs.getString("pref_screen_item", "");
            screenOnItemState = null;
        }
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
    }

    private boolean isEnabled() {
        return enabled;
    }

    private void addStatusItems() {
        if (mStatus == null) {
            return;
        }

        if (isEnabled()) {
            mStatus.set("Backlight Control", "enabled\n" + screenOnItemName + "=" + screenOnItemState);
        } else {
            mStatus.set("Backlight Control", "disabled");
        }
    }
}
