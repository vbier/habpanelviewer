package de.vier_bier.habpanelviewer.preferences;

import android.os.Bundle;

import de.vier_bier.habpanelviewer.Constants;
import de.vier_bier.habpanelviewer.R;

public class PreferencesMain extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_main);

        Bundle bundle = this.getArguments();
        if (bundle == null || !Boolean.TRUE.equals(bundle.getBoolean(Constants.INTENT_FLAG_CAMERA_ENABLED))) {
            findPreference("nested_pref_camera").setEnabled(false);
            findPreference("nested_pref_camera").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_camera)));
        }
    }
}
