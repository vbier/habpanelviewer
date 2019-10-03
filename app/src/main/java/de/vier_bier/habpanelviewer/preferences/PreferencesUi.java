package de.vier_bier.habpanelviewer.preferences;

import android.os.Bundle;
import android.preference.ListPreference;

import org.greenrobot.eventbus.EventBus;

import de.vier_bier.habpanelviewer.Constants;
import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.UiUtil;

public class PreferencesUi extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_ui);

        ListPreference themePreference = (ListPreference) findPreference(Constants.PREF_THEME);
        themePreference.setOnPreferenceChangeListener((preference, o) -> {
            if (getActivity() != null && !getActivity().isFinishing()) {
                if (UiUtil.themeChanged((String) o, getActivity())) {
                    UiUtil.showSnackBar(getActivity().findViewById(R.id.myCoordinatorLayout),
                            R.string.themeChangedRestartRequired, R.string.action_restart,
                            view -> EventBus.getDefault().post(new Constants.Restart()));
                }
            }

            return true;
        });
    }
}
