package de.vier_bier.habpanelviewer.command;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;

import de.vier_bier.habpanelviewer.command.log.CommandLog;
import de.vier_bier.habpanelviewer.command.log.CommandLogClient;
import de.vier_bier.habpanelviewer.openhab.IStateUpdateListener;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;

/**
 * Queue for commands sent from openHAB.
 */
public class CommandQueue extends HandlerThread implements IStateUpdateListener {
    private final ServerConnection mServerConnection;

    private Handler mUiHandler;
    private Handler mWorkerHandler;

    private final ArrayList<ICommandHandler> mHandlers = new ArrayList<>();
    private final CommandLog mCmdLog = new CommandLog();

    public CommandQueue(ServerConnection serverConnection) {
        super("CommandQueue");

        EventBus.getDefault().register(this);

        mServerConnection = serverConnection;
        mUiHandler = new Handler(Looper.getMainLooper());

        start();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(CommandLogClient client) {
        client.setCommandLog(mCmdLog);
    }

    public void addHandler(ICommandHandler h) {
        synchronized (mHandlers) {
            if (!mHandlers.contains(h)) {
                mHandlers.add(h);
            }
        }
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();

        mWorkerHandler = new Handler(getLooper(), msg -> {
            if (msg.what == 12) {
                Command cmd = (Command) msg.obj;

                synchronized (mHandlers) {
                    for (ICommandHandler mHandler : mHandlers) {
                        try {
                            if (mHandler.handleCommand(cmd)) {
                                break;
                            }
                        } catch (Throwable t) {
                            cmd.failed(t.getLocalizedMessage());
                            break;
                        }
                    }
                }
            }

            return true;
        });
    }

    @Override
    public void itemUpdated(String name, String value) {
        if (value != null && !value.isEmpty()) {
            Command cmd = new Command(value);

            mUiHandler.post(() -> mCmdLog.add(cmd));
            mWorkerHandler.obtainMessage(12, cmd).sendToTarget();
        }
    }

    public void updateFromPreferences(final SharedPreferences prefs) {
        String mCmdItemName = prefs.getString("pref_command_item", "");

        mUiHandler.post(() -> mCmdLog.setSize(prefs.getInt("pref_command_log_size", 100)));

        mServerConnection.subscribeCommandItems(this, mCmdItemName);
    }

    public void terminate() {
        EventBus.getDefault().unregister(this);
        quit();
    }
}
