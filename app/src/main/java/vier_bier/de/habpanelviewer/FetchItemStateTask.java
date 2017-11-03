package vier_bier.de.habpanelviewer;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

/**
 * Asynchronous task that fetches the value of an openHAB item from the openHAB rest API.
 */
class FetchItemStateTask extends AsyncTask<Set<String>, Void, Void> {
    private String serverUrl;
    private ArrayList<StateListener> listeners;
    private boolean ignoreCertErrors;

    FetchItemStateTask(String url, ArrayList<StateListener> l, boolean ignoreCertificateErrors) {
        serverUrl = url;
        listeners = l;
        ignoreCertErrors = ignoreCertificateErrors;
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
                    if (urlConnection instanceof HttpsURLConnection && ignoreCertErrors) {
                        ((HttpsURLConnection) urlConnection).setSSLSocketFactory(SSEClient.createSslContext(ignoreCertErrors).getSocketFactory());

                        HostnameVerifier hostnameVerifier = new HostnameVerifier() {
                            @Override
                            public boolean verify(String hostname, SSLSession session) {
                                HostnameVerifier hv =
                                        HttpsURLConnection.getDefaultHostnameVerifier();
                                return hv.verify("openhab.org", session);
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
