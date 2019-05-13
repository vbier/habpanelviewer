package de.vier_bier.habpanelviewer.preferences;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;

import de.vier_bier.habpanelviewer.AdminReceiver;
import de.vier_bier.habpanelviewer.Constants;
import de.vier_bier.habpanelviewer.R;

import static android.app.Activity.RESULT_OK;

public class PreferencesOther extends PreferenceFragment {
    private DevicePolicyManager mDPM;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_other);

        mDPM = (DevicePolicyManager) getActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);

        // add validation to the device admin
        CheckBoxPreference adminPreference = (CheckBoxPreference) findPreference(Constants.PREF_DEVICE_ADMIN);
        adminPreference.setOnPreferenceChangeListener(new AdminValidatingListener());
    }

    @Override
    public void onStart() {
        super.onStart();

        onActivityResult(Constants.REQUEST_DEVICE_ADMIN, RESULT_OK, null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constants.REQUEST_DEVICE_ADMIN && resultCode == RESULT_OK) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            boolean isActive = mDPM.isAdminActive(AdminReceiver.COMP);

            if (prefs.getBoolean(Constants.PREF_DEVICE_ADMIN, false) != isActive) {
                SharedPreferences.Editor editor1 = prefs.edit();
                editor1.putBoolean(Constants.PREF_DEVICE_ADMIN, isActive);
                editor1.apply();

                CheckBoxPreference adminPreference = (CheckBoxPreference) findPreference(Constants.PREF_DEVICE_ADMIN);
                adminPreference.setChecked(isActive);
            }
        }
    }

    private void removeAsAdmin() {
        mDPM.removeActiveAdmin(AdminReceiver.COMP);

        CheckBoxPreference adminPreference = (CheckBoxPreference) findPreference(Constants.PREF_DEVICE_ADMIN);
        adminPreference.setChecked(false);
    }

    private void installAsAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, AdminReceiver.COMP);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.deviceAdminDescription));
        startActivityForResult(intent, Constants.REQUEST_DEVICE_ADMIN);
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
}
