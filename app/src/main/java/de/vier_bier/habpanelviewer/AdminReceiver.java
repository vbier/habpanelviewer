package de.vier_bier.habpanelviewer;

import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;

/**
 * Created by volla on 09.01.18.
 */
public class AdminReceiver extends DeviceAdminReceiver {
    public static final ComponentName COMP =
            new ComponentName("de.vier_bier.habpanelviewer", "de.vier_bier.habpanelviewer.AdminReceiver");

    public AdminReceiver() {
    }
}
