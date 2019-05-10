package de.vier_bier.habpanelviewer.openhab;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.IOException;

import de.vier_bier.habpanelviewer.connection.ConnectionStatistics;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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
            OkHttpClient client = ConnectionStatistics.OkHttpClientFactory.getInstance().create();

            MediaType PLAIN
                    = MediaType.get("text/plain; charset=utf-8");

            RequestBody body = RequestBody.create(PLAIN, item.mItemState);
            Request request = new Request.Builder()
                    .url(item.mServerURL + "/rest/items/" + item.mItemName + "/state")
                    .put(body)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                Log.v(TAG, "set " + item.mItemName + " request response: " + response.body().string()
                        + "(" + response.code() + ")");
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to set state for item " + item.mItemName, e);
        }
    }

    private void getRequest(ItemSubscription item) {
        String itemName = item.mItemName;
        ISubscriptionListener listener = item.mListener;

        OkHttpClient client = ConnectionStatistics.OkHttpClientFactory.getInstance().create();
        Request request = new Request.Builder()
                .url(item.mServerURL + "/rest/items/" + itemName + "/state")
                .build();

        try (Response response = client.newCall(request).execute()) {
            listener.itemUpdated(itemName, response.body().string());
        } catch (IOException e) {
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
