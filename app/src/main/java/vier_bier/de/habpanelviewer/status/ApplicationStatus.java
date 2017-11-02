package vier_bier.de.habpanelviewer.status;

import android.app.Activity;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Class holding StatusItems for visualization in the Status Screen.
 */
public class ApplicationStatus {
    private final Activity mContext;
    private final ArrayList<StatusItem> mValues = new ArrayList<>();
    private final HashMap<String, StatusItem> mIndices = new HashMap<>();
    private BaseAdapter mAdapter;

    public ApplicationStatus(Activity context) {
        mContext = context;
    }

    public void set(String key, String value) {
        StatusItem item = mIndices.get(key);
        if (item == null) {
            item = new StatusItem(key, value);
            mValues.add(item);
            mIndices.put(key, item);
        } else {
            item.setValue(value);
        }

        synchronized (mAdapter) {
            if (mAdapter != null) {
                final BaseAdapter adapter = mAdapter;
                mContext.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        }
    }

    public int getItemCount() {
        return mValues.size();
    }

    public StatusItem getItem(int i) {
        return mValues.get(i);
    }

    public void registerAdapter(BaseAdapter adapter) {
        synchronized (mAdapter) {
            mAdapter = adapter;
        }
    }
}
