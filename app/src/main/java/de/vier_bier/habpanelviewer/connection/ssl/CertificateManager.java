package de.vier_bier.habpanelviewer.connection.ssl;

import android.net.http.SslCertificate;
import android.os.Bundle;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

/**
 * SSL related utility methods.
 */
public class CertificateManager {
    private static CertificateManager mInstance;

    private String mTrustStorePasswd = "secret";
    private final ArrayList<ICertChangedListener> mListeners = new ArrayList<>();

    private File mLocalTrustStoreFile;
    private LocalTrustManager mTrustManager;
    private SSLContext mSslContext;

    private final AtomicBoolean mInitialized = new AtomicBoolean();
    private final CountDownLatch mInitLatch = new CountDownLatch(1);

    public static synchronized CertificateManager getInstance() {
        if (mInstance == null) {
            mInstance = new CertificateManager();
        }

        return mInstance;
    }

    public synchronized void setTrustStore(File storeFile, String passwd) throws GeneralSecurityException {
        mTrustStorePasswd = passwd;
        setTrustStore(storeFile);
    }

    public synchronized void setTrustStore(File storeFile) throws GeneralSecurityException {
        if (!mInitialized.getAndSet(true)) {
            try {
                mLocalTrustStoreFile = storeFile;
                if (!mLocalTrustStoreFile.exists()) {
                    throw new IllegalArgumentException("Given file does not exist: " + storeFile.getAbsolutePath());
                }

                System.setProperty("javax.net.ssl.trustStore", mLocalTrustStoreFile.getAbsolutePath());

                createSslContext();
                HttpsURLConnection.setDefaultSSLSocketFactory(mSslContext.getSocketFactory());
            } finally {
                mInitLatch.countDown();
            }
        }
    }

    public synchronized X509TrustManager getTrustManager() {
        try {
            mInitLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            //TODO.vb. return default trust manager?
            return null;
        }

        return mTrustManager;
    }


    public synchronized SSLSocketFactory getSocketFactory() {
        try {
            mInitLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HttpsURLConnection.getDefaultSSLSocketFactory();
        }

        return mSslContext.getSocketFactory();
    }

    public synchronized void addCertificate(SslCertificate certificate) throws GeneralSecurityException, IOException {
        if (!mInitialized.get()) {
            throw new GeneralSecurityException("Certificate Store not yet initialized!");
        }
        KeyStore localTrustStore = loadTrustStore();
        X509Certificate x509Certificate = getX509CertFromSslCertHack(certificate);

        String alias = hashName(x509Certificate.getSubjectX500Principal());
        localTrustStore.setCertificateEntry(alias, x509Certificate);

        saveTrustStore(localTrustStore);

        // reset fields so the keystore gets read again
        mTrustManager = null;
        mSslContext = null;

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
        try {
            mInitLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

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
        try {
            mInitLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

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

    private void copy(InputStream in, File dst) throws IOException {
        try (OutputStream out = new FileOutputStream(dst)) {
            // Transfer bytes from in to out
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
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

