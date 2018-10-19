package de.vier_bier.habpanelviewer.preferences;

import android.os.Bundle;

import de.vier_bier.habpanelviewer.R;

public class PreferencesReporting extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_reporting);

        boolean motionEnabled = false;
        boolean proximityEnabled = false;
        boolean pressureEnabled = false;
        boolean brightnessEnabled = false;
        boolean temperatureEnabled = false;

        Bundle bundle = this.getArguments();
        if (bundle != null) {
            motionEnabled = bundle.getBoolean("motion_enabled");
            proximityEnabled = bundle.getBoolean("proximity_enabled");
            pressureEnabled = bundle.getBoolean("pressure_enabled");
            brightnessEnabled = bundle.getBoolean("brightness_enabled");
            temperatureEnabled = bundle.getBoolean("temperature_enabled");
        }

        // disable preferences if functionality is not available
        if (!motionEnabled) {
            findPreference("nested_pref_motion").setEnabled(false);
            findPreference("nested_pref_motion").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_motion)));
        }
        if (!proximityEnabled) {
            findPreference("nested_pref_proximity").setEnabled(false);
            findPreference("nested_pref_proximity").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_proximity)));
        }
        if (!pressureEnabled) {
            findPreference("nested_pref_pressure").setEnabled(false);
            findPreference("nested_pref_pressure").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_pressure)));
        }
        if (!brightnessEnabled) {
            findPreference("nested_pref_brightness").setEnabled(false);
            findPreference("nested_pref_brightness").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_brightness)));
        }
        if (!temperatureEnabled) {
            findPreference("nested_pref_temperature").setEnabled(false);
            findPreference("nested_pref_temperature").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_temperature)));
        }
    }
}
