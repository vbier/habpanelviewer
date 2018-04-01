package de.vier_bier.habpanelviewer.command.log;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.ScreenControllingActivity;
import de.vier_bier.habpanelviewer.command.Command;

/**
 * Activity showing the command log.
 */
public class CommandLogActivity extends ScreenControllingActivity {
    private CommandInfoAdapter adapter;

    private final CommandLogClient logClient = this::installAdapter;

    private void installAdapter(CommandLog cmdLog) {
        final ListView listView = findViewById(R.id.command_log_listview);

        adapter = new CommandInfoAdapter(this, cmdLog.getCommands());
        cmdLog.addListener(adapter);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((arg0, arg1, position, arg3) -> {
            cmdLog.getCommands().get(position).toggleShowDetails();
            adapter.notifyDataSetChanged();
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleWithFixedDelay(() -> runOnUiThread(() -> {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }), 0, 1, TimeUnit.SECONDS);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean showOnLockScreen = prefs.getBoolean("pref_show_on_lock_screen", false);
        if (showOnLockScreen) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        setContentView(R.layout.command_log_main);

        final TextView logTitleView = findViewById(R.id.command_log_titleview);
        logTitleView.setText(R.string.command_log_title);

        EventBus.getDefault().post(logClient);
    }

    @Override
    public View getScreenOnView() {
        return findViewById(R.id.command_log_titleview);
    }

    private class CommandInfoAdapter extends BaseAdapter implements CommandLog.CommandLogListener {
        private final Activity mContext;
        private final ArrayList<Command> mCommands;
        private final DateFormat mFormat = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.MEDIUM);

        CommandInfoAdapter(Activity context, ArrayList<Command> commands) {
            mContext = context;
            mCommands = commands;
        }

        @Override
        public int getCount() {
            return mCommands.size();
        }

        @Override
        public Command getItem(int i) {
            return mCommands.get(i);
        }

        @Override
        public long getItemId(int i) {
            return mCommands.get(i).getTime();
        }

        @NonNull
        @Override
        public View getView(int position, View view, @NonNull ViewGroup parent) {
            LayoutInflater inflater = mContext.getLayoutInflater();
            View rowView = inflater.inflate(R.layout.list_info_item, null, true);

            TextView txtTitle = rowView.findViewById(R.id.name);
            TextView txtValue = rowView.findViewById(R.id.value);

            Command cmd = getItem(position);
            txtTitle.setText(cmd.getCommand());

            String txt = mFormat.format(new Date(cmd.getTime()));
            txtValue.setTextColor(cmd.getStatus().getColor());
            if (cmd.hasVisibleDetails()) {
                txt += "\n" + cmd.getDetails();
            }
            txtValue.setText(txt);

            return rowView;
        }

        @Override
        public CharSequence[] getAutofillOptions() {
            return new CharSequence[0];
        }

        @Override
        public void logChanged() {
            runOnUiThread(this::notifyDataSetChanged);
        }
    }
}
