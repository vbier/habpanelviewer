package de.vier_bier.habpanelviewer.preferences;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.Loader;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.EditTextPreference;
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

import java.util.ArrayList;
import java.util.List;

import de.vier_bier.habpanelviewer.R;

public class PreferenceFragment extends android.preference.PreferenceFragment implements Preference.OnPreferenceClickListener {
    private static final String TAG_ID = "NESTED_KEY";

    private PreferenceCallback mCallback;

    ItemsAsyncTaskLoader mLoader;
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
        } else if ("nested_pref_connected".equals(id)) {
            return new PreferencesConnected();
        } else if ("nested_pref_connection".equals(id)) {
            return new PreferencesConnection();
        } else if ("nested_pref_reporting".equals(id)) {
            PreferenceFragment fragment =  new PreferencesReporting();
            fragment.setArguments(addArgs);
            return fragment;
        } else if ("nested_pref_ui".equals(id)) {
            return new PreferencesUi();
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

        if (getArguments() != null) {
            String id = getArguments().getString(TAG_ID);
            if ("nested_pref_battery".equals(id)) {
                addPreferencesFromResource(R.xml.preferences_battery);
            } else if ("nested_pref_brightness".equals(id)) {
                addPreferencesFromResource(R.xml.preferences_brightness);
            } else if ("nested_pref_browser".equals(id)) {
                addPreferencesFromResource(R.xml.preferences_browser);
            } else if ("nested_pref_camera".equals(id)) {
                addPreferencesFromResource(R.xml.preferences_camera);
            } else if ("nested_pref_motion".equals(id)) {
                addPreferencesFromResource(R.xml.preferences_motion);
            } else if ("nested_pref_pressure".equals(id)) {
                addPreferencesFromResource(R.xml.preferences_pressure);
            } else if ("nested_pref_proximity".equals(id)) {
                addPreferencesFromResource(R.xml.preferences_proximity);
            } else if ("nested_pref_restart".equals(id)) {
                addPreferencesFromResource(R.xml.preferences_restart);
            } else if ("nested_pref_screen".equals(id)) {
                addPreferencesFromResource(R.xml.preferences_screen);
            } else if ("nested_pref_temperature".equals(id)) {
                addPreferencesFromResource(R.xml.preferences_temperature);
            } else if ("nested_pref_usage".equals(id)) {
                addPreferencesFromResource(R.xml.preferences_usage);
            } else if ("nested_pref_volume".equals(id)) {
                addPreferencesFromResource(R.xml.preferences_volume);
            }
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

    private void addItemValidation(EditTextPreference p) {
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

    @Override
    public boolean onPreferenceClick(Preference preference) {
        // here you should use the same keys as you used in the xml-file
        if (preference.getKey().startsWith("nested_")) {
            mCallback.onNestedPreferenceSelected(preference.getKey());
        }

        return false;
    }

    private List<Preference> getPreferenceList(Preference p, ArrayList<Preference> list) {
        if( p instanceof PreferenceCategory || p instanceof PreferenceScreen) {
            PreferenceGroup pGroup = (PreferenceGroup) p;
            int pCount = pGroup.getPreferenceCount();
            for(int i = 0; i < pCount; i++) {
                getPreferenceList(pGroup.getPreference(i), list); // recursive call
            }
        } else if (p != null) {
            list.add(p);
        }
        return list;
    }
}
