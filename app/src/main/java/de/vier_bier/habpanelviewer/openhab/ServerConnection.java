package de.vier_bier.habpanelviewer.openhab;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.tylerjroach.eventsource.CertificateIgnoringSSLEngineFactory;
import com.tylerjroach.eventsource.EventSource;
import com.tylerjroach.eventsource.EventSourceHandler;
import com.tylerjroach.eventsource.MessageEvent;

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
import de.vier_bier.habpanelviewer.openhab.average.StatePropagator;
import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;

/**
 * Client for openHABs SSE service. Listens for item value changes.
 */
public class ServerConnection implements StatePropagator {
    private static final String SKIP_INITIAL = "SKIP_INITIAL";
    private Context mCtx;
    private String mServerURL;
    private EventSource mEventSource;
    private ConnectionUtil.CertChangedListener mCertListener;

    private final HashMap<String, ArrayList<StateUpdateListener>> mSubscriptions = new HashMap<>();
    private HashMap<String, String> mValues = new HashMap<>();
    private AtomicBoolean mConnected = new AtomicBoolean(false);

    private SSEHandler client;
    private FetchItemStateTask task;
    private final ArrayList<ConnectionListener> connectionListeners = new ArrayList<>();
    private final HashMap<String, String> lastUpdates = new HashMap<>();
    private final AveragePropagator averagePropagator = new AveragePropagator(this);

    private BroadcastReceiver mNetworkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm == null ? null : cm.getActiveNetworkInfo();

