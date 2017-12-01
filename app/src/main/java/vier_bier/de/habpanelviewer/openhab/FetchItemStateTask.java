package vier_bier.de.habpanelviewer.openhab;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;

import vier_bier.de.habpanelviewer.ConnectionUtil;

/**
 * Asynchronous task that fetches the value of an openHAB item from the openHAB rest API.
 */
public class FetchItemStateTask extends AsyncTask<String, Void, Void> {
    private String serverUrl;
    private boolean ignoreCertErrors;
    private SubscriptionListener subscriptionListener;

    public FetchItemStateTask(String url, boolean ignoreCertificateErrors, SubscriptionListener l) {
        serverUrl = url;
        subscriptionListener = l;
        ignoreCertErrors = ignoreCertificateErrors;
    }

    @SafeVarargs
    @Override
    protected final Void doInBackground(String... itemNames) {
        for (String itemName : itemNames) {
            String response = "";

            try {
                HttpURLConnection urlConnection = ConnectionUtil.createUrlConnection(serverUrl + "/rest/items/" + itemName + "/state", ignoreCertErrors);
                try {
                    BufferedInputStream in = new BufferedInputStream(urlConnection.getInputStream());

                    byte[] contents = new byte[1024];

                    int bytesRead;
                    while (!isCancelled() && (bytesRead = in.read(contents)) != -1) {
                        response += new String(contents, 0, bytesRead);
                    }
                } finally {
                    urlConnection.disconnect();
                }

                subscriptionListener.itemUpdated(itemName, response);
            } catch (FileNotFoundException e) {
                subscriptionListener.itemInvalid(itemName);
                Log.e("Habpanelview", "Failed to obtain state for item " + itemName + ". Item not found.");
            } catch (IOException e) {
                subscriptionListener.itemInvalid(itemName);
                Log.e("Habpanelview", "Failed to obtain state for item " + itemName, e);
            }

            if (isCancelled()) {
                return null;
            }
        }
        return null;
    }
}
