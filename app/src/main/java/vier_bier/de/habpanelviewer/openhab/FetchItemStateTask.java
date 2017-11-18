package vier_bier.de.habpanelviewer.openhab;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

/**
 * Asynchronous task that fetches the value of an openHAB item from the openHAB rest API.
 */
class FetchItemStateTask extends AsyncTask<String, Void, Void> {
    private String serverUrl;
    private boolean ignoreCertErrors;
    private SubscriptionListener subscriptionListener;

    FetchItemStateTask(String url, boolean ignoreCertificateErrors, SubscriptionListener l) {
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
                final URL url = new URL(serverUrl + "/rest/items/" + itemName + "/state");
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                if (urlConnection instanceof HttpsURLConnection && ignoreCertErrors) {
                    ((HttpsURLConnection) urlConnection).setSSLSocketFactory(ServerConnection.createSslContext(ignoreCertErrors).getSocketFactory());

                    HostnameVerifier hostnameVerifier = new HostnameVerifier() {
                        @Override
                        public boolean verify(String hostname, SSLSession session) {
                            return hostname.equalsIgnoreCase(url.getHost());
                        }
                    };
                    ((HttpsURLConnection) urlConnection).setHostnameVerifier(hostnameVerifier);
                }
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
                Log.e("Habpanelview", "Failed to obtain state for item " + itemName, e);
            }

            subscriptionListener.itemUpdated(itemName, response);

            if (isCancelled()) {
                return null;
            }
        }
        return null;
    }
}
