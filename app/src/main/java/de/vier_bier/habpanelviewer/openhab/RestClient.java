package de.vier_bier.habpanelviewer.openhab;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;

import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;

public class RestClient extends HandlerThread {
    private static final String TAG = "HPV-RestClient";

    private static final int SET_ID = 213;
    private static final int GET_ID = 214;

    private Handler mWorkerHandler;

    RestClient() {
        super("RestClient");
        start();
    }

    void getItemState(String serverURL, ISubscriptionListener l, String itemName) {
        mWorkerHandler.obtainMessage(GET_ID, new ItemSubscription(serverURL, l, itemName)).sendToTarget();
    }

    void setItemState(String serverURL, ItemState itemState) {
        mWorkerHandler.obtainMessage(SET_ID, new ItemModification(serverURL, itemState)).sendToTarget();
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();

        mWorkerHandler = new Handler(getLooper(), msg -> {
            switch (msg.what) {
                case GET_ID: getRequest((ItemSubscription) msg.obj);
                    break;
                case SET_ID: putRequest((ItemModification) msg.obj);
                    break;
            }

            return true;
        });
    }

    private void putRequest(ItemModification item) {
        try {
            HttpURLConnection urlConnection = ConnectionUtil.getInstance().createUrlConnection(item.mServerURL + "/rest/items/" + item.mItemName + "/state");
            try {
                urlConnection.setRequestMethod("PUT");
                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty("Content-Type", "text/plain");
                urlConnection.setRequestProperty("Accept", "application/json");

                OutputStreamWriter osw = new OutputStreamWriter(urlConnection.getOutputStream());
                osw.write(item.mItemState);
                osw.flush();
                osw.close();
                Log.v(TAG, "set " + item.mItemName + " request response: " + urlConnection.getResponseMessage()
                        + "(" + urlConnection.getResponseCode() + ")");
            } finally {
                urlConnection.disconnect();
            }
        } catch (IOException | GeneralSecurityException e) {
            Log.e(TAG, "Failed to set state for item " + item.mItemName, e);
        }
    }

    private void getRequest(ItemSubscription item) {
        String itemName = item.mItemName;
        ISubscriptionListener listener = item.mListener;

        StringBuilder response = new StringBuilder();
        try {
            HttpURLConnection urlConnection = ConnectionUtil.getInstance().createUrlConnection(item.mServerURL + "/rest/items/" + itemName + "/state");
            try {
                BufferedInputStream in = new BufferedInputStream(urlConnection.getInputStream());

                byte[] contents = new byte[1024];

                int bytesRead;
                while (!isInterrupted() && (bytesRead = in.read(contents)) != -1) {
                    response.append(new String(contents, 0, bytesRead));
                }
            } finally {
                urlConnection.disconnect();
            }

            listener.itemUpdated(itemName, response.toString());
        } catch (FileNotFoundException e) {
            listener.itemInvalid(itemName);
            Log.e(TAG, "Failed to obtain state for item " + itemName + ". Item not found.");
        } catch (IOException | GeneralSecurityException e) {
            listener.itemInvalid(itemName);
            Log.e(TAG, "Failed to obtain state for item " + itemName, e);
        }
    }

    private class ItemSubscription {
        final String mServerURL;
        final ISubscriptionListener mListener;
        final String mItemName;

        ItemSubscription(String serverURL, ISubscriptionListener l, String itemName) {
            mServerURL = serverURL;
            mListener = l;
            mItemName = itemName;
        }
    }

    private class ItemModification {
        final String mServerURL;
        final String mItemName;
        final String mItemState;

        ItemModification(String serverURL, ItemState itemState) {
            mServerURL = serverURL;
            mItemState = itemState.mItemState;
            mItemName = itemState.mItemName;
        }
    }
}
