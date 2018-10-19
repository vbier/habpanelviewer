package de.vier_bier.habpanelviewer.preferences;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.Loader;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import com.jakewharton.processphoenix.ProcessPhoenix;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLException;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.UiUtil;
import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;

public class PreferenceFragment extends android.preference.PreferenceFragment implements Preference.OnPreferenceClickListener {
    private static final String TAG_ID = "NESTED_KEY";

    private PreferenceCallback mCallback;

    private ItemsAsyncTaskLoader mLoader;
    private final LoaderManager.LoaderCallbacks<List<String>> mLoaderCallbacks = new LoaderManager.LoaderCallbacks<List<String>>() {
        @Override
        public Loader<List<String>> onCreateLoader(int i, Bundle bundle) {
            return mLoader;
        }

        @Override
        public void onLoadFinished(Loader<List<String>> loader, List<String> strings) {
            List<Preference> list = getPreferenceList(getPreferenceScreen(), new ArrayList<>());
            for (Preference p : list) {
                if (p.getKey().endsWith("_item") && p instanceof EditTextPreference) {
                    final EditText editText = ((EditTextPreference) p).getEditText();

                    if (editText instanceof AutoCompleteTextView) {
                        AutoCompleteTextView t = (AutoCompleteTextView) editText;
                        t.setAdapter(new ArrayAdapter(getActivity(), android.R.layout.simple_dropdown_item_1line, strings));
                    }
                }
            }
        }

        @Override
        public void onLoaderReset(Loader<List<String>> loader) {
            List<Preference> list = getPreferenceList(getPreferenceScreen(), new ArrayList<>());
            for (Preference p : list) {
                if (p.getKey().endsWith("_item") && p instanceof EditTextPreference) {
                    final EditText editText = ((EditTextPreference) p).getEditText();

                    if (editText instanceof AutoCompleteTextView) {
                        AutoCompleteTextView t = (AutoCompleteTextView) editText;
                        t.setAdapter(null);
                    }
                }
            }
        }
    };

