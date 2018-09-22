package de.vier_bier.habpanelviewer.openhab;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;

import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;

/**
 * Asynchronous task that sets the value of an openHAB item using the openHAB rest API.
 */
class SetItemStateTask extends AsyncTask<ItemState, Void, Void> {
    private static final String TAG = "HPV-SetItemStateTask";

    private final String serverUrl;

    SetItemStateTask(String url) {
        serverUrl = url;
    }

    @Override
    protected final Void doInBackground(ItemState... itemStates) {
        for (ItemState state : itemStates) {
            try {
                HttpURLConnection urlConnection = ConnectionUtil.getInstance().createUrlConnection(serverUrl + "/rest/items/" + state.mItemName + "/state");
                try {
                    urlConnection.setRequestMethod("PUT");
                    urlConnection.setDoOutput(true);
                    urlConnection.setRequestProperty("Content-Type", "text/plain");
                    urlConnection.setRequestProperty("Accept", "application/json");

                    OutputStreamWriter osw = new OutputStreamWriter(urlConnection.getOutputStream());
                    osw.write(state.mItemState);
                    osw.flush();
                    osw.close();
                    Log.v(TAG, "set request response: " + urlConnection.getResponseMessage()
                            + "(" + urlConnection.getResponseCode() + ")");
                } finally {
                    urlConnection.disconnect();
                }
            } catch (IOException | GeneralSecurityException e) {
                Log.e(TAG, "Failed to set state for item " + state.mItemName, e);
            }

            if (isCancelled()) {
                return null;
            }
        }
        return null;
    }

}

