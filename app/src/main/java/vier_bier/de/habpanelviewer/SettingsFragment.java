package vier_bier.de.habpanelviewer;

import android.os.Bundle;
import android.preference.PreferenceFragment;

/**
 * Created by volla on 07.09.17.
 */

public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }
}
