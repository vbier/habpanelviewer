package de.vier_bier.habpanelviewer.command.log;

import java.util.ArrayList;

/**
 * Created by volla on 11.01.18.
 */

public class CommandLog {
    private final ArrayList<CommandInfo> mCommands = new ArrayList<>();
    private int mSize;

    public void setSize(int size) {
        mSize = size;
        trim();
    }

    public void add(CommandInfo commandInfo) {
        synchronized (mCommands) {
            mCommands.add(commandInfo);
        }
    }

    public void trim() {
        synchronized (mCommands) {
            while (mCommands.size() > mSize) {
                mCommands.remove(0);
            }
        }
    }

    public ArrayList<CommandInfo> getCommands() {
        return mCommands;
    }
}
