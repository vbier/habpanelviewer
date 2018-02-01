package de.vier_bier.habpanelviewer.command;

import android.content.Intent;

import com.jakewharton.processphoenix.ProcessPhoenix;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.vier_bier.habpanelviewer.MainActivity;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;

/**
 * Handler for RESTART, UPDATE_ITEMS commands.
 */
public class InternalCommandHandler implements CommandHandler {
    private final Pattern START_PATTERN = Pattern.compile("START_APP (.+)");

    private final MainActivity mActivity;
    private final ServerConnection mConnection;

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
            Matcher m = START_PATTERN.matcher(cmd);

            if (!m.matches()) {
                return false;
            }

            final String app = m.group(1);
            Intent launchIntent = mActivity.getPackageManager().getLaunchIntentForPackage(app);

            if (launchIntent != null) {
                mActivity.startActivity(launchIntent);
            } else {
                throw new IllegalArgumentException("Could not find app for package " + app);
            }
        }

        return true;
    }
}
