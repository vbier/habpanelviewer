package de.vier_bier.habpanelviewer.openhab;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLHandshakeException;

import de.vier_bier.habpanelviewer.NetworkTracker;
import de.vier_bier.habpanelviewer.connection.ConnectionStatistics;
import de.vier_bier.habpanelviewer.db.CredentialManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

public class SseConnection implements NetworkTracker.INetworkListener, CredentialManager.CredentialsListener {
    private static final String TAG = "HPV-SseConnection";

    String mUrl;
    private boolean mNetworkConnected;

    private final List<ISseListener> mListeners = new ArrayList<>();
    Status mStatus = Status.NOT_CONNECTED;
    private EventSource mEventSource;

    SseConnection() {
    }

    void setServerUrl(String url) {
        if (mUrl == null && url != null || mUrl != null && !mUrl.equals(url)) {
            mUrl = url;

            if (mStatus.isConnecting() || mStatus == Status.CONNECTED) {
                disconnect();
            }

            if (mStatus == Status.NOT_CONNECTED || mStatus == Status.URL_MISSING) {
                connect();
            }
        }
    }

    @Override
    public void credentialsEntered() {
        if (mStatus.isConnecting() || mStatus == Status.CONNECTED) {
            disconnect();
        }

        if (mStatus == Status.NOT_CONNECTED || mStatus == Status.UNAUTHORIZED) {
            connect();
        }
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

    void connect() {
        Log.v(TAG, "SseConnection.connect");

        if (mEventSource != null) {
            mEventSource.cancel();
            mEventSource = null;
        }

        if (!mNetworkConnected) {
            setStatus(Status.NO_NETWORK);
        } else if (mUrl == null || mUrl.trim().isEmpty()) {
            setStatus(Status.URL_MISSING);
        } else {
            setStatus(Status.CONNECTING);

            OkHttpClient client = createConnection();
            EventSourceListener sseHandler = new SSEHandler();
            try {
                Request request = new Request.Builder().url(buildUrl()).build();
                mEventSource = EventSources.createFactory(client).newEventSource(request, sseHandler);

                Log.v(TAG, "mEventSource = " + mEventSource.request().url().toString());
            } catch (IllegalArgumentException e) {
                setStatus(Status.INVALID_URL);
            }
        }
    }

    OkHttpClient createConnection() {
        return ConnectionStatistics.OkHttpClientFactory.getInstance().create();
    }

    String buildUrl() {
        return mUrl + "/rest/events";
    }

    @TestOnly
    void disconnect() {
        if (mEventSource != null) {
            mEventSource.cancel();
            mEventSource = null;
        }

        setStatus(Status.NOT_CONNECTED);
    }

    private void setNetworkConnected(boolean connected) {
        mNetworkConnected = connected;

        if (!connected || (mStatus == Status.NOT_CONNECTED || mStatus == Status.NO_NETWORK)) {
            connect();
        }
    }

    private void setStatus(Status status) {
        if (status != mStatus) {
            mStatus = status;

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
            return this == CONNECTING;
        }
   }

    private class SSEHandler extends EventSourceListener {
        private int failureCount = 0;
        SSEHandler() { }

        @Override
        public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
            failureCount = 0;
            setStatus(Status.CONNECTED);
        }

        @Override
        public void onClosed(@NotNull EventSource eventSource) {
            if (mStatus == Status.CONNECTED){
                setStatus(Status.NOT_CONNECTED);
            }
        }

        @Override
        public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
            if ("message".equals(type)) {
                synchronized (mListeners) {
                    for (ISseListener l : mListeners) {
                        if (l instanceof ISseDataListener) {
                            ((ISseDataListener) l).data(data);
                        }
                    }
                }
            }
        }

        @Override
        public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
            failureCount++;

            if (t instanceof SSLHandshakeException) {
                setStatus(Status.CERTIFICATE_ERROR);
            } else if (response != null && (response.code() == 401 || response.code() == 407)) {
                setStatus(Status.UNAUTHORIZED);
            } else {
                setStatus(Status.NOT_CONNECTED);

                int delay = 500;

                if (failureCount > 100) {
                    delay = 5000;
                } else if (failureCount > 50) {
                    delay = 2000;
                } else if (failureCount > 30) {
                    delay = 1000;
                } else if (failureCount > 10) {
                    delay = 1000;
                }

                // trigger reconnect
                final Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        connect();
                    }
                }, delay);
            }
        }
    }
}
