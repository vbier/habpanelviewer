package vier_bier.de.habpanelviewer.openhab;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;

import vier_bier.de.habpanelviewer.ssl.ConnectionUtil;

/**
 * Asynchronous task that sets the value of an openHAB item using the openHAB rest API.
 */
public class SetItemStateTask extends AsyncTask<ItemState, Void, Void> {
    private String serverUrl;

    public SetItemStateTask(String url) {
        serverUrl = url;
    }

    @Override
    protected final Void doInBackground(ItemState... itemStates) {
        for (ItemState state : itemStates) {
            try {
                HttpURLConnection urlConnection = ConnectionUtil.createUrlConnection(serverUrl + "/rest/items/" + state.mItemName + "/state");
                try {
                    urlConnection.setRequestMethod("PUT");
                    urlConnection.setDoOutput(true);
                    urlConnection.setRequestProperty("Content-Type", "text/plain");
                    urlConnection.setRequestProperty("Accept", "application/json");

                    OutputStreamWriter osw = new OutputStreamWriter(urlConnection.getOutputStream());
                    osw.write(state.mItemState);
                    osw.flush();
                    osw.close();
                    Log.v("Habpanelview", "set request response: " + urlConnection.getResponseMessage()
                            + "(" + urlConnection.getResponseCode() + ")");
                } finally {
                    urlConnection.disconnect();
                }
            } catch (IOException | GeneralSecurityException e) {
                Log.e("Habpanelview", "Failed to set state for item " + state.mItemName, e);
            }

            if (isCancelled()) {
                return null;
            }
        }
        return null;
    }

}

