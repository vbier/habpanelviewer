package de.vier_bier.habpanelviewer.preferences;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLException;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.UiUtil;
import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;

public class PreferencesConnection extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_connection);

        if (mLoader == null) {
            mLoader = new ItemsAsyncTaskLoader(getActivity());
        }

        // add validation to the openHAB url
        EditTextPreference urlPreference = (EditTextPreference) findPreference("pref_server_url");
        urlPreference.setOnPreferenceChangeListener(new URLValidatingListener());
        mLoader.setServerUrl(urlPreference.getText());
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
                    new ValidateHabPanelTask(getActivity(), preference.getTitle());
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
}
