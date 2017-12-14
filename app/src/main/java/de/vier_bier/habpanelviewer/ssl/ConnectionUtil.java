package de.vier_bier.habpanelviewer.ssl;

import android.content.Context;
import android.net.http.SslCertificate;
import android.os.Bundle;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.security.auth.x500.X500Principal;

import de.vier_bier.habpanelviewer.R;

/**
 * SSL related utility methods.
 */
public class ConnectionUtil {
    private static File localTrustStoreFile;
    private static final String TRUSTSTORE_PASSWORD = "secret";

    private static MyTrustManager mTrustManager;
    private static SSLContext mSslContext;

    public static synchronized void initialize(Context ctx) throws GeneralSecurityException, IOException {
        localTrustStoreFile = new File(ctx.getFilesDir(), "localTrustStore.bks");
        if (!localTrustStoreFile.exists()) {
            try (InputStream in = ctx.getResources().openRawResource(R.raw.mytruststore)) {
                copy(in, localTrustStoreFile);
            }
        }

        System.setProperty("javax.net.ssl.trustStore", localTrustStoreFile.getAbsolutePath());

        SSLContext sslContext = createSslContext();
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
    }

    public static synchronized void addCertificate(SslCertificate certificate) throws GeneralSecurityException, IOException {
        KeyStore localTrustStore = loadTrustStore();
        X509Certificate x509Certificate = getX509CertFromSslCertHack(certificate);

        String alias = hashName(x509Certificate.getSubjectX500Principal());
        localTrustStore.setCertificateEntry(alias, x509Certificate);

        saveTrustStore(localTrustStore);

        // reset fields so the keystore gets read again
        mTrustManager = null;
        mSslContext = null;
    }

    public static synchronized HttpURLConnection createUrlConnection(final String urlStr) throws IOException, GeneralSecurityException {
        final URL url = new URL(urlStr);
        SSLContext sslCtx = createSslContext();
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        if (urlConnection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) urlConnection).setSSLSocketFactory(sslCtx.getSocketFactory());

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

    public static synchronized boolean isTrusted(SslCertificate cert) {
        if (mTrustManager == null) {
            KeyStore trustStore = loadTrustStore();
            mTrustManager = new MyTrustManager(trustStore);
        }

        try {
            mTrustManager.checkClientTrusted(new X509Certificate[]{getX509CertFromSslCertHack(cert)}, "generic");
        } catch (CertificateException e) {
            return false;
        }

        return true;
    }

    public static synchronized SSLContext createSslContext() throws GeneralSecurityException {
        if (mSslContext != null) {
            return mSslContext;
        }

        if (mTrustManager == null) {
            KeyStore trustStore = loadTrustStore();
            mTrustManager = new MyTrustManager(trustStore);
        }

        TrustManager[] tms = new TrustManager[]{mTrustManager};

        mSslContext = SSLContext.getInstance("TLS");
        mSslContext.init(null, tms, null);

        return mSslContext;
    }

    private static KeyStore loadTrustStore() {
        try {
            KeyStore localTrustStore = KeyStore.getInstance("BKS");
            InputStream in = new FileInputStream(localTrustStoreFile);
            try {
                localTrustStore.load(in, TRUSTSTORE_PASSWORD.toCharArray());
            } finally {
                in.close();
            }

            return localTrustStore;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void copy(InputStream in, File dst) throws IOException {
        try (OutputStream out = new FileOutputStream(dst)) {
            // Transfer bytes from in to out
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private static X509Certificate getX509CertFromSslCertHack(SslCertificate sslCert) {
        X509Certificate x509Certificate;

        Bundle bundle = SslCertificate.saveState(sslCert);
        byte[] bytes = bundle.getByteArray("x509-certificate");

        if (bytes == null) {
            x509Certificate = null;
        } else {
            try {
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                Certificate cert = certFactory.generateCertificate(new ByteArrayInputStream(bytes));
                x509Certificate = (X509Certificate) cert;
            } catch (CertificateException e) {
                x509Certificate = null;
            }
        }

        return x509Certificate;
    }

    private static void saveTrustStore(KeyStore localTrustStore)
            throws IOException, GeneralSecurityException {
        FileOutputStream out = new FileOutputStream(localTrustStoreFile);
        localTrustStore.store(out, TRUSTSTORE_PASSWORD.toCharArray());
    }

    private static String hashName(X500Principal principal) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(
                    principal.getEncoded());

            String result = Integer.toString(leInt(digest), 16);
            if (result.length() > 8) {
                StringBuffer buff = new StringBuffer();
                int padding = 8 - result.length();
                for (int i = 0; i < padding; i++) {
                    buff.append("0");
                }
                buff.append(result);

                return buff.toString();
            }

            return result;
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static int leInt(byte[] bytes) {
        int offset = 0;
        return ((bytes[offset++] & 0xff) << 0)
                | ((bytes[offset++] & 0xff) << 8)
                | ((bytes[offset++] & 0xff) << 16)
                | ((bytes[offset] & 0xff) << 24);
    }

}

