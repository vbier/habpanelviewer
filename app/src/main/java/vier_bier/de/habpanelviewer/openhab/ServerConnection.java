package vier_bier.de.habpanelviewer.openhab;

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
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Client for openHABs SSE service. Listens for item value changes.
 */
public class ServerConnection {
    private Context mCtx;
    private String mServerURL;
    private boolean mIgnoreCertErrors;
    private EventSource mEventSource;

    private final HashMap<String, ArrayList<StateUpdateListener>> mSubscriptions = new HashMap<>();
    private HashMap<String, String> mValues = new HashMap<>();
    private AtomicBoolean mConnected = new AtomicBoolean(false);

    private SSEHandler client;
    private FetchItemStateTask task;
    private final ArrayList<ConnectionListener> connectionListeners = new ArrayList<>();
    private final HashSet<ItemState> pendingUpdates = new HashSet<>();

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
    }

    public void addConnectionListener(ConnectionListener l) {
        synchronized (connectionListeners) {
            if (!connectionListeners.contains(l)) {
                connectionListeners.add(l);
            }
        }
    }

    public void subscribeItems(StateUpdateListener l, String... names) {
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
                } else {
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
    }

    public void updateFromPreferences(SharedPreferences prefs) {
        // in case server url has changed reconnect
        if (mServerURL == null || !mServerURL.equalsIgnoreCase(prefs.getString("pref_url", ""))) {
            mServerURL = prefs.getString("pref_url", "");
            close();
        }

        // in case certification checking has changed reconnect
        if (mIgnoreCertErrors != prefs.getBoolean("pref_ignore_ssl_errors", false)) {
            mIgnoreCertErrors = prefs.getBoolean("pref_ignore_ssl_errors", false);

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

    public static SSLContext createSslContext(boolean ignoreCertificateErrors) {
        SSLContext sslContext;
        try {
            TrustManager[] trustAllCerts = null;

            if (ignoreCertificateErrors) {
                trustAllCerts = new TrustManager[]{new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                        Log.v("TrustManager", "checkClientTrusted");
                    }

                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        Log.v("TrustManager", "getAcceptedIssuers");
                        return null;
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        Log.v("TrustManager", "checkServerTrusted");
                    }
                }};
            }

            sslContext = SSLContext.getInstance("TLS");
            try {
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            } catch (KeyManagementException e) {
                return null;
            }
        } catch (NoSuchAlgorithmException e1) {
            return null;
        }

        return sslContext;
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
                    builder = builder.sslEngineFactory(new CertificateIgnoringSSLEngineFactory(mIgnoreCertErrors));
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
        connectionListeners.clear();
        mSubscriptions.clear();
    }

    public String getState(String item) {
        return mValues.get(item);
    }

    public void updateState(String item, String state) {
        if (item != null && !item.isEmpty() && state != null && !state.equals(getState(item))) {
            if (mConnected.get()) {
                Log.v("Habpanelview", "Sending state update for " + item + ": " + state);

                SetItemStateTask t = new SetItemStateTask(mServerURL, mIgnoreCertErrors);
                t.execute(new ItemState(item, state));
            } else {
                Log.v("Habpanelview", "Buffering update of item " + item);
                synchronized (pendingUpdates) {
                    pendingUpdates.add(new ItemState(item, state));
                }
            }
        }
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
                sendPendingUpdates();
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

                synchronized (connectionListeners) {
                    for (ConnectionListener l : connectionListeners) {
                        l.disconnected();
                    }
                }
            }
        }

        private void sendPendingUpdates() {
            Log.v("Habpanelview", "Sending pending updates...");
            synchronized (pendingUpdates) {
                for (ItemState state : pendingUpdates) {
                    updateState(state.mItemName, state.mItemState);
                }
                pendingUpdates.clear();
            }
            Log.v("Habpanelview", "Pending updates sent");
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

            task = new FetchItemStateTask(mServerURL, mIgnoreCertErrors, new SubscriptionListener() {
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
