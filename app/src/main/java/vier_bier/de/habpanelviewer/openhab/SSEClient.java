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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import vier_bier.de.habpanelviewer.StateListener;

/**
 * Client for openHABs SSE service. Listens for item value changes.
 */
public class SSEClient {
    private Context mCtx;
    private String mServerURL;
    private EventSource mEventSource;
    private Set<String> mItems;

    private boolean mIgnoreCertErrors;

    private SSEHandler client;
    private FetchItemStateTask task;
    private final ArrayList<StateListener> stateListeners = new ArrayList<>();
    private ConnectionListener connectionListener;

    private BroadcastReceiver mNetworkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

            if (activeNetwork != null && activeNetwork.isConnectedOrConnecting()) {
                connect();
            } else {
                close();
            }
        }
    };

    public SSEClient(Context context) {
        mCtx = context;

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mCtx.registerReceiver(mNetworkReceiver, intentFilter);
    }

    public void updateFromPreferences(SharedPreferences prefs) {
        HashSet<String> items = new HashSet<>();
        boolean flashEnabled = prefs.getBoolean("pref_flash_enabled", false);
        if (flashEnabled) {
            items.add(prefs.getString("pref_flash_item", ""));
        }

        boolean screenOnEnabled = prefs.getBoolean("pref_screen_enabled", false);
        if (screenOnEnabled) {
            items.add(prefs.getString("pref_screen_item", ""));
        }

        boolean volumeEnabled = prefs.getBoolean("pref_volume_enabled", false);
        if (volumeEnabled) {
            items.add(prefs.getString("pref_volume_item", ""));
        }

        boolean batteryEnabled = prefs.getBoolean("pref_battery_enabled", false);
        if (batteryEnabled) {
            items.add(prefs.getString("pref_battery_item", ""));
        }

        ConnectivityManager cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        // in case server url has changed reconnect
        if (mServerURL == null || !mServerURL.equalsIgnoreCase(prefs.getString("pref_url", ""))) {
            mServerURL = prefs.getString("pref_url", "");
            close();
        }

        // in case items have changed reconnect
        if (mItems == null || !mItems.equals(items)) {
            mItems = items;

            close();
        }

        // in case certification checking has changed reconnect
        if (mIgnoreCertErrors != prefs.getBoolean("pref_ignore_ssl_errors", false)) {
            mIgnoreCertErrors = prefs.getBoolean("pref_ignore_ssl_errors", false);

            close();
        }

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

    public void addStateListener(StateListener listener) {
        synchronized (stateListeners) {
            if (!stateListeners.contains(listener)) {
                stateListeners.add(listener);
            }
        }
    }

    public void setConnectionListener(ConnectionListener l) {
        connectionListener = l;
    }

    void connect() {
        if (!mServerURL.isEmpty() && mEventSource == null) {
            StringBuilder topic = new StringBuilder();
            for (String item : mItems) {
                if (!item.isEmpty()) {
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
                Log.d("Habpanelview", "EventSource connected");
                return;
            }
        } else {
            Log.d("Habpanelview", "EventSource connection skipped");
        }
    }

    synchronized void close() {
        if (mEventSource != null) {
            mEventSource.close();
            mEventSource = null;
            Log.d("Habpanelview", "EventSource closed");
        }

        client = null;
    }

    public void terminate() {
        mCtx.unregisterReceiver(mNetworkReceiver);
        stateListeners.clear();
    }

    /**
     * All callbacks are currently returned on executor thread.
     * If you want to update the ui from a callback, make sure to post to main thread
     */
    private class SSEHandler implements EventSourceHandler {
        private AtomicBoolean connected = new AtomicBoolean(false);

        private SSEHandler() {
        }

        @Override
        public void onConnect() {
            Log.v("Habpanelview", "SSE onConnect");

            if (!connected.getAndSet(true)) {
                connectionListener.connected(mServerURL);
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

                        synchronized (stateListeners) {
                            for (StateListener l : stateListeners) {
                                l.updateState(name, payload.getString("value"));
                            }
                        }
                    }
                } catch (JSONException e) {
                    Log.e("Habpanelview", "Error parsing JSON", e);
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

            if (connected.getAndSet(false)) {
                connectionListener.disconnected();
            }
        }

        private synchronized void fetchCurrentItemsState() {
            if (task != null) {
                task.cancel(true);
            }

            task = new FetchItemStateTask(mServerURL, stateListeners, mIgnoreCertErrors);
            Log.d("Habpanelview", "Actively fetching items state");
            task.execute(mItems);
        }
    }
}
