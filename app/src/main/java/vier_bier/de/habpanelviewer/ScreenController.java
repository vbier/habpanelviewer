package vier_bier.de.habpanelviewer;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.util.Log;
import android.view.WindowManager;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Controller for the screen backlight.
 */
class ScreenController implements StateListener {
    private final PowerManager.WakeLock screenLock;
    private final Activity activity;

    private boolean enabled;
    private String screenOnItemName;
    private String screenOnItemState;

    private Pattern screenOnPattern;

    ScreenController(PowerManager pwrManager, Activity activity) {
        this.activity = activity;
        screenLock = pwrManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "HabpanelViewer");
    }

    void screenOff() {
        // make sure screen lock is released
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (screenLock.isHeld()) {
            screenLock.release();
        }
    }

    void screenOn() {
        if (!screenLock.isHeld()) {
            screenLock.acquire();
            screenLock.release();
        }
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    String getItemName() {
        return screenOnItemName;
    }

    String getItemState() {
        return screenOnItemState;
    }

    boolean isEnabled() {
        return enabled;
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

    void updateFromPreferences(SharedPreferences prefs) {
        screenOnPattern = null;
        screenOnItemName = prefs.getString("pref_screen_item", "");
        screenOnItemState = null;
        enabled = prefs.getBoolean("pref_screen_enabled", false);

        String onRegexpStr = prefs.getString("pref_screen_on_regex", "");
        if (!onRegexpStr.isEmpty()) {
            try {
                screenOnPattern = Pattern.compile(onRegexpStr);
            } catch (PatternSyntaxException e) {
                // is handled in the preferences
            }
        }
    }

}