    public static PreferenceFragment newInstance(String id, Bundle addArgs) {
        if ("nested_pref_other".equals(id)) {
            return new PreferencesOther();
        }

        PreferenceFragment fragment = new PreferenceFragment();

        Bundle args = new Bundle();
        args.putAll(addArgs);
        args.putString(TAG_ID, id);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        if (activity instanceof PreferenceCallback) {
            mCallback = (PreferenceCallback) activity;
        } else {
            throw new IllegalStateException("Owner must implement Callback interface");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String id = getArguments().getString(TAG_ID);
        if (id == null) {
            addPreferencesFromResource(R.xml.preferences_main);
        } else if ("nested_pref_browser".equals(id)) {
            addPreferencesFromResource(R.xml.preferences_browser);
        } else if ("nested_pref_camera".equals(id)) {
            addPreferencesFromResource(R.xml.preferences_camera);
        } else if ("nested_pref_connection".equals(id)) {
            addPreferencesFromResource(R.xml.preferences_connection);
            addConnectionValidation();
        } else if ("nested_pref_reporting".equals(id)) {
            addPreferencesFromResource(R.xml.preferences_reporting);
            addReportingValidation();
        } else if ("nested_pref_restart".equals(id)) {
            addPreferencesFromResource(R.xml.preferences_restart);
        } else if ("nested_pref_ui".equals(id)) {
            addPreferencesFromResource(R.xml.preferences_ui);
            addUiValidation();
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        // register as click listener for all nested prefs
        List<Preference> list = getPreferenceList(getPreferenceScreen(), new ArrayList<>());
        for (Preference p : list) {
            if (p.getKey().startsWith("nested_")) {
                p.setOnPreferenceClickListener(this);
            } else if (p.getKey().endsWith("_item") && p instanceof EditTextPreference) {
                addItemValidation((EditTextPreference) p);
            }
        }

        if (mLoader != null) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            mLoader.setServerUrl(prefs.getString("pref_server_url", ""));
            getLoaderManager().initLoader(1234, null, mLoaderCallbacks);
        }
    }

    private void addReportingValidation() {
        EditTextPreference connectedIntervalPreference =
                (EditTextPreference) findPreference("pref_connected_interval");
        connectedIntervalPreference.setOnPreferenceChangeListener(new NumberValidatingListener(0, 6000));

        boolean cameraEnabled = false;
        boolean motionEnabled = false;
        boolean proximityEnabled = false;
        boolean pressureEnabled = false;
        boolean brightnessEnabled = false;
        boolean temperatureEnabled = false;

        Bundle bundle = this.getArguments();
        if (bundle != null) {
            cameraEnabled = bundle.getBoolean("camera_enabled");
            motionEnabled = bundle.getBoolean("motion_enabled");
            proximityEnabled = bundle.getBoolean("proximity_enabled");
            pressureEnabled = bundle.getBoolean("pressure_enabled");
            brightnessEnabled = bundle.getBoolean("brightness_enabled");
            temperatureEnabled = bundle.getBoolean("temperature_enabled");
        }

        // disable preferences if functionality is not available
        if (!cameraEnabled) {
            findPreference("pref_camera").setEnabled(false);
            findPreference("pref_camera").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_camera)));
        }
        if (!motionEnabled) {
            findPreference("pref_motion").setEnabled(false);
            findPreference("pref_motion").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_motion)));
        }
        if (!proximityEnabled) {
            findPreference("pref_proximity").setEnabled(false);
            findPreference("pref_proximity").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_proximity)));
        }
        if (!pressureEnabled) {
            findPreference("pref_pressure").setEnabled(false);
            findPreference("pref_pressure").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_pressure)));
        }
        if (!brightnessEnabled) {
            findPreference("pref_brightness").setEnabled(false);
            findPreference("pref_brightness").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_brightness)));
        }
        if (!temperatureEnabled) {
            findPreference("pref_temperature").setEnabled(false);
            findPreference("pref_temperature").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_temperature)));
        }
    }

    protected void addItemValidation(EditTextPreference p) {
        if (mLoader == null) {
            mLoader = new ItemsAsyncTaskLoader(getActivity());
        }

        final EditText editText = p.getEditText();
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

    private void addUiValidation() {
        ListPreference themePreference = (ListPreference) findPreference("pref_theme");
        themePreference.setOnPreferenceChangeListener((preference, o) -> {
            if (getActivity() != null && !getActivity().isFinishing()) {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

                if (UiUtil.themeChanged(prefs, getActivity())) {
                    UiUtil.showSnackBar(getActivity().findViewById(R.id.myCoordinatorLayout),
                            R.string.themeChangedRestartRequired, R.string.action_restart,
                            view -> {
                                getActivity().finish();
                                ProcessPhoenix.triggerRebirth(getActivity().getApplication());
                            });
                }
            }

            return true;
        });
    }

    private void addConnectionValidation() {
        if (mLoader == null) {
            mLoader = new ItemsAsyncTaskLoader(getActivity());
        }

        // add validation to the openHAB url
        EditTextPreference urlPreference = (EditTextPreference) findPreference("pref_server_url");
        urlPreference.setOnPreferenceChangeListener(new URLValidatingListener());
        mLoader.setServerUrl(urlPreference.getText());
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        // here you should use the same keys as you used in the xml-file
        if (preference.getKey().startsWith("nested_")) {
            mCallback.onNestedPreferenceSelected(preference.getKey());
        }

        return false;
    }

    protected List<Preference> getPreferenceList(Preference p, ArrayList<Preference> list) {
        if( p instanceof PreferenceCategory || p instanceof PreferenceScreen) {
            PreferenceGroup pGroup = (PreferenceGroup) p;
            int pCount = pGroup.getPreferenceCount();
            for(int i = 0; i < pCount; i++) {
                getPreferenceList(pGroup.getPreference(i), list); // recursive call
            }
        } else {
            list.add(p);
        }
        return list;
    }


    private class URLValidatingListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object o) {
            String text = (String) o;
            mLoader.setServerUrl(text);

            if (text == null || text.isEmpty()) {
                return true;
            }
            AsyncTask<String, Void, Void> validator =
                    new ValidateHabPanelTask(PreferenceFragment.this.getActivity(), preference.getTitle());
            validator.execute(text);

            return true;
        }

    }

    private static final class ValidateHabPanelTask extends AsyncTask<String, Void, Void> {
        private final WeakReference<Activity> activity;
        private final CharSequence preferenceName;

        ValidateHabPanelTask(Activity act, CharSequence prefName) {
            activity = new WeakReference<>(act);
            preferenceName = prefName;
        }

        @Override
        protected Void doInBackground(String... urls) {
            String dialogTitle = null;
            String dialogText = null;
            try {
                String serverURL = urls[0] + "/rest/services";
                HttpURLConnection urlConnection = ConnectionUtil.getInstance().createUrlConnection(serverURL);
                urlConnection.connect();

                if (urlConnection.getResponseCode() != 200) {
                    dialogText = getResString(R.string.notValidOpenHabUrl);
                } else {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader((urlConnection.getInputStream())))) {
                        String line;
                        boolean habPanelFound = false;
                        while (!habPanelFound && (line = br.readLine()) != null) {
                            habPanelFound = line.contains("\"org.openhab.habpanel\"");
                        }
                        if (!habPanelFound) {
                            dialogText = getResString(R.string.habPanelNotAvailable);
                        }
                    }
                }

                urlConnection.disconnect();
            } catch (MalformedURLException e) {
                dialogText = urls[0] + getResString(R.string.notValidUrl);
            } catch (SSLException e) {
                dialogTitle = getResString(R.string.certInvalid);
                dialogText = getResString(R.string.couldNotConnect) + " " + urls[0] + ".\n" +
                        getResString(R.string.acceptCertWhenOurOfPrefs);
            } catch (IOException | GeneralSecurityException e) {
                dialogText = getResString(R.string.couldNotConnect) + " " + urls[0];
            }

            Activity a = activity.get();
            if (dialogText != null && a != null && !a.isFinishing()) {
                if (dialogTitle == null) {
                    dialogTitle = preferenceName + " " + getResString(R.string.invalid);
                }

                UiUtil.showDialog(a, dialogTitle, dialogText);
            }

            return null;
        }

        private String getResString(int resId) {
            Activity a = activity.get();
            if (a != null) {
                return a.getResources().getString(resId);
            }

            return "";
        }
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
                UiUtil.showDialog(getActivity(), preference.getTitle() + " "
                                + PreferenceFragment.this.getResources().getString(R.string.invalid),
                        getString(R.string.noValidIntInRange, minVal, maxVal));
                return false;
            }

            return true;
        }
    }
}
