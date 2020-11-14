package de.vier_bier.habpanelviewer.preferences;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.legacy.app.FragmentCompat;

import org.greenrobot.eventbus.EventBus;

import de.vier_bier.habpanelviewer.Constants;
import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.UiUtil;

public class PreferencesBrowser extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_browser);

        // add validation to the allow webrtc
        CheckBoxPreference allowPreference = (CheckBoxPreference) findPreference(Constants.PREF_ALLOW_WEBRTC);
        allowPreference.setOnPreferenceChangeListener(new WebRtcValidatingListener());

        // restart on change of desktop mode
        CheckBoxPreference desktopPreference = (CheckBoxPreference) findPreference(Constants.PREF_DESKTOP_MODE);
        desktopPreference.setOnPreferenceChangeListener((preference, o) -> {
            if (getActivity() != null && !getActivity().isFinishing()) {
                UiUtil.showSnackBar(getActivity().findViewById(R.id.myCoordinatorLayout),
                        R.string.themeChangedRestartRequired, R.string.action_restart,
                        view -> EventBus.getDefault().post(new Constants.Restart()));
            }

            return true;
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        if (needsPermissions()) {
            CheckBoxPreference allowPreference = (CheckBoxPreference) findPreference(Constants.PREF_ALLOW_WEBRTC);
            allowPreference.setChecked(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == Constants.REQUEST_WEBRTC) {
            setAllowWebRtcPref(grantResults.length == 2
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED);
        }
    }

    private void setAllowWebRtcPref(boolean allowWebRtc) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if (prefs.getBoolean(Constants.PREF_ALLOW_WEBRTC, false) != allowWebRtc) {
            SharedPreferences.Editor editor1 = prefs.edit();
            editor1.putBoolean(Constants.PREF_ALLOW_WEBRTC, allowWebRtc);
            editor1.apply();

            CheckBoxPreference allowPreference = (CheckBoxPreference) findPreference(Constants.PREF_ALLOW_WEBRTC);
            allowPreference.setChecked(allowWebRtc);
        }
    }

    private boolean needsPermissions() {
        return ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;
    }

    private void requestMissingPermissions() {
        FragmentCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA},
                Constants.REQUEST_WEBRTC);
    }

    private class WebRtcValidatingListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object o) {
            return onPreferenceChange(o, false, true);
        }

        private boolean onPreferenceChange(Object o, boolean setValues, boolean checkAccel) {
            boolean value = (Boolean) o;

            if (value) {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                if (prefs.getBoolean(Constants.PREF_MOTION_DETECTION_ENABLED, false)
                        || prefs.getBoolean(Constants.PREF_MOTION_DETECTION_PREVIEW, false)) {
                    UiUtil.showCancelDialog(getActivity(), null,
                            "Enabling WebRTC will disable other camera related features. Continue?",
                            (dialogInterface, i) -> {
                                final SharedPreferences prefs1 = PreferenceManager.getDefaultSharedPreferences(getActivity());
                                SharedPreferences.Editor editor1 = prefs1.edit();
                                editor1.putBoolean(Constants.PREF_MOTION_DETECTION_ENABLED, false);
                                editor1.putBoolean(Constants.PREF_MOTION_DETECTION_PREVIEW, false);
                                editor1.apply();

                                onPreferenceChange(true, true, true);
                            }, null);

                    return false;
                }

                if (checkAccel && !prefs.getBoolean(Constants.PREF_HW_ACCELERATED, false)) {
                    UiUtil.showButtonDialog(getActivity(), null,
                            "HW Acceleration is disabled, WebRTC might not be able to show video. Do you want to enable HW acceleration?",
                            R.string.yes,
                            (dialogInterface, i) -> {
                                final SharedPreferences prefs1 = PreferenceManager.getDefaultSharedPreferences(getActivity());
                                SharedPreferences.Editor editor1 = prefs1.edit();
                                editor1.putBoolean(Constants.PREF_HW_ACCELERATED, true);
                                editor1.apply();

                                CheckBoxPreference allowPreference = (CheckBoxPreference) findPreference(Constants.PREF_HW_ACCELERATED);
                                allowPreference.setChecked(true);

                                onPreferenceChange(true, true, false);
                            },
                            R.string.no,
                            (dialogInterface, i) -> onPreferenceChange(true, true, false));

                    return false;
                }

                if (needsPermissions()) {
                    requestMissingPermissions();
                    return false;
                }
            }

            if (setValues) {
                setAllowWebRtcPref(value);
            }
            return true;
        }
    }
}
