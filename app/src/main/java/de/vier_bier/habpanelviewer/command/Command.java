package de.vier_bier.habpanelviewer.command;

import android.graphics.Color;

/**
 * Info about an received command.
 */
public class Command {
    private final long mTime = System.currentTimeMillis();
    private final String mCommand;

    private CommandStatus mStatus;
    private String mDetails;

    private boolean mShowDetails;

    public Command(String command, String errorMessage) {
        this(command);
        failed(errorMessage);
    }

    public Command(String command) {
        mCommand = command;
        mStatus = CommandStatus.UNHANDLED;
    }

    public String getCommand() {
        return mCommand;
    }

    public long getTime() {
        return mTime;
    }

    public void failed(String errorMessage) {
        mDetails = errorMessage;
        mStatus = CommandStatus.FAILED;
    }

    public void start() {
        if (mStatus == CommandStatus.UNHANDLED) {
            setStatus(CommandStatus.EXECUTING);
        }
    }

    public void progress(String message) {
        if (mStatus == CommandStatus.EXECUTING) {
            if (mDetails == null) {
                mDetails = message;
            } else {
                mDetails = mDetails + "\n" + message;
            }
        }
    }

    public void finished() {
        if (mStatus == CommandStatus.EXECUTING) {
            setStatus(CommandStatus.OK);
        }
    }

    private void setStatus(CommandStatus status) {
        mStatus = status;
    }

    public String getDetails() {
        return mDetails;
    }

    public CommandStatus getStatus() {
        return mStatus;
    }

    public void toggleShowDetails() {
        mShowDetails = !mShowDetails;
    }

    public boolean hasVisibleDetails() {
        return mShowDetails && mDetails != null;
    }

    public enum CommandStatus {
        UNHANDLED {
            @Override
            public int getColor() {
                return Color.YELLOW;
            }
        }, EXECUTING {
            @Override
            public int getColor() {
                return Color.GRAY;
            }
        }, FAILED {
            @Override
            public int getColor() {
                return Color.RED;
            }
        }, OK {
            @Override
            public int getColor() {
                return Color.GREEN;
            }
        };

        public abstract int getColor();
    }
}
