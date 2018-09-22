package de.vier_bier.habpanelviewer.openhab;

import android.os.AsyncTask;
import android.util.Log;

import java.net.URI;

import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;
import io.opensensors.sse.client.EventSource;

public class AsyncConnectTask extends AsyncTask<EventSource, Void, Void> {
    private static final String TAG = "HPV-AsyncConnectTask";

    private final URI fUri;

    AsyncConnectTask(URI uri) {
        fUri = uri;
    }

    @Override
    protected Void doInBackground(EventSource... eventSources) {
        for (EventSource s : eventSources) {
            try {
                s.connect(fUri, ConnectionUtil.getInstance().createSslContext());
                Log.d(TAG, "EventSource connected");
            } catch (Exception e) {
                Log.e(TAG, "failed to connect event source", e);
            }
        }

        return null;
    }
}
