package de.vier_bier.habpanelviewer.openhab;

import android.util.Log;

import com.here.oksse.OkSse;
import com.here.oksse.ServerSentEvent;

import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLHandshakeException;

import de.vier_bier.habpanelviewer.NetworkTracker;
import de.vier_bier.habpanelviewer.connection.OkHttpClientFactory;
import de.vier_bier.habpanelviewer.db.CredentialManager;
import okhttp3.Challenge;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SseConnection implements NetworkTracker.INetworkListener, CredentialManager.CredentialsListener {
    private static final String TAG = "HPV-SseConnection";

    String mUrl;

    private boolean mNetworkConnected;
    private boolean mHasBeenConnected;

    private final List<ISseListener> mListeners = new ArrayList<>();
    volatile Status mStatus = Status.NOT_CONNECTED;
    private ServerSentEvent mEventSource;

    SseConnection() {
    }

    void setServerUrl(String url) {
        if (mUrl == null && url != null || mUrl != null && !mUrl.equals(url)) {
            mUrl = url;
            mHasBeenConnected = false;

            if (mStatus.isConnecting() || mStatus == Status.CONNECTED) {
                disconnect();
            }

            if (mStatus == Status.NOT_CONNECTED || mStatus == Status.URL_MISSING) {
                connect();
            }
        }
    }

    @Override
    public void credentialsEntered(String user, String pass) {
        if (mStatus.isConnecting() || mStatus == Status.CONNECTED) {
            disconnect();
        }

        if (mStatus == Status.NOT_CONNECTED || mStatus == Status.UNAUTHORIZED) {
            connect();
        }
    }

    @Override
    public void credentialsCancelled() {
    }

    void addListener(ISseListener l) {
        synchronized (mListeners) {
            if (!mListeners.contains(l)) {
                mListeners.add(l);
            }
        }
    }

    void removeListener(ISseListener l) {
        synchronized (mListeners) {
            mListeners.remove(l);
        }
    }

    @Override
    public void disconnected() {
        setNetworkConnected(false);
    }

    @Override
    public void connected() {
        setNetworkConnected(true);
    }

    synchronized void connect() {
        Log.v(TAG, "SseConnection.connect");
        Log.v(TAG, "mEventSource=" + (mEventSource == null ? "null" : mEventSource.hashCode()));

        boolean isReconnect = false;
        if (mEventSource != null) {
            isReconnect = true;
            ServerSentEvent oldSource = mEventSource;
            mEventSource = null;
            oldSource.close();
        }

        if (!mNetworkConnected) {
            setStatus(Status.NO_NETWORK);
        } else if (mUrl == null || mUrl.trim().isEmpty()) {
            setStatus(Status.URL_MISSING);
        } else {
            setStatus(isReconnect ? Status.RECONNECTING : Status.CONNECTING);

            OkHttpClient client = createConnection();
            OkSse oksse = new OkSse(client);
            ServerSentEvent.Listener sseHandler = new SSEHandler();
            try {
                Request request = new Request.Builder().url(buildUrl()).build();
                mEventSource = oksse.newServerSentEvent(request, sseHandler);
                Log.v(TAG, "mEventSource = " + mEventSource.request().url().toString());
                Log.v(TAG, "mEventSource=" + (mEventSource == null ? "null" : mEventSource.hashCode()));
            } catch (IllegalArgumentException e) {
                setStatus(Status.INVALID_URL);
            }
        }
    }

    OkHttpClient createConnection() {
        return OkHttpClientFactory.getInstance().create();
    }

    String buildUrl() {
        return mUrl + "/rest/events";
    }

    synchronized void disconnect() {
        if (mEventSource != null) {
            ServerSentEvent oldSource = mEventSource;
            mEventSource = null;
            oldSource.close();
        }

        setStatus(Status.NOT_CONNECTED);
    }

    private void setNetworkConnected(boolean connected) {
        mNetworkConnected = connected;

        if (!connected) {
            disconnect();
            setStatus(Status.NO_NETWORK);
        } else if (mStatus == Status.NOT_CONNECTED || mStatus == Status.NO_NETWORK) {
            connect();
        }
    }

    private void setStatus(Status status) {
        Log.e(TAG, "setting status " + status.name() + ", connection=" + (mEventSource == null ? "null" : mEventSource.hashCode()), new Exception("dummy"));

        if (status != mStatus) {
            mStatus = status;

            if (mStatus == Status.CONNECTED) {
                mHasBeenConnected = true;
            }

            synchronized (mListeners) {
                for (ISseListener l : mListeners) {
                    if (l instanceof ISseConnectionListener) {
                        ((ISseConnectionListener) l).statusChanged(status);
                    }
                }
            }
        }
    }

    public Status getStatus() {
        return mStatus;
    }

    void dispose() {
        disconnect();
        mListeners.clear();
    }

    interface ISseDataListener extends ISseListener{
        void data(String data);
    }

    public enum Status {
        // initial state
        NOT_CONNECTED,
        CONNECTING, CONNECTED,
        // whenever the connection is lost, state will be RECONNECTING
        RECONNECTING,
        // device has not network
        NO_NETWORK,
        // url is not set or invalid
        URL_MISSING, INVALID_URL,
        // missing or wrong auth
        UNAUTHORIZED,
        // failed to connect due to invalid certificate
        CERTIFICATE_ERROR,
        // other failure
        FAILURE;

        boolean isConnecting() {
            return this == RECONNECTING || this == CONNECTING;
        }
   }

    private class SSEHandler implements ServerSentEvent.Listener {
        SSEHandler() { }

        @Override
        public void onOpen(ServerSentEvent sse, Response response) {
            Log.v(TAG, "SSEHandler.onOpen: mEventSource=" + (mEventSource == null ? "null" : mEventSource.hashCode()) + ", sse=" + sse.hashCode());

            setStatus(Status.CONNECTED);
        }

        @Override
        public void onMessage(ServerSentEvent sse, String id, String event, String message) {
            Log.v(TAG, "SSEHandler.onMessage: mEventSource=" + (mEventSource == null ? "null" : mEventSource.hashCode()) + ", sse=" + sse.hashCode());

            if (mEventSource == sse) {
                synchronized (mListeners) {
                    for (ISseListener l : mListeners) {
                        if (l instanceof ISseDataListener) {
                            ((ISseDataListener) l).data(message);
                        }
                    }
                }
            } else {
                Log.v(TAG, "ignoring event from old event source!");
            }
        }

        @Override
        public void onComment(ServerSentEvent sse, String comment) {
        }

        @Override
        public boolean onRetryTime(ServerSentEvent sse, long milliseconds) {
            return false;
        }

        @Override
        public boolean onRetryError(ServerSentEvent sse, Throwable throwable, Response response) {
            Log.v(TAG, "SSEHandler.onRetryError: mEventSource=" + (mEventSource == null ? "null" : mEventSource.hashCode()) + ", sse=" + sse.hashCode() + ", status=" + mStatus);

            if (mEventSource == sse) {
                if (throwable instanceof SSLHandshakeException) {
                    setStatus(Status.CERTIFICATE_ERROR);
                } else if (response != null && (response.code() == 401 || response.code() == 407)) {
                    String realm = null;
                    for (Challenge c : response.challenges()) {
                        realm = c.authParams().get("realm");
                        if (realm != null) {
                            break;
                        }
                    }
                    OkHttpClientFactory.getInstance().setHost(response.request().url().host());
                    OkHttpClientFactory.getInstance().setRealm(realm);

                    setStatus(Status.UNAUTHORIZED);
                } else if (mHasBeenConnected) {
                    // connection lost, retry
                    setStatus(Status.RECONNECTING);
                } else {
                    setStatus(Status.FAILURE);
                }

                return mHasBeenConnected;
            } else {
                Log.v(TAG, "ignoring event from old event source!");
            }

            return false;
        }

        @Override
        public void onClosed(ServerSentEvent sse) {
            Log.v(TAG, "SSEHandler.onClosed: mEventSource=" + (mEventSource == null ? "null" : mEventSource.hashCode()) + ", sse=" + sse.hashCode() + ", status=" + mStatus);

            if (mEventSource == sse) {
                if (mStatus == Status.CONNECTED){
                    setStatus(Status.NOT_CONNECTED);

                    Log.v(TAG, "sse connection closed unexpectedly. reconnecting...");

                    // connection closed from outside, try to reconnect
                    connect();
                }
            } else {
                Log.v(TAG, "ignoring event from old event source!");
            }
        }

        @Override
        public Request onPreRetry(ServerSentEvent sse, Request originalRequest) {
            return originalRequest;
        }
    }
}
