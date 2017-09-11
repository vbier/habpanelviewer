package vier_bier.de.habpanelviewer;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;

/**
 * Created by volla on 10.09.17.
 */
public class FetchItemStateTask extends AsyncTask<Set<String>, Void, Void> {
    private String serverUrl;
    private StateListener listener;

    public FetchItemStateTask(String url, StateListener l) {
        serverUrl = url;
        listener = l;
    }

    @Override
    protected Void doInBackground(Set<String>... itemNames) {
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

                listener.updateState(itemName, response);

                if (isCancelled()) {
                    return null;
                }
            }
        }
        return null;
    }
}
