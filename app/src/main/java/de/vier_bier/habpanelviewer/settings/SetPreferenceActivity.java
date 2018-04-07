package de.vier_bier.habpanelviewer.settings;

import android.os.Bundle;
import android.view.View;

import de.vier_bier.habpanelviewer.ScreenControllingActivity;

/**
 * Activity for setting preferences.
 */
public class SetPreferenceActivity extends ScreenControllingActivity {
    private SettingsFragment mSettingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSettingsFragment = new SettingsFragment();
        mSettingsFragment.setArguments(getIntent().getExtras());
        getFragmentManager().beginTransaction().replace(android.R.id.content, mSettingsFragment).commit();
    }

    @Override
    public View getScreenOnView() {
        return mSettingsFragment.getView();
    }
}

