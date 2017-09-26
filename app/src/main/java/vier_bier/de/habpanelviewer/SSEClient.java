package vier_bier.de.habpanelviewer;

import android.util.Log;

import com.tylerjroach.eventsource.EventSource;
import com.tylerjroach.eventsource.EventSourceHandler;
import com.tylerjroach.eventsource.MessageEvent;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Set;

/**
 * Client for openHABs SSE service. Listens for item value changes.
 */
class SSEClient {
    private Set<String> items;
    private String baseUrl;
    private EventSource eventSource;
    private SSEHandler client;
    private FetchItemStateTask task;
    private final ArrayList<StateListener> stateListeners = new ArrayList<>();
    private ConnectionListener connectionListener;

    public SSEClient(String baseUrl, Set<String> items) {
        this.items = items;
        this.baseUrl = baseUrl;
    }

    public void addStateListener(StateListener listener) {
        synchronized (stateListeners) {
            if (!stateListeners.contains(listener)) {
                stateListeners.add(listener);
            }
        }
    }

    public void removeStateListener(StateListener listener) {
        synchronized (stateListeners) {
            stateListeners.remove(listener);
        }
    }

    public void setConnectionListener(ConnectionListener l) {
        connectionListener = l;
    }

    public void connect() {
        if (!baseUrl.isEmpty()) {
            StringBuilder topic = new StringBuilder();
            for (String item : items) {
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
                    uri = new URI(baseUrl + "/rest/events?topics=" + topic.toString());
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                    return;
                }

                client = new SSEHandler();
                eventSource = new EventSource.Builder(uri)
                        .eventHandler(client)
                        .build();

                eventSource.connect();
                Log.d("Habpanelview", "EventSource connected");
                return;
            }
        }

        Log.d("Habpanelview", "EventSource connection skipped");
    }

    public boolean close() {
        boolean closed = false;

        if (eventSource != null) {
            EventSource oldSource = eventSource;
            eventSource = null;
            oldSource.close();
            closed = true;
            Log.d("Habpanelview", "EventSource closed");
        }

        client = null;

        return closed;
    }

    /**
     * All callbacks are currently returned on executor thread.
     * If you want to update the ui from a callback, make sure to post to main thread
     */
    private class SSEHandler implements EventSourceHandler {
        private boolean connected = false;

        private SSEHandler() {
        }

        @Override
        public void onConnect() {
            connected = true;
            Log.v("Habpanelview", "SSE connected");

            connectionListener.connected(baseUrl);
            fetchCurrentItemsState();
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
            Log.v("Habpanelview", "onClosed: reConnect=" + String.valueOf(willReconnect));
            if (connected) {
                connected = false;
                connectionListener.disconnected();

                if (close()) {
                    connect();
                }
            }
        }

        private synchronized void fetchCurrentItemsState() {
            if (task != null) {
                task.cancel(true);
            }

            task = new FetchItemStateTask(baseUrl, stateListeners);
            Log.d("Habpanelview", "Actively fetching items state");
            task.execute(items);
        }
    }
}