            if (activeNetwork != null && activeNetwork.isConnectedOrConnecting()) {
                connect();
            } else {
                close();
            }
        }
    };

    public ServerConnection(Context context) {
        mCtx = context;

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mCtx.registerReceiver(mNetworkReceiver, intentFilter);

        mCertListener = new ConnectionUtil.CertChangedListener() {
            @Override
            public void certAdded() {
                Log.d("Habpanelview", "Cert added, reconnecting to server...");

                // if we are not connected, try to connect. connection may have failed due to
                // certificate errors
                if (!mConnected.get()) {
                    close();
                    connect();
                }
            }
        };

        ConnectionUtil.addCertListener(mCertListener);
    }

    @Override
    protected void finalize() throws Throwable {
        ConnectionUtil.removeCertListener(mCertListener);

        super.finalize();
    }

    public void addConnectionListener(ConnectionListener l) {
        synchronized (connectionListeners) {
            if (!connectionListeners.contains(l)) {
                connectionListeners.add(l);
            }
        }
    }

    public void subscribeItems(StateUpdateListener l, String... names) {
        subscribeItems(l, true, names);
    }

    public boolean subscribeItems(StateUpdateListener l, boolean initialValue, String... names) {
        boolean itemsChanged = false;

        final HashSet<String> newItems = new HashSet<>();
        for (String name : names) {
            if (name != null && !name.isEmpty()) {
                newItems.add(name);
            }
        }

        synchronized (mSubscriptions) {
            for (String name : new HashSet<>(mSubscriptions.keySet())) {
                ArrayList<StateUpdateListener> listeners = mSubscriptions.get(name);
                if (listeners != null && listeners.contains(l) && !newItems.contains(name)) {
                    listeners.remove(l);

                    if (listeners.isEmpty()) {
                        itemsChanged = true;
                        mSubscriptions.remove(name);
                        mValues.remove(name);
                    }
                }
            }

            for (String name : newItems) {
                ArrayList<StateUpdateListener> listeners = mSubscriptions.get(name);
                if (listeners == null) {
                    itemsChanged = true;
                    listeners = new ArrayList<>();
                    mSubscriptions.put(name, listeners);

                    if (!initialValue) {
                        // if initial value shall not be propagated, put SKIP_INITIAL in the value map
                        // as this makes the connect skip fetching of the initial value
                        mValues.put(name, SKIP_INITIAL);
                    }
                } else if (mValues.containsKey(name) && initialValue) {
                    l.itemUpdated(name, mValues.get(name));
                }

                if (!listeners.contains(l)) {
                    listeners.add(l);
                }
            }
        }

        if (itemsChanged && mConnected.get()) {
            close();
            connect();
        }

        return itemsChanged;
    }

    public void updateFromPreferences(SharedPreferences prefs) {
        // in case server url has changed reconnect
        if (mServerURL == null || !mServerURL.equalsIgnoreCase(prefs.getString("pref_url", ""))) {
            mServerURL = prefs.getString("pref_url", "");
            close();
        }

        ConnectivityManager cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm == null ? null : cm.getActiveNetworkInfo();
        if (activeNetwork != null && activeNetwork.isConnectedOrConnecting()) {
            connect();
        } else {
            close();
        }
    }

    private void connect() {
        if (!mServerURL.isEmpty() && mEventSource == null) {
            StringBuilder topic = new StringBuilder();
            synchronized (mSubscriptions) {
                for (String item : mSubscriptions.keySet()) {
                    if (topic.length() > 0) {
                        topic.append(",");
                    }
                    topic.append("smarthome/items/").append(item);
                }
            }

            if (topic.length() > 0) {
                URI uri;

                try {
                    uri = new URI(mServerURL + "/rest/events?topics=" + topic.toString());

                    if (uri.getPort() < 0 || uri.getPort() > 65535) {
                        Log.e("Habpanelview", "Could not create SSE connection, port out of range: " + uri.getPort());
                        return;
                    }
                } catch (URISyntaxException e) {
                    Log.e("Habpanelview", "Could not create SSE connection", e);
                    return;
                }

                client = new SSEHandler();
                EventSource.Builder builder = new EventSource.Builder(uri).eventHandler(client);
                if (mServerURL.startsWith("https:")) {
                    builder = builder.sslEngineFactory(new CertificateIgnoringSSLEngineFactory());
                }
                mEventSource = builder.build();
                mEventSource.connect();
                Log.d("Habpanelview", "EventSource mConnected");
            }
        } else {
            Log.d("Habpanelview", "EventSource connection skipped");
        }
    }

    private synchronized void close() {
        if (mEventSource != null) {
            mEventSource.close();
            mEventSource = null;
            Log.d("Habpanelview", "EventSource closed");
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

            if (mConnected.get()) {
                Log.v("Habpanelview", "Sending state update for " + item + ": " + state);

                SetItemStateTask t = new SetItemStateTask(mServerURL);
                t.execute(new ItemState(item, state));
            }
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

    /**
     * All callbacks are currently returned on executor thread.
     * If you want to update the ui from a callback, make sure to post to main thread
     */
    private class SSEHandler implements EventSourceHandler {

        private SSEHandler() {
        }

        @Override
        public void onConnect() {
            Log.v("Habpanelview", "SSE onConnect");

            if (!mConnected.getAndSet(true)) {
                synchronized (connectionListeners) {
                    for (ConnectionListener l : connectionListeners) {
                        l.connected(mServerURL);
                    }
                }
                sendCurrentValues();
                fetchCurrentItemsState();
            }
        }

        @Override
        public void onMessage(String event, MessageEvent message) {
            if (message != null) {
                try {
                    JSONObject jObject = new JSONObject(message.data);
                    String type = jObject.getString("type");
                    if ("ItemStateEvent".equals(type) || "GroupItemStateChangedEvent".equals(type)) {
                        JSONObject payload = new JSONObject(jObject.getString("payload"));
                        String topic = jObject.getString("topic");
                        String name = topic.split("/")[2];

                        final String value = payload.getString("value");
                        propagateItem(name, value);
                    }
                } catch (JSONException e) {
                    Log.e("Habpanelview", "Error parsing JSON", e);
                }
            }
        }

        private void propagateItem(String name, String value) {
            if (!value.equals(mValues.put(name, value))) {
                final ArrayList<StateUpdateListener> listeners;
                synchronized (mSubscriptions) {
                    listeners = mSubscriptions.get(name);
                }

                for (StateUpdateListener l : listeners) {
                    l.itemUpdated(name, value);
                }
            }
        }

        @Override
        public void onComment(String comment) {
        }

        @Override
        public void onError(Throwable t) {
        }

        @Override
        public void onClosed(boolean willReconnect) {
            Log.v("Habpanelview", "SSE onClosed: reConnect=" + String.valueOf(willReconnect));

            if (mConnected.getAndSet(false)) {
                mValues.clear();
                averagePropagator.clear();

                synchronized (connectionListeners) {
                    for (ConnectionListener l : connectionListeners) {
                        l.disconnected();
                    }
                }
            }
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

            task = new FetchItemStateTask(mServerURL, new SubscriptionListener() {
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
    }
}
