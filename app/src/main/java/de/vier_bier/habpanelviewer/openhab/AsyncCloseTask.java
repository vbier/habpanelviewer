package de.vier_bier.habpanelviewer.openhab;

import android.os.AsyncTask;
import android.util.Log;

import io.opensensors.sse.client.EventSource;

class AsyncCloseTask extends AsyncTask<EventSource, Void, Void> {
    private static final String TAG = "HPV-AsyncCloseTask";

    @Override
    protected Void doInBackground(EventSource... eventSources) {
        for (EventSource s : eventSources) {
            try {
                s.close();
            } catch (NullPointerException e) {
                // no idea why this happens, but it should do no harm to ignore it as the
                // event source is discarded anyway
            }
            Log.d(TAG, "EventSource closed");
        }

        return null;
    }
}
