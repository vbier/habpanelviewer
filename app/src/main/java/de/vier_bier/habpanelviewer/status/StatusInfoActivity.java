package de.vier_bier.habpanelviewer.status;

import android.app.Activity;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.ScreenControllingActivity;

/**
 * Status Information Activity showing interesting app or hardware details.
 */
public class StatusInfoActivity extends ScreenControllingActivity {
    private final ApplicationStatus status = new ApplicationStatus();
    private ScheduledExecutorService executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final StatusItemAdapter adapter = new StatusItemAdapter(this, status);

        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleWithFixedDelay(() -> {
            EventBus.getDefault().post(status);

            runOnUiThread(adapter::notifyDataSetChanged);
        }, 0, 1, TimeUnit.SECONDS);

        setContentView(R.layout.activity_statusinfo);

        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        final ListView listview = findViewById(R.id.info_listview);
        listview.setAdapter(adapter);
    }

    @Override
    public View getScreenOnView() {
        return findViewById(R.id.info_listview);
    }

    @Override
    protected void onDestroy() {
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
        super.onDestroy();
    }

    private static class StatusItemAdapter extends BaseAdapter {
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
            View rowView= inflater.inflate(R.layout.row_commandlog, parent, false);

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
