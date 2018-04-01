package de.vier_bier.habpanelviewer.settings;

import android.app.LoaderManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.security.GeneralSecurityException;
import java.util.List;

import javax.net.ssl.SSLException;

import de.vier_bier.habpanelviewer.AdminReceiver;
import de.vier_bier.habpanelviewer.BuildConfig;
import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.UiUtil;
import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;

import static android.app.Activity.RESULT_OK;

/**
 * Fragment for preferences.
 */
public class SettingsFragment extends PreferenceFragment {
    private DevicePolicyManager mDPM;

    private static final String[] ITEMS_PREFS = new String[]{
            "pref_motion_item", "pref_proximity_item", "pref_volume_item", "pref_connected_item",
            "pref_pressure_item", "pref_brightness_item", "pref_temperature_item", "pref_command_item",
            "pref_battery_item", "pref_battery_charging_item", "pref_battery_level_item"};

    private ItemsAsyncTaskLoader mLoader;
    private final LoaderManager.LoaderCallbacks<List<String>> mLoaderCallbacks = new LoaderManager.LoaderCallbacks<List<String>>() {
        @Override
        public Loader<List<String>> onCreateLoader(int i, Bundle bundle) {
            return mLoader;
        }

        @Override
        public void onLoadFinished(Loader<List<String>> loader, List<String> strings) {
            for (String key : ITEMS_PREFS) {
                final EditText editText = ((EditTextPreference) findPreference(key)).getEditText();

                if (editText instanceof AutoCompleteTextView) {
                    AutoCompleteTextView t = (AutoCompleteTextView) editText;
                    t.setAdapter(new ArrayAdapter(getActivity(), android.R.layout.simple_dropdown_item_1line, strings));
                }
            }
        }

        @Override
        public void onLoaderReset(Loader<List<String>> loader) {
            for (String key : ITEMS_PREFS) {
                final EditText editText = ((EditTextPreference) findPreference(key)).getEditText();

                if (editText instanceof AutoCompleteTextView) {
                    AutoCompleteTextView t = (AutoCompleteTextView) editText;
                    t.setAdapter(null);
                }
            }
        }
    };

    private boolean motionEnabled = false;
    private boolean proximityEnabled = false;
    private boolean pressureEnabled = false;
    private boolean brightnessEnabled = false;
    private boolean temperatureEnabled = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = this.getArguments();
        if (bundle != null) {
            motionEnabled = bundle.getBoolean("motion_enabled");
            proximityEnabled = bundle.getBoolean("proximity_enabled");
            pressureEnabled = bundle.getBoolean("pressure_enabled");
            brightnessEnabled = bundle.getBoolean("brightness_enabled");
            temperatureEnabled = bundle.getBoolean("temperature_enabled");
        }

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        mDPM = (DevicePolicyManager) getActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);
        mLoader = new ItemsAsyncTaskLoader(getActivity());

        // disable preferences if functionality is not available
        if (!cameraEnabled) {
            findPreference("pref_camera").setEnabled(false);
            findPreference("pref_camera").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_camera)));
        }
        if (!motionEnabled) {
            findPreference("pref_motion").setEnabled(false);
            findPreference("pref_motion").setSummary(getString(R.string.pref_motion) + getString(R.string.notAvailableOnDevice));
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

        onActivityResult(42, RESULT_OK, null);

        // add validation to the device admin
        CheckBoxPreference adminPreference = (CheckBoxPreference) findPreference("pref_device_admin");
        adminPreference.setOnPreferenceChangeListener(new AdminValidatingListener());

        // add validation to the openHAB url
        EditTextPreference urlPreference = (EditTextPreference) findPreference("pref_server_url");
        urlPreference.setOnPreferenceChangeListener(new URLValidatingListener());
        mLoader.setServerUrl(urlPreference.getText());

        // add validation to the items
        for (String key : ITEMS_PREFS) {
            final EditText editText = ((EditTextPreference) findPreference(key)).getEditText();
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(final Editable editable) {
                    final String itemName = editable.toString();

                    getActivity().runOnUiThread(() -> {
                        if (mLoader.isValid(itemName)) {
                            editText.setTextColor(Color.GREEN);
                        } else {
                            editText.setTextColor(Color.RED);
                        }
                    });
                }

                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }
            });

            // initial color
            editText.setText(editText.getText());
        }

        getLoaderManager().initLoader(1234, null, mLoaderCallbacks);
    }

    private class URLValidatingListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object o) {
            String text = (String) o;
            mLoader.setServerUrl(text);

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

    private class AdminValidatingListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object o) {
            boolean value = (Boolean) o;

            if (value && !mDPM.isAdminActive(AdminReceiver.COMP)) {
                installAsAdmin();
            } else if (!value && mDPM.isAdminActive(AdminReceiver.COMP)) {
                removeAsAdmin();
            }

            return false;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 42 && resultCode == RESULT_OK) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            boolean isActive = mDPM.isAdminActive(AdminReceiver.COMP);

            if (prefs.getBoolean("pref_device_admin", false) != isActive) {
                SharedPreferences.Editor editor1 = prefs.edit();
                editor1.putBoolean("pref_device_admin", isActive);
                editor1.putString("pref_app_version", BuildConfig.VERSION_NAME);
                editor1.apply();

                CheckBoxPreference adminPreference = (CheckBoxPreference) findPreference("pref_device_admin");
                adminPreference.setChecked(isActive);
            }
        }
    }

    private void removeAsAdmin() {
        mDPM.removeActiveAdmin(AdminReceiver.COMP);

        CheckBoxPreference adminPreference = (CheckBoxPreference) findPreference("pref_device_admin");
        adminPreference.setChecked(false);
    }

    private void installAsAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, AdminReceiver.COMP);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.deviceAdminDescription));
        startActivityForResult(intent, 42);
    }
}
