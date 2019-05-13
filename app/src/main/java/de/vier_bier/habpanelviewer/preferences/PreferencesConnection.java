package de.vier_bier.habpanelviewer.preferences;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;

import java.net.MalformedURLException;
import java.net.URL;

import de.vier_bier.habpanelviewer.Constants;
import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.UiUtil;

public class PreferencesConnection extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_connection);

        EditTextPreference urlPreference = (EditTextPreference) findPreference(Constants.PREF_SERVER_URL);
        urlPreference.setOnPreferenceChangeListener(new URLValidatingListener());
    }

    private class URLValidatingListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object o) {
            String text = (String) o;

            if (text == null || text.isEmpty()) {
                return true;
            }

            String dialogText = null;

            try {
                URL uri = new URL(text);

                if (uri.getPort() < 0 || uri.getPort() > 65535) {
                    dialogText = "Port invalid: " + uri.getPort();
                }
            } catch (MalformedURLException e) {
                dialogText = "URL invalid: " + e.getLocalizedMessage();
            }

            if (dialogText != null) {
                UiUtil.showDialog(getActivity(), preference.getTitle() + " "
                        + getActivity().getResources().getString(R.string.invalid), dialogText);
            }

            return true;
        }
    }
}
