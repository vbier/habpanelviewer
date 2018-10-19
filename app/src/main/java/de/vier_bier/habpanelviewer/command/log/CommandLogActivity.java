package de.vier_bier.habpanelviewer.command.log;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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
    private MenuItem mClearItem;

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
            if (mClearItem != null) {
                mClearItem.setEnabled(adapter.getCount() > 0);
            }
        }), 0, 1, TimeUnit.SECONDS);

        setContentView(R.layout.command_log_main);

        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String theme = prefs.getString("pref_theme", "dark");

        if ("dark".equals(theme)) {
            myToolbar.setPopupTheme(R.style.ThemeOverlay_AppCompat_Dark);
        } else {
            myToolbar.setPopupTheme(R.style.ThemeOverlay_AppCompat_Light);
        }

        EventBus.getDefault().post(logClient);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.command_log_toolbar_menu, menu);

        mClearItem = menu.findItem(R.id.action_clear_log);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_clear_log) {
            adapter.clear();

            if (mClearItem != null) {
                mClearItem.setEnabled(false);
            }

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public View getScreenOnView() {
        return findViewById(R.id.command_log_listview);
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
            View rowView = inflater.inflate(R.layout.list_info_item, parent, false);

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

        void clear() {
            mCommands.clear();
            logChanged();
        }
    }
}
