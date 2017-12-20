package de.vier_bier.habpanelviewer.settings;

import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import com.jakewharton.processphoenix.ProcessPhoenix;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.security.GeneralSecurityException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.net.ssl.SSLException;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.UiUtil;
import de.vier_bier.habpanelviewer.openhab.FetchItemStateTask;
import de.vier_bier.habpanelviewer.openhab.SubscriptionListener;
import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;

/**
 * Fragment for preferences.
 */
public class SettingsFragment extends PreferenceFragment {
    private boolean flashEnabled = false;
    private boolean motionEnabled = false;
    private boolean screenEnabled = false;
    private boolean proximityEnabled = false;
    private boolean pressureEnabled = false;
    private boolean brightnessEnabled = false;
    private boolean temperatureEnabled = false;

    private boolean newApi = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = this.getArguments();
        if (bundle != null) {
            flashEnabled = bundle.getBoolean("flash_enabled");
            motionEnabled = bundle.getBoolean("motion_enabled");
            screenEnabled = bundle.getBoolean("screen_enabled");
            proximityEnabled = bundle.getBoolean("proximity_enabled");
            pressureEnabled = bundle.getBoolean("pressure_enabled");
            brightnessEnabled = bundle.getBoolean("brightness_enabled");
            temperatureEnabled = bundle.getBoolean("temperature_enabled");
        }

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        // disable preferences if functionality is not available
        if (!flashEnabled) {
            findPreference("pref_flash").setEnabled(false);
            findPreference("pref_flash").setSummary(getString(R.string.pref_flash) + getString(R.string.notAvailableOnDevice));
        }
        if (!motionEnabled) {
            findPreference("pref_motion").setEnabled(false);
            findPreference("pref_motion").setSummary(getString(R.string.pref_motion) + getString(R.string.notAvailableOnDevice));
        }
        if (!screenEnabled) {
            findPreference("pref_screen").setEnabled(false);
            findPreference("pref_screen").setSummary(getString(R.string.pref_screen) + getString(R.string.notAvailableOnDevice));
        }
        if (!proximityEnabled) {
            findPreference("pref_proximity").setEnabled(false);
            findPreference("pref_proximity").setSummary(getString(R.string.pref_proximity) + getString(R.string.notAvailableOnDevice));
        }
        if (!pressureEnabled) {
            findPreference("pref_pressure").setEnabled(false);
            findPreference("pref_pressure").setSummary(getString(R.string.pref_pressure) + getString(R.string.notAvailableOnDevice));
        }
        if (!brightnessEnabled) {
            findPreference("pref_brightness").setEnabled(false);
            findPreference("pref_brightness").setSummary(getString(R.string.pref_brightness) + getString(R.string.notAvailableOnDevice));
        }
        if (!temperatureEnabled) {
            findPreference("pref_temperature").setEnabled(false);
            findPreference("pref_temperature").setSummary(getString(R.string.pref_temperature) + getString(R.string.notAvailableOnDevice));
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

        // add validation to the package name
        EditTextPreference pkgPreference = (EditTextPreference) findPreference("pref_app_package");
        pkgPreference.setOnPreferenceChangeListener(new PackageValidatingListener());

        // add validation to the items
        for (String key : new String[]{"pref_flash_item", "pref_motion_item", "pref_screen_item",
                "pref_proximity_item", "pref_pressure_item", "pref_brightness_item",
                "pref_volume_item", "pref_temperature_item", "pref_battery_item",
                "pref_battery_charging_item", "pref_battery_level_item"}) {
            final EditText editText = ((EditTextPreference) findPreference(key)).getEditText();
            editText.addTextChangedListener(new ValidatingTextWatcher() {
                @Override
                public void afterTextChanged(final Editable editable) {
                    final String itemName = editable.toString();

                    final String serverUrl = ((EditTextPreference) findPreference("pref_url")).getText();
                    FetchItemStateTask task = new FetchItemStateTask(serverUrl, new SubscriptionListener() {
                        @Override
                        public void itemInvalid(String name) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    editText.setTextColor(Color.RED);
                                }
                            });
                        }

                        @Override
                        public void itemUpdated(String name, String value) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    editText.setTextColor(Color.WHITE);
                                }
                            });
                        }
                    });
                    task.execute(new String[]{itemName});
                }
            });
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        newApi = ((CheckBoxPreference) findPreference("pref_motion_detection_new_api")).isChecked();
    }

    @Override
    public void onStop() {
        if (newApi != ((CheckBoxPreference) findPreference("pref_motion_detection_new_api")).isChecked()) {
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
                    UiUtil.showDialog(getActivity(), preference.getTitle() + " " + getString(R.string.invalid), getString(R.string.couldNotFindApp) + pkg);
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
                        HttpURLConnection urlConnection = ConnectionUtil.createUrlConnection(urls[0]);
                        urlConnection.connect();
                        urlConnection.disconnect();
                    } catch (MalformedURLException e) {
                        UiUtil.showDialog(getActivity(), preference.getTitle() + " " + getString(R.string.invalid), urls[0] + getString(R.string.notValidUrl));
                    } catch (SSLException e) {
                        UiUtil.showDialog(getActivity(), getString(R.string.certInvalid), getString(R.string.couldNotConnect) + " " + urls[0] + ".\n" + getString(R.string.acceptCertWhenOurOfSettings));
                    } catch (IOException | GeneralSecurityException e) {
                        UiUtil.showDialog(getActivity(), preference.getTitle() + " " + getString(R.string.invalid), getString(R.string.couldNotConnect) + " " + urls[0]);
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
                if (text != null) {
                    Pattern.compile(text);
                }
            } catch (PatternSyntaxException e) {
                UiUtil.showDialog(getActivity(), preference.getTitle() + " " + getString(R.string.invalid), e.getMessage());
            }

            return true;
        }
    }

    private abstract class ValidatingTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }
    }
}
