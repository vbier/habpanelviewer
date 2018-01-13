package de.vier_bier.habpanelviewer.command;

import com.jakewharton.processphoenix.ProcessPhoenix;

import de.vier_bier.habpanelviewer.MainActivity;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;

/**
 * Handler for RESTART, UPDATE_ITEMS commands.
 */
public class InternalCommandHandler implements CommandHandler {
    MainActivity mActivity;
    ServerConnection mConnection;

    public InternalCommandHandler(MainActivity mainActivity, ServerConnection connection) {
        mActivity = mainActivity;
        mConnection = connection;
    }

    @Override
    public boolean handleCommand(String cmd) {
        if ("RESTART".equals(cmd)) {
            mActivity.destroy();
            ProcessPhoenix.triggerRebirth(mActivity);
        } else if ("UPDATE_ITEMS".equals(cmd)) {
            mConnection.sendCurrentValues();
        } else {
            return false;
        }

        return true;
    }
}
