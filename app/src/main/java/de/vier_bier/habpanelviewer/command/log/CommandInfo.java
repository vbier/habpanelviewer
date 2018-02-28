package de.vier_bier.habpanelviewer.command.log;

/**
 * Info about an received command.
 */
public class CommandInfo {
    private final long mTime = System.currentTimeMillis();
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

    boolean isHandled() {
        return mHandled;
    }

    public long getTime() {
        return mTime;
    }

    Throwable getThrowable() {
        return mException;
    }
}
