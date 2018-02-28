package de.vier_bier.habpanelviewer.status;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Class holding StatusItems for visualization in the Status Screen.
 */
public class ApplicationStatus {
    private final ArrayList<StatusItem> mValues = new ArrayList<>();
    private final HashMap<String, StatusItem> mIndices = new HashMap<>();

    public synchronized void set(String key, String value) {
        StatusItem item = mIndices.get(key);
        if (item == null) {
            item = new StatusItem(key, value);
            mValues.add(item);
            mIndices.put(key, item);
        } else {
            item.setValue(value);
        }
    }

    int getItemCount() {
        return mValues.size();
    }

    StatusItem getItem(int i) {
        return mValues.get(i);
    }
}
