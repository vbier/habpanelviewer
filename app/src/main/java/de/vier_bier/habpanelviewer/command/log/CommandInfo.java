package de.vier_bier.habpanelviewer.command.log;

/**
 * Created by volla on 11.01.18.
 */

public class CommandInfo {
    private long mTime = System.currentTimeMillis();
    private final String mCommand;
    private final boolean mHandled;
    private final Throwable mException;

    public CommandInfo(String command, boolean handled) {
        this(command, handled, null);
    }

    public CommandInfo(String command, boolean handled, Throwable exception) {
        mCommand = command;
        mHandled = handled;
        mException = exception;
    }

    public String getCommand() {
        return mCommand;
    }

    public boolean isHandled() {
        return mHandled;
    }

    public long getTime() {
        return mTime;
    }

    public Throwable getThrowable() {
        return mException;
    }
}
