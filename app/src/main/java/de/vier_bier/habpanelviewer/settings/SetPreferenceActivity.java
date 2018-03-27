package de.vier_bier.habpanelviewer.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.WindowManager;

import de.vier_bier.habpanelviewer.ScreenControllingActivity;

/**
 * Activity for setting preferences.
 */
public class SetPreferenceActivity extends ScreenControllingActivity {
    private SettingsFragment mSettingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean showOnLockScreen = prefs.getBoolean("pref_show_on_lock_screen", false);
        if (showOnLockScreen) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        mSettingsFragment = new SettingsFragment();
        mSettingsFragment.setArguments(getIntent().getExtras());
        getFragmentManager().beginTransaction().replace(android.R.id.content, mSettingsFragment).commit();
    }

    @Override
    public View getScreenOnView() {
        return mSettingsFragment.getView();
    }
}

