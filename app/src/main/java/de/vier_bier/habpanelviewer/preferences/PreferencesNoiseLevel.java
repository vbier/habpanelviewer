package de.vier_bier.habpanelviewer.preferences;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.legacy.app.FragmentCompat;

import de.vier_bier.habpanelviewer.Constants;
import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.UiUtil;

public class PreferencesNoiseLevel extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_noise_level);

        CheckBoxPreference cbPref = (CheckBoxPreference) findPreference(Constants.PREF_NOISE_LEVEL_ENABLED);
        cbPref.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
        boolean value = (Boolean) o;

        if (value) {
            if (needsPermissions()) {
                requestMissingPermissions();
                return false;
            }
        }

        return true;
    }

    private boolean needsPermissions() {
        return ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED;
    }

    private void requestMissingPermissions() {
        FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                Constants.REQUEST_RECORD_AUDIO);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == Constants.REQUEST_RECORD_AUDIO) {
            setAllowPreviewPref(grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED);
        }
    }

    private void setAllowPreviewPref(boolean allowPreview) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if (!prefs.getBoolean(Constants.PREF_NOISE_LEVEL_ENABLED, false)) {
            SharedPreferences.Editor editor1 = prefs.edit();
            editor1.putBoolean(Constants.PREF_NOISE_LEVEL_ENABLED, true);
            editor1.apply();

            CheckBoxPreference allowPreference = (CheckBoxPreference) findPreference(Constants.PREF_NOISE_LEVEL_ENABLED);
            allowPreference.setChecked(true);
        }
    }
}

