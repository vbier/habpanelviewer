package de.vier_bier.habpanelviewer.command;

import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;

import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.openhab.StateUpdateListener;

/**
 * Queue for commands sent from openHAB.
 */
public class CommandQueue implements StateUpdateListener {
    private final ArrayList<CommandHandler> mHandlers = new ArrayList<>();
    private ServerConnection mServerConnection;

    private String cmdItemName;


    public CommandQueue(ServerConnection serverConnection) {
        mServerConnection = serverConnection;
    }

    public void addHandler(CommandHandler h) {
        synchronized (mHandlers) {
            if (!mHandlers.contains(h)) {
                mHandlers.add(h);
            }
        }
    }

    public void removeHandler(CommandHandler h) {
        synchronized (mHandlers) {
            mHandlers.remove(h);
        }
    }

    @Override
    public void itemUpdated(String name, String value) {
        if (value != null && !value.isEmpty()) {
            synchronized (mHandlers) {
                for (CommandHandler mHandler : mHandlers) {
                    try {
                        if (mHandler.handleCommand(value)) {
                            mServerConnection.updateState(name, "");
                            return;
                        }
                    } catch (Throwable t) {
                        Log.e("Habpanelview", "unhandled exception", t);
                    }
                }
            }

            Log.w("Habpanelview", "received unhandled command: " + value);
        }

    }

    public void updateFromPreferences(SharedPreferences prefs) {
        cmdItemName = prefs.getString("pref_command_item", "");

        mServerConnection.subscribeItems(this, false, cmdItemName);
    }
}
