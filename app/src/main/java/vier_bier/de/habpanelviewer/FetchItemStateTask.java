package vier_bier.de.habpanelviewer;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Set;

/**
 * Asynchronous task that fetches the value of an openHAB item from the openHAB rest API.
 */
class FetchItemStateTask extends AsyncTask<Set<String>, Void, Void> {
    private String serverUrl;
    private ArrayList<StateListener> listeners;

    FetchItemStateTask(String url, ArrayList<StateListener> l) {
        serverUrl = url;
        listeners = l;
    }

    @SafeVarargs
    @Override
    protected final Void doInBackground(Set<String>... itemNames) {
        for (Set<String> names : itemNames) {
            for (String itemName : names) {
                String response = "";

                try {
                    URL url = new URL(serverUrl + "/rest/items/" + itemName + "/state");
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
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
                } catch (IOException e) {
                    Log.e("Habpanelview", "Failed to obtain value for flash Item!", e);
                }

                for (StateListener l : listeners) {
                    l.updateState(itemName, response);
                }

                if (isCancelled()) {
                    return null;
                }
            }
        }
        return null;
    }
}
