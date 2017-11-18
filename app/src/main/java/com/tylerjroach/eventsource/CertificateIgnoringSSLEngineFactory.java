package com.tylerjroach.eventsource;

import javax.net.ssl.SSLEngine;

import vier_bier.de.habpanelviewer.openhab.ServerConnection;

/**
 * Workaround for a bug in the eventsource implementation.
 */
public class CertificateIgnoringSSLEngineFactory extends SSLEngineFactory {
    private boolean mIgnoreCertErrors;

    public CertificateIgnoringSSLEngineFactory(boolean ignoreCertErrors) {
        mIgnoreCertErrors = ignoreCertErrors;
    }

    @Override
    SSLEngine GetNewSSLEngine() {
        return ServerConnection.createSslContext(mIgnoreCertErrors).createSSLEngine();
    }
}
