package de.vier_bier.habpanelviewer.command;

import android.content.SharedPreferences;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;

import de.vier_bier.habpanelviewer.command.log.CommandInfo;
import de.vier_bier.habpanelviewer.command.log.CommandLog;
import de.vier_bier.habpanelviewer.command.log.CommandLogClient;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.openhab.StateUpdateListener;

/**
 * Queue for commands sent from openHAB.
 */
public class CommandQueue implements StateUpdateListener {
    private final ArrayList<CommandHandler> mHandlers = new ArrayList<>();
    private ServerConnection mServerConnection;
    private CommandLog mCmdLog = new CommandLog();

    private String mCmdItemName;

    public CommandQueue(ServerConnection serverConnection) {
        EventBus.getDefault().register(this);

        mServerConnection = serverConnection;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(CommandLogClient client) {
        client.setCommandLog(mCmdLog);
    }

    public void addHandler(CommandHandler h) {
        synchronized (mHandlers) {
            if (!mHandlers.contains(h)) {
                mHandlers.add(h);
            }
        }
    }

    @Override
    public void itemUpdated(String name, String value) {
        if (value != null && !value.isEmpty()) {
            try {
                synchronized (mHandlers) {
                    for (CommandHandler mHandler : mHandlers) {
                        try {
                            if (mHandler.handleCommand(value)) {
                                mCmdLog.add(new CommandInfo(value, true));
                                return;
                            }
                        } catch (Throwable t) {
                            Log.e("Habpanelview", "unhandled exception", t);
                            mCmdLog.add(new CommandInfo(value, true, t));
                            return;
                        }
                    }
                }

                Log.w("Habpanelview", "received unhandled command: " + value);
                mCmdLog.add(new CommandInfo(value, false));
            } finally {
                mServerConnection.updateState(name, "");
            }
        }
    }

    public void updateFromPreferences(SharedPreferences prefs) {
        mCmdItemName = prefs.getString("pref_command_item", "");

        mCmdLog.setSize(prefs.getInt("pref_command_log_size", 100));

        if (mServerConnection.subscribeItems(this, false, mCmdItemName)) {
            mServerConnection.updateState(mCmdItemName, "");
        }
    }
}
