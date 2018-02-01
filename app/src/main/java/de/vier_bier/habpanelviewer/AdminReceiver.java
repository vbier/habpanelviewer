package de.vier_bier.habpanelviewer;

import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;

/**
 * Specific implementation of a DeviceAdminReceiver.
 */
public class AdminReceiver extends DeviceAdminReceiver {
    public static final ComponentName COMP =
            new ComponentName("de.vier_bier.habpanelviewer", "de.vier_bier.habpanelviewer.AdminReceiver");

    public AdminReceiver() {
    }
}
