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

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.UiUtil;

public class PreferencesBrowser extends PreferenceFragment {
    public static final int MY_PERMISSIONS_REQUEST_WEBRTC = 125;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_browser);

        // add validation to the allow webrtc
        CheckBoxPreference allowPreference = (CheckBoxPreference) findPreference("pref_allow_webrtc");
        allowPreference.setOnPreferenceChangeListener(new WebRtcValidatingListener());
    }

    @Override
    public void onStart() {
        super.onStart();

        if (needsPermissions()) {
            CheckBoxPreference allowPreference = (CheckBoxPreference) findPreference("pref_allow_webrtc");
            allowPreference.setChecked(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST_WEBRTC) {
            setAllowWebRtcPref(grantResults.length == 2
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED);
        }
    }

    private void setAllowWebRtcPref(boolean allowWebRtc) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if (prefs.getBoolean("pref_allow_webrtc", false) != allowWebRtc) {
            SharedPreferences.Editor editor1 = prefs.edit();
            editor1.putBoolean("pref_allow_webrtc", allowWebRtc);
            editor1.apply();

            CheckBoxPreference allowPreference = (CheckBoxPreference) findPreference("pref_allow_webrtc");
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
                MY_PERMISSIONS_REQUEST_WEBRTC);
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
                if (prefs.getBoolean("pref_motion_detection_enabled", false)
                        || prefs.getBoolean("pref_motion_detection_preview", false)) {
                    UiUtil.showCancelDialog(getActivity(), null,
                            "Enabling WebRTC will disable other camera related features. Continue?",
                            (dialogInterface, i) -> {
                                final SharedPreferences prefs1 = PreferenceManager.getDefaultSharedPreferences(getActivity());
                                SharedPreferences.Editor editor1 = prefs1.edit();
                                editor1.putBoolean("pref_motion_detection_enabled", false);
                                editor1.putBoolean("pref_motion_detection_preview", false);
                                editor1.apply();

                                onPreferenceChange(value, true, true);
                            }, null);

                    return false;
                }

                if (checkAccel && !prefs.getBoolean("pref_hardware_accelerated", false)) {
                    UiUtil.showButtonDialog(getActivity(), null,
                            "HW Acceleration is disabled, WebRTC might not be able to show video. Do you want to enable HW acceleration?",
                            R.string.yes,
                            (dialogInterface, i) -> {
                                final SharedPreferences prefs1 = PreferenceManager.getDefaultSharedPreferences(getActivity());
                                SharedPreferences.Editor editor1 = prefs1.edit();
                                editor1.putBoolean("pref_hardware_accelerated", true);
                                editor1.apply();

                                CheckBoxPreference allowPreference = (CheckBoxPreference) findPreference("pref_hardware_accelerated");
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
