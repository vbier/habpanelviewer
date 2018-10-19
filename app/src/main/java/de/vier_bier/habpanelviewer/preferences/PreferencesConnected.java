package de.vier_bier.habpanelviewer.preferences;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.UiUtil;

public class PreferencesConnected extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_connected);

        EditTextPreference connectedIntervalPreference =
                (EditTextPreference) findPreference("pref_connected_interval");
        connectedIntervalPreference.setOnPreferenceChangeListener(new NumberValidatingListener(0, 6000));
    }

    private class NumberValidatingListener implements Preference.OnPreferenceChangeListener {
        private final int minVal;
        private final int maxVal;

        NumberValidatingListener(int minVal, int maxVal) {
            this.minVal = minVal;
            this.maxVal = maxVal;
        }

        @Override
        public boolean onPreferenceChange(final Preference preference, Object o) {
            boolean invalid;
            try {
                int val = Integer.parseInt((String) o);

                invalid = val < minVal || val > maxVal;
            } catch (NumberFormatException e) {
                invalid = true;
            }

            if (invalid && getActivity() != null && !getActivity().isFinishing()) {
                UiUtil.showDialog(getActivity(),
                        preference.getTitle() + " " + getResources().getString(R.string.invalid),
                        getString(R.string.noValidIntInRange, minVal, maxVal));
                return false;
            }

            return true;
        }
    }
}
