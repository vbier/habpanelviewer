package de.vier_bier.habpanelviewer.openhab;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import de.vier_bier.habpanelviewer.openhab.average.AveragePropagator;
import de.vier_bier.habpanelviewer.openhab.average.IStatePropagator;
import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;
import io.opensensors.sse.client.EventSource;
import io.opensensors.sse.client.EventSourceHandler;
import io.opensensors.sse.client.MessageEvent;

/**
 * Client for openHABs SSE service. Listens for item value changes.
 */
public class ServerConnection implements IStatePropagator {
    private final Context mCtx;
    private String mServerURL;
    private EventSource mEventSource;
    private final ConnectionUtil.CertChangedListener mCertListener;

    private final HashMap<String, ArrayList<IStateUpdateListener>> mSubscriptions = new HashMap<>();
    private final HashMap<String, ArrayList<IStateUpdateListener>> mCmdSubscriptions = new HashMap<>();
    private final HashMap<String, String> mValues = new HashMap<>();

    private SSEHandler client;
    private FetchItemStateTask task;
    private final ArrayList<IConnectionListener> connectionListeners = new ArrayList<>();
    private final HashMap<String, String> lastUpdates = new HashMap<>();
    private final AveragePropagator averagePropagator = new AveragePropagator(this);

    private final BroadcastReceiver mNetworkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm == null ? null : cm.getActiveNetworkInfo();

            if (activeNetwork != null && activeNetwork.isConnectedOrConnecting()) {
                if (!isConnected()) {
                    connect();
                }
            } else if (isConnected()) {
                close();
            }
        }
    };

    public ServerConnection(Context context) {
        mCtx = context;

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mCtx.registerReceiver(mNetworkReceiver, intentFilter);

        mCertListener = () -> {
            Log.d("Habpanelview", "Cert added, reconnecting to server...");

            // if we are not connected, try to connect. connection may have failed due to
            // certificate errors
            if (!isConnected()) {
                connect();
            }
        };

        ConnectionUtil.addCertListener(mCertListener);
    }

    @Override
    protected void finalize() throws Throwable {
        ConnectionUtil.removeCertListener(mCertListener);

        super.finalize();
    }

    public void addConnectionListener(IConnectionListener l) {
        synchronized (connectionListeners) {
            if (!connectionListeners.contains(l)) {
                connectionListeners.add(l);
            }
        }
    }


    public void subscribeCommandItems(IStateUpdateListener l, String... names) {
        subscribeItems(mCmdSubscriptions, l, false, names);
    }

    public void subscribeItems(IStateUpdateListener l, String... names) {
        subscribeItems(mSubscriptions, l, true, names);
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
            close();

            ConnectivityManager cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm == null ? null : cm.getActiveNetworkInfo();
            if (activeNetwork != null && activeNetwork.isConnectedOrConnecting()) {
                connect();
            }
        }
    }

    private synchronized boolean isConnected() {
        return mEventSource != null;
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
                        Log.e("Habpanelview", "Could not create SSE connection, port out of range: " + uri.getPort());
                        return;
                    }
                } catch (URISyntaxException e) {
                    Log.e("Habpanelview", "Could not create SSE connection", e);
                    return;
                }

                client = new SSEHandler();
                mEventSource = new EventSource(client);

                final URI fUri = uri;
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... voids) {
                        try {
                            mEventSource.connect(fUri, ConnectionUtil.createSslContext());
                        } catch (Exception e) {
                            Log.e("Habpanelview", "failed to connect event source", e);
                        }

                        return null;
                    }
                }.execute();

                Log.d("Habpanelview", "EventSource connection initiated");
            }
        } else {
            Log.d("Habpanelview", "EventSource connection skipped");
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
            try {
                mEventSource.close();
            } catch (InterruptedException e) {
                Log.v("Habpanelview", "failed to wait for EventSource closure");
            }
            Log.d("Habpanelview", "EventSource closed");
            mEventSource = null;
        }

        client = null;
    }

    public void terminate() {
        mCtx.unregisterReceiver(mNetworkReceiver);
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

    public void updateState(String item, String state) {
        updateState(item, state, false);
    }

    private void updateState(String item, String state, boolean resend) {
        if (item != null && !item.isEmpty() && state != null && (resend || !state.equals(getState(item)))) {
            synchronized (lastUpdates) {
                lastUpdates.put(item, state);
            }

            if (isConnected()) {
                Log.v("Habpanelview", "Sending state update for " + item + ": " + state);

                SetItemStateTask t = new SetItemStateTask(mServerURL);
                t.execute(new ItemState(item, state));
            }
        }
    }

    public void updateJpeg(String item, byte[] data) {
        if (data != null) {
            updateState(item, "data:image/jpeg;base64," + Base64.encodeToString(data, Base64.NO_WRAP));
        }
    }

    public void sendCurrentValues() {
        Log.v("Habpanelview", "Sending pending updates...");
        synchronized (lastUpdates) {
            for (Map.Entry<String, String> entry : lastUpdates.entrySet()) {
                updateState(entry.getKey(), entry.getValue(), true);
            }
        }
        Log.v("Habpanelview", "Pending updates sent");
    }

    private class SSEHandler implements EventSourceHandler {
        private final AtomicBoolean mConnected = new AtomicBoolean(false);

        private SSEHandler() {
        }

        @Override
        public void onConnect() {
            Log.v("Habpanelview", "SSE onConnect");

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
            Log.v("Habpanelview", "onMessage: message=" + message);
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
                    Log.e("Habpanelview", "Error parsing JSON", e);
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            Log.v("Habpanelview", "SSE onError: t=" + t.getMessage());

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
            Log.v("Habpanelview", "SSE onLogMessage: " + s);
        }

        private synchronized void fetchCurrentItemsState() {
            if (task != null) {
                task.cancel(true);
            }

            HashSet<String> missingItems = new HashSet<>();
            synchronized (mSubscriptions) {
                for (String item : mSubscriptions.keySet()) {
                    if (!mValues.containsKey(item)) {
                        missingItems.add(item);
                    }
                }
            }

            task = new FetchItemStateTask(mServerURL, new ISubscriptionListener() {
                @Override
                public void itemUpdated(String name, String value) {
                    propagateItem(name, value);
                }

                @Override
                public void itemInvalid(String name) {
                    propagateItem(name, "ITEM NOT FOUND");
                }
            });
            Log.d("Habpanelview", "Actively fetching items state");
            task.execute(missingItems.toArray(new String[missingItems.size()]));
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

            Log.v("Habpanelview", "propagating item: " + name + "=" + value);

            final ArrayList<IStateUpdateListener> listeners;
            synchronized (subscriptions) {
                listeners = subscriptions.get(name);
            }

            for (IStateUpdateListener l : listeners) {
                l.itemUpdated(name, value);
            }
        }
    }
}
