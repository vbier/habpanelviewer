package de.vier_bier.habpanelviewer.command;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;

import de.vier_bier.habpanelviewer.AdminReceiver;

/**
 * Handler for ADMIN_LOCK_SCREEN command.
 */
public class AdminHandler implements ICommandHandler {
    private final DevicePolicyManager mDPM;

    public AdminHandler(Activity activity) {
        mDPM = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    @Override
    public boolean handleCommand(Command cmd) {
        final String cmdStr = cmd.getCommand();

        if ("ADMIN_LOCK_SCREEN".equals(cmdStr)) {
            if (mDPM.isAdminActive(AdminReceiver.COMP)) {
                cmd.start();
                mDPM.lockNow();
                cmd.finished();
            } else {
                cmd.failed("device admin privileges missing");
            }
            return true;
        }

        return false;
    }
}
