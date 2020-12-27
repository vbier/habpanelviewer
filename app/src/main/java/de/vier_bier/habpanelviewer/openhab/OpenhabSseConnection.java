package de.vier_bier.habpanelviewer.openhab;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OpenhabSseConnection extends SseConnection implements SseConnection.ISseDataListener {
    private static final String TAG = "HPV-O...SseConnection";

    private final List<String> mItemNames = new ArrayList<>();
    private String mCmdItemName;

    private final ArrayList<IStateUpdateListener> mListeners = new ArrayList<>();

    OpenhabSseConnection() {
        super.addListener(this);
    }

    void addItemValueListener(IStateUpdateListener l) {
        synchronized (mListeners) {
            if (!mListeners.contains(l)) {
                mListeners.add(l);
            }
        }
    }

    public void removeItemValueListener(IStateUpdateListener l) {
        synchronized (mListeners) {
            mListeners.remove(l);
        }
    }

    @Override
    public void data(String data) {
        if (data != null) {
            try {
                JSONObject jObject = new JSONObject(data);
                String type = jObject.getString("type");
                if ("ItemStateChangedEvent".equals(type) || "GroupItemStateChangedEvent".equals(type)) {
                    JSONObject payload = new JSONObject(jObject.getString("payload"));
                    String topic = jObject.getString("topic");
                    String name = topic.split("/")[2];
                    final String value = payload.getString("value");

                    synchronized (mListeners) {
                        for (IStateUpdateListener l : mListeners) {
                            l.itemUpdated(name, value);
                        }
                    }
                } else if ("ItemCommandEvent".equals(type)) {
                    JSONObject payload = new JSONObject(jObject.getString("payload"));
                    String topic = jObject.getString("topic");
                    String name = topic.split("/")[2];
                    final String value = payload.getString("value");

                    synchronized (mListeners) {
                        for (IStateUpdateListener l : mListeners) {
                            l.itemUpdated(name, value);
                        }
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing JSON", e);
            }
        }
    }

    void setItemNames(String cmdItemName, String... names) {
        synchronized (mItemNames) {
            mCmdItemName = cmdItemName;
            mItemNames.clear();
            Collections.addAll(mItemNames, names);
        }

        if (mStatus.isConnecting() || mStatus == Status.CONNECTED) {
            disconnect();
        }

        if (mStatus == Status.NOT_CONNECTED) {
            connect();
        }
    }

    @Override
    protected String buildUrl() {
        return mUrl + "/rest/events?topics=" + buildTopic();
    }

    private String buildTopic() {
        StringBuilder topic = new StringBuilder();
        synchronized (mItemNames) {
            for (String item : mItemNames) {
                if (topic.length() > 0) {
                    topic.append(",");
                }
                topic.append("openhab/items/").append(item).append("/statechanged");
            }
        }
        if (mCmdItemName != null && !"".equals(mCmdItemName.trim())) {
            if (topic.length() > 0) {
                topic.append(",");
            }
            topic.append("openhab/items/").append(mCmdItemName).append("/command");
        }

        if (topic.length() == 0) {
            topic.append("openhab/items/dummyItemThatDoesNotExist/statechanged");
        }


        Log.v(TAG, "new SSE topic: " + topic.toString());
        return topic.toString();
    }
}
