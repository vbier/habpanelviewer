package de.vier_bier.habpanelviewer.command;

import com.jakewharton.processphoenix.ProcessPhoenix;

import de.vier_bier.habpanelviewer.MainActivity;

/**
 * Handler for RESTART commands.
 */
public class RestartHandler implements CommandHandler {
    MainActivity mActivity;

    public RestartHandler(MainActivity mainActivity) {
        mActivity = mainActivity;
    }

    @Override
    public boolean handleCommand(String cmd) {
        if ("RESTART".equals(cmd)) {
            mActivity.destroy();
            ProcessPhoenix.triggerRebirth(mActivity);

            return true;
        }

        return false;
    }
}
