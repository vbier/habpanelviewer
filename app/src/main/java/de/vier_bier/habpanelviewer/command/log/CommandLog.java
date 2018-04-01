package de.vier_bier.habpanelviewer.command.log;

import java.util.ArrayList;

import de.vier_bier.habpanelviewer.command.Command;

/**
 * Log for commands received from openHAB.
 */
public class CommandLog {
    private final ArrayList<Command> mCommands = new ArrayList<>();
    private final ArrayList<CommandLogListener> mListeners = new ArrayList<>();
    private int mSize;

    public void setSize(int size) {
        mSize = size;
        trim();
    }

    public void add(Command commandInfo) {
        synchronized (mCommands) {
            if (mCommands.isEmpty()) {
                mCommands.add(commandInfo);
            } else {
                mCommands.add(0, commandInfo);
            }
        }

        if (mCommands.size() > mSize) {
            mCommands.remove(mSize);
        }

        notifyListeners();
    }

    private void trim() {
        synchronized (mCommands) {
            while (mCommands.size() > mSize) {
                mCommands.remove(mSize);
            }
        }
        notifyListeners();
    }

    public ArrayList<Command> getCommands() {
        return mCommands;
    }

    private void notifyListeners() {
        synchronized (mListeners) {
            for (CommandLogListener l : mListeners) {
                l.logChanged();
            }
        }
    }

    void addListener(CommandLogListener listener) {
        synchronized (mListeners) {
            mListeners.add(listener);
        }
    }

    public interface CommandLogListener {
        void logChanged();
    }
}
