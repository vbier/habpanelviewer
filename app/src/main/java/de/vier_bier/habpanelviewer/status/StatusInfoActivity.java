package de.vier_bier.habpanelviewer.status;

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

import de.vier_bier.habpanelviewer.R;

/**
 * Status Information Activity showing interesting app or hardware details.
 */
public class StatusInfoActivity extends Activity {
    private final ApplicationStatus status = new ApplicationStatus(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EventBus.getDefault().post(status);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean showOnLockScreen = prefs.getBoolean("pref_show_on_lock_screen", false);
        if (showOnLockScreen) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        setContentView(R.layout.info_main);

        final ListView listview = findViewById(R.id.info_listview);
        final StatusItemAdapter adapter = new StatusItemAdapter(this, status);

        listview.setAdapter(adapter);
        status.registerAdapter(adapter);
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        status.registerAdapter(null);
        super.onDestroy();
    }

    private class StatusItemAdapter extends BaseAdapter {
        private final Activity mContext;
        private final ApplicationStatus mStatus;

        StatusItemAdapter(Activity context, ApplicationStatus status) {
            mContext = context;
            mStatus = status;
        }

        @Override
        public int getCount() {
            return mStatus.getItemCount();
        }

        @Override
        public StatusItem getItem(int i) {
            return mStatus.getItem(i);
        }

        @Override
        public long getItemId(int i) {
            return mStatus.getItem(i).getId();
        }

        @NonNull
        @Override
        public View getView(int position, View view, @NonNull ViewGroup parent) {
            LayoutInflater inflater = mContext.getLayoutInflater();
            View rowView= inflater.inflate(R.layout.list_info_item, null, true);

            TextView txtTitle = rowView.findViewById(R.id.name);
            TextView txtValue = rowView.findViewById(R.id.value);
            txtTitle.setText(getItem(position).getName());
            txtValue.setText(getItem(position).getValue());

            return rowView;
        }

        @Override
        public CharSequence[] getAutofillOptions() {
            return new CharSequence[0];
        }
    }
}
