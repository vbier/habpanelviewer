package de.vier_bier.habpanelviewer.openhab;

import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import java.net.URI;

import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;
import io.opensensors.sse.client.EventSource;

class AsyncConnectTask extends AsyncTask<EventSource, Void, Void> {
    private static final String TAG = "HPV-AsyncConnectTask";

    private final URI fUri;
    private final String fAuthStr;

    AsyncConnectTask(URI uri, String authStr) {
        fUri = uri;
        fAuthStr = authStr;
    }

    @Override
    protected Void doInBackground(EventSource... eventSources) {
        for (EventSource s : eventSources) {
            try {
                String basicAuth = fAuthStr == null ? null
                        : "Basic " + new String(Base64.encode(fAuthStr.getBytes(), Base64.NO_WRAP));

                s.connect(fUri, basicAuth, ConnectionUtil.getInstance().createSslContext());
                Log.d(TAG, "EventSource connected");
            } catch (Exception e) {
                Log.e(TAG, "failed to connect event source", e);
            }
        }

        return null;
    }
}
