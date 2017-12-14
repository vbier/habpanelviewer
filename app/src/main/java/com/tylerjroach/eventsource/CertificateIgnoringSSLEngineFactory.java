package com.tylerjroach.eventsource;

import java.security.GeneralSecurityException;

import javax.net.ssl.SSLEngine;

import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;

/**
 * Workaround for a bug in the eventsource implementation.
 */
public class CertificateIgnoringSSLEngineFactory extends SSLEngineFactory {
    public CertificateIgnoringSSLEngineFactory() {
    }

    @Override
    SSLEngine GetNewSSLEngine() {
        try {
            return ConnectionUtil.createSslContext().createSSLEngine();
        } catch (GeneralSecurityException e) {
            return super.GetNewSSLEngine();
        }
    }
}
