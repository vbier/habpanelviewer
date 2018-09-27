package de.vier_bier.habpanelviewer.reporting;

import android.content.Intent;
import android.content.SharedPreferences;

public interface IDeviceMonitor {
    void terminate();
    void updateFromPreferences(SharedPreferences prefs);
    void disablePreferences(Intent intent);
}
