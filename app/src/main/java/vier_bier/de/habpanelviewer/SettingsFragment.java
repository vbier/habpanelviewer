package vier_bier.de.habpanelviewer;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import com.jakewharton.processphoenix.ProcessPhoenix;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

/**
 * Fragment for preferences.
 */
public class SettingsFragment extends PreferenceFragment {
    private boolean flashEnabled = false;
    private boolean motionEnabled = false;
    private boolean screenEnabled = false;

    private boolean newApi = false;
    private boolean ignoreCertErrors = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = this.getArguments();
        if (bundle != null) {
            flashEnabled = bundle.getBoolean("flash_enabled");
            motionEnabled = bundle.getBoolean("motion_enabled");
            screenEnabled = bundle.getBoolean("screen_enabled");
        }

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        // disable preferences if functionality is not available
        if (!flashEnabled) {
            findPreference("pref_flash").setEnabled(false);
            findPreference("pref_flash").setSummary("Flash control is not available because no back camera with flash has been found");
        }
        if (!motionEnabled) {
            findPreference("pref_motion").setEnabled(false);
            findPreference("pref_motion").setSummary("Motion detection is not available because no front camera has been found");
        }
        if (!screenEnabled) {
            findPreference("pref_screen").setEnabled(false);
            findPreference("pref_screen").setSummary("Backlight control is not available on this device");
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

    @Override
    public void onStart() {
        super.onStart();
        newApi = ((CheckBoxPreference) findPreference("pref_motion_detection_new_api")).isChecked();
        ignoreCertErrors = ((CheckBoxPreference) findPreference("pref_ignore_ssl_errors")).isChecked();
    }

    @Override
    public void onStop() {
        if (newApi != ((CheckBoxPreference) findPreference("pref_motion_detection_new_api")).isChecked()
                || ignoreCertErrors != ((CheckBoxPreference) findPreference("pref_ignore_ssl_errors")).isChecked()) {
            ProcessPhoenix.triggerRebirth(getActivity());
        }
        super.onStop();
    }

    private class PackageValidatingListener implements Preference.OnPreferenceChangeListener {
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

    private class URLValidatingListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object o) {
            String text = (String) o;

            if (text == null || text.isEmpty()) {
                return true;
            }
            AsyncTask<String, Void, Void> validator = new AsyncTask<String, Void, Void>() {
                @Override
                protected Void doInBackground(String... urls) {
                    try {
                        URL url = new URL(urls[0]);
                        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                        if (urlConnection instanceof HttpsURLConnection) {
                            ((HttpsURLConnection) urlConnection).setSSLSocketFactory(SSEClient.createSslContext(ignoreCertErrors).getSocketFactory());

                            HostnameVerifier hostnameVerifier = new HostnameVerifier() {
                                @Override
                                public boolean verify(String hostname, SSLSession session) {
                                    HostnameVerifier hv =
                                            HttpsURLConnection.getDefaultHostnameVerifier();
                                    return hv.verify("openhab.org", session);
                                }
                            };
                            ((HttpsURLConnection) urlConnection).setHostnameVerifier(hostnameVerifier);
                        }
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

    private class PatternValidatingListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            String text = (String) o;
            try {
                //noinspection ResultOfMethodCallIgnored
                Pattern.compile(text);
            } catch (PatternSyntaxException e) {
                UiUtil.showDialog(getActivity(), preference.getTitle() + " invalid", e.getMessage());
            }

            return true;
        }
    }
}
