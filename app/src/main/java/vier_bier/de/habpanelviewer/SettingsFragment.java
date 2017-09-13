package vier_bier.de.habpanelviewer;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Created by volla on 07.09.17.
 */

public class SettingsFragment extends PreferenceFragment {
    private boolean flashEnabled = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = this.getArguments();
        if (bundle != null) {
            flashEnabled = bundle.getBoolean("flash_enabled");
        }

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        // disable flash preferences if flash not available
        if (!flashEnabled) {
            findPreference("pref_flash").setEnabled(false);
            findPreference("pref_flash").setSummary("Flash control is not available because no back camera with flash has been found");
        }

        // add validation to the regexps
        String[] regExpPrefIds = new String[]{"pref_flash_pulse_regex", "pref_flash_steady_regex", "pref_screen_on_regex"};
        PatternValidatingListener l = new PatternValidatingListener();
        for (String id : regExpPrefIds) {
            EditTextPreference textPreference = (EditTextPreference) findPreference(id);
            textPreference.setOnPreferenceChangeListener(l);

            l.onPreferenceChange(textPreference, textPreference.getText());
        }

        // add validation to the openHAB url
        EditTextPreference urlPreference = (EditTextPreference) findPreference("pref_url");
        urlPreference.setOnPreferenceChangeListener(new URLValidatingListener());

        // add validation to the openHAB url
        EditTextPreference pkgPreference = (EditTextPreference) findPreference("pref_app_package");
        pkgPreference.setOnPreferenceChangeListener(new PackageValidatingListener());
    }

    class PackageValidatingListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            String pkg = (String) o;

            if (!pkg.isEmpty()) {
                Intent launchIntent = getActivity().getPackageManager().getLaunchIntentForPackage(pkg);
                if (launchIntent == null) {
                    UiUtil.showDialog(getActivity(), preference.getTitle() + " invalid", "Could not find app for package " + pkg);
                }
            }
            return true;
        }
    }

    class URLValidatingListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object o) {
            String text = (String) o;

            AsyncTask<String, Void, Void> validator = new AsyncTask<String, Void, Void>() {
                @Override
                protected Void doInBackground(String... urls) {
                    try {
                        URL url = new URL(urls[0]);
                        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                        urlConnection.setConnectTimeout(200);
                        urlConnection.connect();
                        urlConnection.disconnect();
                    } catch (MalformedURLException e) {
                        UiUtil.showDialog(getActivity(), preference.getTitle() + " invalid", urls[0] + " is not a valid URL");
                    } catch (IOException e) {
                        UiUtil.showDialog(getActivity(), preference.getTitle() + " invalid", "Could not connect to openHAB at URL " + urls[0]);
                    }

                    return null;
                }
            };
            validator.execute(text);

            return true;
        }
    }

    class PatternValidatingListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            String text = (String) o;
            try {
                Pattern.compile(text);
            } catch (PatternSyntaxException e) {
                UiUtil.showDialog(getActivity(), preference.getTitle() + " invalid", e.getMessage());
            }

            return true;
        }
    }
}
