package de.vier_bier.habpanelviewer.ssl;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class MyTrustManager implements X509TrustManager {

    static class LocalStoreX509TrustManager implements X509TrustManager {

        private X509TrustManager trustManager;

        LocalStoreX509TrustManager(KeyStore localTrustStore) {
            try {
                TrustManagerFactory tmf = TrustManagerFactory
                        .getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(localTrustStore);

                trustManager = findX509TrustManager(tmf);
                if (trustManager == null) {
                    throw new IllegalStateException("Couldn't find X509TrustManager");
                }
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            trustManager.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            trustManager.checkServerTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return trustManager.getAcceptedIssuers();
        }
    }

    static X509TrustManager findX509TrustManager(TrustManagerFactory tmf) {
        TrustManager tms[] = tmf.getTrustManagers();
        for (TrustManager tm : tms) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }

        return null;
    }

    private X509TrustManager defaultTrustManager;
    private X509TrustManager localTrustManager;

    private X509Certificate[] acceptedIssuers;

    MyTrustManager(KeyStore localKeyStore) {
        try {
            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);

            defaultTrustManager = findX509TrustManager(tmf);
            if (defaultTrustManager == null) {
                throw new IllegalStateException("Couldn't find X509TrustManager");
            }

            localTrustManager = new LocalStoreX509TrustManager(localKeyStore);

            List<X509Certificate> allIssuers = new ArrayList<>();
            allIssuers.addAll(Arrays.asList(defaultTrustManager.getAcceptedIssuers()));
            allIssuers.addAll(Arrays.asList(localTrustManager.getAcceptedIssuers()));
            acceptedIssuers = allIssuers.toArray(new X509Certificate[allIssuers.size()]);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }


    }

    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        try {
            defaultTrustManager.checkClientTrusted(chain, authType);
        } catch (CertificateException ce) {
            localTrustManager.checkClientTrusted(chain, authType);
        }
    }

    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        try {
            defaultTrustManager.checkServerTrusted(chain, authType);
        } catch (CertificateException ce) {
            localTrustManager.checkServerTrusted(chain, authType);
        }
    }

    public X509Certificate[] getAcceptedIssuers() {
        return acceptedIssuers;
    }

}
