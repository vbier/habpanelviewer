package de.vier_bier.habpanelviewer.openhab;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import de.vier_bier.habpanelviewer.NetworkTracker;
import de.vier_bier.habpanelviewer.openhab.average.AveragePropagator;
import de.vier_bier.habpanelviewer.openhab.average.IStatePropagator;
import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;
import io.opensensors.sse.client.EventSource;
import io.opensensors.sse.client.EventSourceHandler;
import io.opensensors.sse.client.MessageEvent;

/**
 * Client for openHABs SSE service. Listens for item value changes.
 */
public class ServerConnection implements IStatePropagator, NetworkTracker.INetworkListener {
    public static final String ACTION_SET_WITH_TIMEOUT = "ACTION_SET_WITH_TIMEOUT";
    public static final String FLAG_ITEM_NAME = "itemName";
    public static final String FLAG_ITEM_STATE = "itemState";
    public static final String FLAG_ITEM_TIMEOUT_STATE = "itemTimeoutState";
    public static final String FLAG_TIMEOUT = "timeout";

    private static final String TAG = "HPV-ServerConnection";

    private String mServerURL;
    private EventSource mEventSource;
    private final AtomicBoolean mConnected = new AtomicBoolean(false);
    private RestClient mRestClient = new RestClient();

    private final ConnectionUtil.CertChangedListener mCertListener;
    private final ConnectivityManager mConnManager;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent i) {
            final String item = i.getStringExtra(FLAG_ITEM_NAME);
            final String state = i.getStringExtra(FLAG_ITEM_STATE);
            final String timeoutState = i.getStringExtra(FLAG_ITEM_TIMEOUT_STATE);
            final int timeout = i.getIntExtra(FLAG_TIMEOUT, 60);

            updateStateWithTimeout(item, state, timeoutState, timeout);
        }
    };

    private final HashMap<String, ArrayList<IStateUpdateListener>> mSubscriptions = new HashMap<>();
    private final HashMap<String, ArrayList<IStateUpdateListener>> mCmdSubscriptions = new HashMap<>();
    private final HashMap<String, String> mValues = new HashMap<>();

    private SSEHandler client;
    private final ArrayList<IConnectionListener> connectionListeners = new ArrayList<>();
    private final HashMap<String, String> lastUpdates = new HashMap<>();
    private final AveragePropagator averagePropagator = new AveragePropagator(this);

    public ServerConnection(Context context) {
        mConnManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        mCertListener = () -> {
            Log.d(TAG, "cert added, reconnecting to server...");

            // if we are not connected, try to connect. connection may have failed due to
            // certificate errors
            if (!isConnected()) {
                connect();
            }
        };

        ConnectionUtil.getInstance().addCertListener(mCertListener);

        IntentFilter f = new IntentFilter();
        f.addAction(ACTION_SET_WITH_TIMEOUT);

        LocalBroadcastManager.getInstance(context).registerReceiver(mReceiver, f);
    }

    public void addConnectionListener(IConnectionListener l) {
        synchronized (connectionListeners) {
            if (!connectionListeners.contains(l)) {
                connectionListeners.add(l);
            }
        }
    }

    public void subscribeCommandItem(IStateUpdateListener l, String name) {
        if (name != null && !"".equals(name)) {
            Log.d(TAG, "subscribing command item: " + name);
            subscribeItems(mCmdSubscriptions, l, false, name);
        }
    }

    public void subscribeItems(IStateUpdateListener l, String... names) {
        String[] namesArr = removeEmpty(names);

        if (namesArr.length > 0) {
            Log.d(TAG, "subscribing items: " + Arrays.toString(namesArr));
            subscribeItems(mSubscriptions, l, true, namesArr);
        }
    }

    private String[] removeEmpty(String[] names) {
        List<String> list = new ArrayList<>(Arrays.asList(names));
        Iterator<String> i = list.iterator();
        while (i.hasNext()) {
            String s = i.next();
            if (s == null || s.trim().isEmpty()) {
                i.remove();
            }
        }
        return list.toArray(new String[0]);
    }

    private void subscribeItems(HashMap<String, ArrayList<IStateUpdateListener>> subscriptions,
                                IStateUpdateListener l, boolean initialValue, String... names) {
        boolean itemsChanged = false;

        final HashSet<String> newItems = new HashSet<>();
        for (String name : names) {
            if (name != null && !name.isEmpty()) {
                newItems.add(name);
            }
        }

        synchronized (subscriptions) {
            for (String name : new HashSet<>(subscriptions.keySet())) {
                ArrayList<IStateUpdateListener> listeners = subscriptions.get(name);
                if (listeners != null && listeners.contains(l) && !newItems.contains(name)) {
                    listeners.remove(l);

                    if (listeners.isEmpty()) {
                        itemsChanged = true;
                        subscriptions.remove(name);
                        mValues.remove(name);
                    }
                }
            }

            for (String name : newItems) {
                ArrayList<IStateUpdateListener> listeners = subscriptions.get(name);
                if (listeners == null) {
                    itemsChanged = true;
                    listeners = new ArrayList<>();
                    subscriptions.put(name, listeners);
                } else if (mValues.containsKey(name) && initialValue) {
                    l.itemUpdated(name, mValues.get(name));
                }

                if (!listeners.contains(l)) {
                    listeners.add(l);
                }
            }
        }

        if (itemsChanged) {
            reconnect();
        }
    }

    public void updateFromPreferences(SharedPreferences prefs) {
        // in case server url has changed reconnect
        if (mServerURL == null || !mServerURL.equalsIgnoreCase(prefs.getString("pref_server_url", ""))) {
            mServerURL = prefs.getString("pref_server_url", "");
            Log.d(TAG, "new server URL: " + mServerURL);
            close();

            NetworkInfo activeNetwork = mConnManager == null ? null : mConnManager.getActiveNetworkInfo();
            if (activeNetwork != null && activeNetwork.isConnectedOrConnecting()) {
                connect();
            } else {
                Log.d(TAG, "skipping connect due to missing network");
            }
        }
    }

    private synchronized boolean isConnected() {
        return mEventSource != null && mConnected.get();
    }

    public synchronized void reconnect() {
        if (isConnected()) {
            close();
        }
        connect();
    }

    private synchronized void connect() {
        if (mServerURL != null && !mServerURL.isEmpty() && !isConnected()) {
            String topic = buildTopic();

            if (topic.length() > 0) {
                URI uri;

                try {
                    uri = new URI(mServerURL + "/rest/events?topics=" + topic);

                    if (uri.getPort() < 0 || uri.getPort() > 65535) {
                        Log.e(TAG, "Could not create SSE connection, port out of range: " + uri.getPort());
                        return;
                    }
                } catch (URISyntaxException e) {
                    Log.e(TAG, "Could not create SSE connection", e);
                    return;
                }

                Log.d(TAG, "creating SSE handler and EventSource...");
                client = new SSEHandler();
                mEventSource = new EventSource(client);

                new AsyncConnectTask(uri).execute(mEventSource);
                Log.d(TAG, "EventSource connection initiated");
            } else {
                Log.d(TAG, "EventSource connection skipped: no subscriptions");
            }
        } else {
            Log.d(TAG, "EventSource connection skipped: serverURL=" + mServerURL
                    + ", connected=" + isConnected());
        }
    }

    private String buildTopic() {
        StringBuilder topic = new StringBuilder();
        synchronized (mSubscriptions) {
            for (String item : mSubscriptions.keySet()) {
                if (topic.length() > 0) {
                    topic.append(",");
                }
                topic.append("smarthome/items/").append(item).append("/statechanged");
            }
        }
        synchronized (mCmdSubscriptions) {
            for (String item : mCmdSubscriptions.keySet()) {
                if (topic.length() > 0) {
                    topic.append(",");
                }
                topic.append("smarthome/items/").append(item).append("/command");
            }
        }

        return topic.toString();
    }

    private synchronized void close() {
        if (mEventSource != null) {
            AsyncCloseTask t = new AsyncCloseTask();
            t.execute(mEventSource);
            mEventSource = null;
        }

        client = null;
    }

    public void terminate(Context context) {
        ConnectionUtil.getInstance().removeCertListener(mCertListener);
        LocalBroadcastManager.getInstance(context).unregisterReceiver(mReceiver);

        averagePropagator.terminate();
        connectionListeners.clear();
        mSubscriptions.clear();
    }

    public String getState(String item) {
        return mValues.get(item);
    }

    public void addStateToAverage(String item, Integer state, int updateInterval) {
        if (averagePropagator.addStateToAverage(item, state, updateInterval)) {
            // in case this is the first value for the item propagate it
            updateState(item, state.toString());
        }
    }

    private void updateStateWithTimeout(String item, String state, String timeoutState, int timeoutInSeconds) {
        updateState(item, state);

        averagePropagator.setStateIn(item, timeoutState, timeoutInSeconds);
    }

    public void updateState(String item, String state) {
        updateState(item, state, false);
    }

    private void updateState(String item, String state, boolean resend) {
        if (item != null && !item.isEmpty() && state != null && (resend || !state.equals(getState(item)))) {
            if (resend || !state.equals(lastUpdates.get(item))) {
                synchronized (lastUpdates) {
                    lastUpdates.put(item, state);
                }

                if (isConnected()) {
                    Log.v(TAG, "Sending state update for " + item + ": " + state);
                    mRestClient.setItemState(mServerURL, new ItemState(item, state));
                }
            }
        }
    }

    public void updateJpeg(String item, byte[] data) {
        if (data != null) {
            updateState(item, "data:image/jpeg;base64," + Base64.encodeToString(data, Base64.NO_WRAP));
        }
    }

    public void sendCurrentValues() {
        Log.v(TAG, "Sending pending updates...");
        synchronized (lastUpdates) {
            for (Map.Entry<String, String> entry : lastUpdates.entrySet()) {
                updateState(entry.getKey(), entry.getValue(), true);
            }
        }
        Log.v(TAG, "Pending updates sent");
    }

    @Override
    public void disconnected() {
        if (isConnected()) {
            close();
        }
    }

    @Override
    public void connected() {
        if (!isConnected()) {
            connect();
        }
    }

    private class SSEHandler implements EventSourceHandler {
        private final ISubscriptionListener mListener = new ISubscriptionListener() {
            @Override
            public void itemUpdated(String name, String value) {
                propagateItem(name, value);
            }

            @Override
            public void itemInvalid(String name) {
                propagateItem(name, "ITEM NOT FOUND");
            }
        };

        private SSEHandler() {
            mConnected.set(false);
        }

        @Override
        public void onConnect() {
            Log.v(TAG, "SSE onConnect");

            if (!mConnected.getAndSet(true)) {
                synchronized (connectionListeners) {
                    for (IConnectionListener l : connectionListeners) {
                        l.connected(mServerURL);
                    }
                }
                sendCurrentValues();
                fetchCurrentItemsState();
            }
        }

        @Override
        public void onMessage(String event, MessageEvent message) {
            Log.v(TAG, "onMessage: message=" + message);
            if (message != null) {
                try {
                    JSONObject jObject = new JSONObject(message.data);
                    String type = jObject.getString("type");
                    if ("ItemStateChangedEvent".equals(type) || "GroupItemStateChangedEvent".equals(type)) {
                        JSONObject payload = new JSONObject(jObject.getString("payload"));
                        String topic = jObject.getString("topic");
                        String name = topic.split("/")[2];

                        if (mSubscriptions.containsKey(name)) {
                            final String value = payload.getString("value");
                            propagateItem(name, value);
                        }
                    } else if ("ItemCommandEvent".equals(type)) {
                        JSONObject payload = new JSONObject(jObject.getString("payload"));
                        String topic = jObject.getString("topic");
                        String name = topic.split("/")[2];

                        if (mCmdSubscriptions.containsKey(name)) {
                            final String value = payload.getString("value");
                            propagateCommand(name, value);
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing JSON", e);
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            Log.v(TAG, "SSE onError: t=" + t.getMessage());

            if (mConnected.getAndSet(false)) {
                mValues.clear();
                averagePropagator.clear();

                synchronized (connectionListeners) {
                    for (IConnectionListener l : connectionListeners) {
                        l.disconnected();
                    }
                }
            }
        }

        @Override
        public void onLogMessage(String s) {
            Log.v(TAG, "SSE onLogMessage: " + s);
        }

        private synchronized void fetchCurrentItemsState() {
            HashSet<String> missingItems = new HashSet<>();
            synchronized (mSubscriptions) {
                for (String item : mSubscriptions.keySet()) {
                    if (!mValues.containsKey(item)) {
                        missingItems.add(item);
                    }
                }
            }

            Log.d(TAG, "Actively fetching items state");
            for (String item : missingItems) {
                mRestClient.getItemState(mServerURL, mListener, item);
            }
        }

        private void propagateItem(String name, String value) {
            if (!value.equals(mValues.get(name))) {
                propagate(mSubscriptions, name, value);
            }
        }

        private void propagateCommand(String name, String value) {
            propagate(mCmdSubscriptions, name, value);
        }

        private void propagate(HashMap<String, ArrayList<IStateUpdateListener>> subscriptions, String name, String value) {
            mValues.put(name, value);

            Log.v(TAG, "propagating item: " + name + "=" + value);

            final ArrayList<IStateUpdateListener> listeners;
            synchronized (subscriptions) {
                listeners = subscriptions.get(name);
            }

            if (listeners != null) {
                for (IStateUpdateListener l : listeners) {
                    l.itemUpdated(name, value);
                }
            }
        }
    }
}
