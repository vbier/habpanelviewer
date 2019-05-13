package de.vier_bier.habpanelviewer.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;

import de.vier_bier.habpanelviewer.Constants;
import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.UiUtil;

public class PreferencesMotion extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_motion);

        CheckBoxPreference cbPref = (CheckBoxPreference) findPreference(Constants.PREF_MOTION_DETECTION_ENABLED);
        cbPref.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
        boolean value = (Boolean) o;

        if (value) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

            if (prefs.getBoolean(Constants.PREF_ALLOW_WEBRTC, false)) {
                UiUtil.showCancelDialog(getActivity(), null,
                        "Enabling motion detection will disable WebRTC. Continue?",
                        (dialogInterface, i) -> {
                            final SharedPreferences prefs1 = PreferenceManager.getDefaultSharedPreferences(getActivity());
                            SharedPreferences.Editor editor1 = prefs1.edit();
                            editor1.putBoolean(Constants.PREF_ALLOW_WEBRTC, false);
                            editor1.putBoolean(Constants.PREF_MOTION_DETECTION_ENABLED, true);
                            editor1.apply();

                            CheckBoxPreference allowPreference = (CheckBoxPreference) findPreference(Constants.PREF_MOTION_DETECTION_ENABLED);
                            allowPreference.setChecked(true);
                        }, null);

                return false;
            }
        }

        return true;
    }
}

