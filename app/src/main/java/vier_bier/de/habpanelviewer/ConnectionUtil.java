package vier_bier.de.habpanelviewer;

import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * SSL related utility methods.
 */
public class ConnectionUtil {
    public static HttpURLConnection createUrlConnection(final String urlStr, final boolean ignoreSSLErrors) throws MalformedURLException, SSLException, IOException {
        final URL url = new URL(urlStr);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        if (urlConnection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) urlConnection).setSSLSocketFactory(ConnectionUtil.createSslContext(ignoreSSLErrors).getSocketFactory());

            HostnameVerifier hostnameVerifier = new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return hostname.equalsIgnoreCase(url.getHost());
                }
            };
            ((HttpsURLConnection) urlConnection).setHostnameVerifier(hostnameVerifier);
        }
        urlConnection.setConnectTimeout(200);

        return urlConnection;
    }

    public static SSLContext createSslContext(boolean ignoreCertificateErrors) {
        SSLContext sslContext;
        try {
            TrustManager[] trustAllCerts = null;

            if (ignoreCertificateErrors) {
                trustAllCerts = new TrustManager[]{new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                        Log.v("TrustManager", "checkClientTrusted");
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        Log.v("TrustManager", "getAcceptedIssuers");
                        return null;
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        Log.v("TrustManager", "checkServerTrusted");
                    }
                }};
            }

            sslContext = SSLContext.getInstance("TLS");
            try {
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            } catch (KeyManagementException e) {
                return null;
            }
        } catch (NoSuchAlgorithmException e1) {
            return null;
        }

        return sslContext;
    }
}
