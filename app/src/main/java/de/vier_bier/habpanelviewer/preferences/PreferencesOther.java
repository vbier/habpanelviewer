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
import de.vier_bier.habpanelviewer.R;

import static android.app.Activity.RESULT_OK;

public class PreferencesOther extends PreferenceFragment {
    private DevicePolicyManager mDPM;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDPM = (DevicePolicyManager) getActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);

        // add validation to the device admin
        CheckBoxPreference adminPreference = (CheckBoxPreference) findPreference("pref_device_admin");
        adminPreference.setOnPreferenceChangeListener(new AdminValidatingListener());
    }

    @Override
    public void onStart() {
        super.onStart();

        onActivityResult(42, RESULT_OK, null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 42 && resultCode == RESULT_OK) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            boolean isActive = mDPM.isAdminActive(AdminReceiver.COMP);

            if (prefs.getBoolean("pref_device_admin", false) != isActive) {
                SharedPreferences.Editor editor1 = prefs.edit();
                editor1.putBoolean("pref_device_admin", isActive);
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
