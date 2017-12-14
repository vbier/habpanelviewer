package de.vier_bier.habpanelviewer.settings;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

/**
 * Activity for setting preferences.
 */
public class SetPreferenceActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        SettingsFragment f = new SettingsFragment();
        f.setArguments(getIntent().getExtras());
        getFragmentManager().beginTransaction().replace(android.R.id.content, f).commit();
    }
}

