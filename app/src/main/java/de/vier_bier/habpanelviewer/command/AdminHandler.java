package de.vier_bier.habpanelviewer.command;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;

import de.vier_bier.habpanelviewer.AdminReceiver;

/**
 * Handler for ADMIN_LOCK_SCREEN command.
 */
public class AdminHandler implements CommandHandler {
    private final DevicePolicyManager mDPM;

    public AdminHandler(Activity activity) {
        mDPM = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    @Override
    public boolean handleCommand(String cmd) {
        if ("ADMIN_LOCK_SCREEN".equals(cmd) && mDPM.isAdminActive(AdminReceiver.COMP)) {
            mDPM.lockNow();
            return true;
        }

        return false;
    }
}
