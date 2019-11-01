package de.vier_bier.habpanelviewer.connection.ssl;

import android.net.http.SslCertificate;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import org.jetbrains.annotations.TestOnly;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

/**
 * SSL Certificate Manager.
 *
 * Sets up an app specific trust store and makes it default for all HTTPS requests.
 * Allows to add certificates to this store to be available on for this app on subsequent starts.
 */
public class CertificateManager {
    private static final String TAG = "HPV-CertificateManager";
    private static CertificateManager mInstance;

    private String mTrustStorePasswd = "secret";
    private final ArrayList<ICertChangedListener> mListeners = new ArrayList<>();

    private File mLocalTrustStoreFile;
    private LocalTrustManager mTrustManager;
    private SSLContext mSslContext;

    private boolean mInitSuccess;

    @TestOnly
    public static synchronized CertificateManager get(File certFile, String passwd) throws GeneralSecurityException {
        CertificateManager cm = new CertificateManager();
        cm.setTrustStore(certFile, passwd);
        return cm;
    }

    public static synchronized CertificateManager getInstance() {
        if (mInstance == null) {
            mInstance = new CertificateManager();

            File certFile = new File(Environment.getDataDirectory()
                    + "/data/de.vier_bier.habpanelviewer/files/localTrustStore.bks");

            try {
                mInstance.setTrustStore(certFile);
            } catch (GeneralSecurityException e) {
                Log.e(TAG, "Certificate store initialization failed", e);
            }
        }

        return mInstance;
    }

    @TestOnly
    private synchronized void setTrustStore(File storeFile, String passwd) throws GeneralSecurityException {
        mTrustStorePasswd = passwd;
        setTrustStore(storeFile);
    }

    public boolean isInitialized() {
        return mInitSuccess;
    }

    private synchronized void setTrustStore(File storeFile) throws GeneralSecurityException {
        mLocalTrustStoreFile = storeFile;
        if (!mLocalTrustStoreFile.exists()) {
            throw new IllegalArgumentException("Given file does not exist: " + storeFile.getAbsolutePath());
        }

        System.setProperty("javax.net.ssl.trustStore", mLocalTrustStoreFile.getAbsolutePath());

        createSslContext();
        HttpsURLConnection.setDefaultSSLSocketFactory(mSslContext.getSocketFactory());
        mInitSuccess = true;
    }

    public synchronized X509TrustManager getTrustManager() {
        return mTrustManager;
    }


    public synchronized SSLSocketFactory getSocketFactory() {
        return mSslContext.getSocketFactory();
    }

    public synchronized void addCertificate(SslCertificate certificate) throws GeneralSecurityException, IOException {
        KeyStore localTrustStore = loadTrustStore();
        X509Certificate x509Certificate = getX509CertFromSslCertHack(certificate);

        String alias = hashName(x509Certificate.getSubjectX500Principal());
        localTrustStore.setCertificateEntry(alias, x509Certificate);

        saveTrustStore(localTrustStore);

        // notify listeners
        synchronized (mListeners) {
            for (ICertChangedListener l : mListeners) {
                l.certAdded();
            }
        }
    }

    public void addCertListener(ICertChangedListener l) {
        synchronized (mListeners) {
            if (!mListeners.contains(l)) {
                mListeners.add(l);
            }
        }
    }

    public void removeCertListener(ICertChangedListener l) {
        synchronized (mListeners) {
            mListeners.remove(l);
        }
    }

    public synchronized boolean isTrusted(X509Certificate cert) {
        if (mTrustManager == null) {
            KeyStore trustStore = loadTrustStore();
            mTrustManager = new LocalTrustManager(trustStore);
        }

        try {
            mTrustManager.checkClientTrusted(new X509Certificate[]{cert}, "generic");
        } catch (CertificateException e) {
            return false;
        }

        return true;
    }

    public synchronized boolean isTrusted(SslCertificate cert) {
        if (mTrustManager == null) {
            KeyStore trustStore = loadTrustStore();
            mTrustManager = new LocalTrustManager(trustStore);
        }

        try {
            mTrustManager.checkClientTrusted(new X509Certificate[]{getX509CertFromSslCertHack(cert)}, "generic");
        } catch (CertificateException e) {
            return false;
        }

        return true;
    }

    private synchronized void createSslContext() throws GeneralSecurityException {
        if (mSslContext == null) {
            if (mTrustManager == null) {
                KeyStore trustStore = loadTrustStore();
                mTrustManager = new LocalTrustManager(trustStore);
            }

            TrustManager[] tms = new TrustManager[]{mTrustManager};

            mSslContext = SSLContext.getInstance("TLS");
            mSslContext.init(null, tms, null);
        }
    }

    private KeyStore loadTrustStore() {
        try {
            KeyStore localTrustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (InputStream in = new FileInputStream(mLocalTrustStoreFile)) {
                localTrustStore.load(in, mTrustStorePasswd.toCharArray());
            }

            return localTrustStore;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private X509Certificate getX509CertFromSslCertHack(SslCertificate sslCert) {
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

    private void saveTrustStore(KeyStore localTrustStore)
            throws IOException, GeneralSecurityException {
        FileOutputStream out = new FileOutputStream(mLocalTrustStoreFile);
        localTrustStore.store(out, mTrustStorePasswd.toCharArray());

        // reset fields so the keystore gets read again
        mTrustManager = null;
        mSslContext = null;

        createSslContext();
    }

    private String hashName(X500Principal principal) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(
                    principal.getEncoded());

            return Integer.toString(leInt(digest), 16);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private int leInt(byte[] bytes) {
        int offset = 0;
        return ((bytes[offset++] & 0xff))
                | ((bytes[offset++] & 0xff) << 8)
                | ((bytes[offset++] & 0xff) << 16)
                | ((bytes[offset] & 0xff) << 24);
    }

    public interface ICertChangedListener {
        void certAdded();
    }
}

