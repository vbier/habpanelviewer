package de.vier_bier.habpanelviewer.openhab;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import de.vier_bier.habpanelviewer.Constants;
import de.vier_bier.habpanelviewer.connection.ssl.CertificateManager;
import de.vier_bier.habpanelviewer.db.CredentialManager;
import de.vier_bier.habpanelviewer.openhab.average.AveragePropagator;
import de.vier_bier.habpanelviewer.openhab.average.IStatePropagator;

/**
 * Client for openHABs SSE service. Listens for item value changes.
 */
public class ServerConnection implements IStatePropagator {

    private static final String TAG = "HPV-ServerConnection";

    private String mServerURL;
    private final OpenhabSseConnection mSseConnection = new OpenhabSseConnection();
    private final RestClient mRestClient = new RestClient();

    private final CertificateManager.ICertChangedListener mCertListener;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent i) {
            final String item = i.getStringExtra(Constants.INTENT_FLAG_ITEM_NAME);
            final String state = i.getStringExtra(Constants.INTENT_FLAG_ITEM_STATE);
            final String timeoutState = i.getStringExtra(Constants.INTENT_FLAG_ITEM_TIMEOUT_STATE);
            final int timeout = i.getIntExtra(Constants.INTENT_FLAG_TIMEOUT, 60);

            updateStateWithTimeout(item, state, timeoutState, timeout);
        }
    };

    private final HashMap<String, ArrayList<IStateUpdateListener>> mSubscriptions = new HashMap<>();
    private final HashMap<String, ArrayList<IStateUpdateListener>> mCmdSubscriptions = new HashMap<>();
    private final HashMap<String, String> mValues = new HashMap<>();

    private final HashMap<String, String> lastUpdates = new HashMap<>();
    private final AveragePropagator averagePropagator = new AveragePropagator(this);

    public ServerConnection(Context context) {
        mCertListener = () -> {
            Log.d(TAG, "cert added, reconnecting to server...");

            if (mSseConnection.getStatus() == SseConnection.Status.CERTIFICATE_ERROR) {
                mSseConnection.connect();
            }
        };

        mSseConnection.addListener(new SseConnectionListener());
        mSseConnection.addItemValueListener(new SseStateUpdateListener());

        CertificateManager.getInstance().addCertListener(mCertListener);
        CredentialManager.getInstance().addCredentialsListener(mSseConnection);

        IntentFilter f = new IntentFilter();
        f.addAction(Constants.INTENT_ACTION_SET_WITH_TIMEOUT);

        LocalBroadcastManager.getInstance(context).registerReceiver(mReceiver, f);
    }

    public void addConnectionListener(ISseConnectionListener l) {
        mSseConnection.addListener(l);
    }

    public void subscribeCommandItem(IStateUpdateListener l, String name) {
        if (name != null && !"".equals(name) && checkItemsChanged(mCmdSubscriptions, l, false, name)) {
            String cmdItemName = mCmdSubscriptions.isEmpty() ? null : mCmdSubscriptions.keySet().iterator().next();
            mSseConnection.setItemNames(cmdItemName, mSubscriptions.keySet().toArray(new String[0]));
            reconnect();
        }
    }

    public void subscribeItems(IStateUpdateListener l, String... names) {
        if (checkItemsChanged(mSubscriptions, l, true, names)) {
            String cmdItemName = mCmdSubscriptions.isEmpty() ? null : mCmdSubscriptions.keySet().iterator().next();
            mSseConnection.setItemNames(cmdItemName, mSubscriptions.keySet().toArray(new String[0]));
            reconnect();
        }
    }

    private boolean checkItemsChanged(HashMap<String, ArrayList<IStateUpdateListener>> subscriptions,
                                      IStateUpdateListener l, boolean initialValue, String... names) {
        final HashSet<String> currentItems = new HashSet<>();
        final HashSet<String> newItems = new HashSet<>();
        final HashSet<String> removedItems = new HashSet<>();

        // remove empty names
        for (String name : names) {
            if (name != null && !name.trim().isEmpty()) {
                currentItems.add(name);
            }
        }

        boolean itemsChanged = false;
        synchronized (subscriptions) {
            for (String name : new HashSet<>(subscriptions.keySet())) {
                ArrayList<IStateUpdateListener> listeners = subscriptions.get(name);
                if (listeners != null && listeners.contains(l) && !currentItems.contains(name)) {
                    listeners.remove(l);

                    if (listeners.isEmpty()) {
                        itemsChanged = true;
                        subscriptions.remove(name);
                        mValues.remove(name);

                        removedItems.add(name);
                    }
                }
            }

            if (removedItems.size() > 0) {
                Log.d(TAG, "unsubscribing items: " + removedItems.toString());
            }

            for (String name : currentItems) {
                ArrayList<IStateUpdateListener> listeners = subscriptions.get(name);
                if (listeners == null) {
                    itemsChanged = true;
                    listeners = new ArrayList<>();
                    subscriptions.put(name, listeners);

                    newItems.add(name);
                } else if (mValues.containsKey(name) && initialValue) {
                    l.itemUpdated(name, mValues.get(name));
                }

                if (!listeners.contains(l)) {
                    listeners.add(l);
                }
            }

            if (newItems.size() > 0) {
                Log.d(TAG, "subscribing items: " + newItems.toString());
            }

            if (subscriptions.size() > 0) {
                Log.v(TAG, "current set of items: " + subscriptions.keySet().toString());
            }
        }

        return itemsChanged;
    }

    public void updateFromPreferences(SharedPreferences prefs, Context ctx) {
        final boolean serverChanged = mServerURL == null
                || !mServerURL.equalsIgnoreCase(prefs.getString(Constants.PREF_SERVER_URL, ""));

        if (serverChanged) {
            mServerURL = prefs.getString(Constants.PREF_SERVER_URL, "");
            Log.d(TAG, "new server URL: " + mServerURL);
            close();

            mSseConnection.setServerUrl(mServerURL);
        }
    }

    private synchronized boolean isSseConnected() {
        return mSseConnection.getStatus() == SseConnection.Status.CONNECTED;
    }

    public synchronized void reconnect() {
        if (isSseConnected()) {
            mSseConnection.disconnect();
        }
        mSseConnection.connect();
    }

    private synchronized void close() {
        mSseConnection.disconnect();
    }

    public void terminate(Context context) {
        CertificateManager.getInstance().removeCertListener(mCertListener);
        CredentialManager.getInstance().removeCredentialsListener(mSseConnection);
        LocalBroadcastManager.getInstance(context).unregisterReceiver(mReceiver);

        averagePropagator.terminate();
        mSseConnection.dispose();
        mSubscriptions.clear();
    }

    public String getState(String item) {
        return mValues.get(item);
    }

    public void removeFromAverage(String item) {
        averagePropagator.removeFromAverage(item);
    }

    public void addStateToAverage(String item, Float state, int updateInterval) {
        if (averagePropagator.addStateToAverage(item, state, updateInterval)) {
            // in case this is the first value for the item propagate it
            updateState(item, state.toString());
        }
    }

    private void updateStateWithTimeout(String item, String state, String timeoutState, int timeoutInSeconds) {
        updateState(item, state);

        averagePropagator.setStateIn(item, timeoutState, timeoutInSeconds);
    }

    public void updateState(String item, String state) {
        updateState(item, state, false);
    }

    private void updateState(String item, String state, boolean resend) {
        if (item != null && !item.isEmpty() && state != null && (resend || !state.equals(getState(item)))) {
            if (resend || !state.equals(lastUpdates.get(item))) {
                synchronized (lastUpdates) {
                    lastUpdates.put(item, state);
                }

                if (isSseConnected()) {
                    Log.v(TAG, "Sending state update for " + item + ": " + state);
                    mRestClient.setItemState(mServerURL, new ItemState(item, state));
                }
            }
        }
    }

    public void updateJpeg(String item, byte[] data) {
        if (data != null) {
            updateState(item, "data:image/jpeg;base64," + Base64.encodeToString(data, Base64.NO_WRAP));
        }
    }

    public void sendCurrentValues() {
        Log.v(TAG, "Sending pending updates...");
        synchronized (lastUpdates) {
            for (Map.Entry<String, String> entry : lastUpdates.entrySet()) {
                updateState(entry.getKey(), entry.getValue(), true);
            }
        }
        Log.v(TAG, "Pending updates sent");
    }

    public SseConnection getSseConnection() {
        return mSseConnection;
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

        Log.v(TAG, "propagating item: " + name + "=" + value);

        final ArrayList<IStateUpdateListener> listeners;
        synchronized (subscriptions) {
            listeners = subscriptions.get(name);
        }

        if (listeners != null) {
            for (IStateUpdateListener l : listeners) {
                l.itemUpdated(name, value);
            }
        }
    }

    private class SseStateUpdateListener implements IStateUpdateListener {
        @Override
        public void itemUpdated(String name, String value) {
            if (mSubscriptions.containsKey(name)) {
                propagateItem(name, value);
            }
            if (mCmdSubscriptions.containsKey(name)) {
                propagateCommand(name, value);
            }
        }
    }

    private class SseConnectionListener implements ISseConnectionListener {
        private SseConnection.Status mLastStatus;

        private final ISubscriptionListener mListener = new ISubscriptionListener() {
            @Override
            public void itemUpdated(String name, String value) {
                propagateItem(name, value);
            }

            @Override
            public void itemInvalid(String name) {
                propagateItem(name, "ITEM NOT FOUND");
            }
        };

        @Override
        public void statusChanged(SseConnection.Status newStatus) {
            if (newStatus == SseConnection.Status.CONNECTED && mLastStatus != SseConnection.Status.CONNECTED) {
                sendCurrentValues();
                fetchCurrentItemsState();
            } else if (mLastStatus == SseConnection.Status.CONNECTED && newStatus != SseConnection.Status.CONNECTED) {
                mValues.clear();
                averagePropagator.clear();
            }
            mLastStatus = newStatus;
        }

        private synchronized void fetchCurrentItemsState() {
            HashSet<String> missingItems = new HashSet<>();
            synchronized (mSubscriptions) {
                for (String item : mSubscriptions.keySet()) {
                    if (!mValues.containsKey(item)) {
                        missingItems.add(item);
                    }
                }
            }

            Log.d(TAG, "Actively fetching items state");
            for (String item : missingItems) {
                mRestClient.getItemState(mServerURL, mListener, item);
            }
        }

    }
}
