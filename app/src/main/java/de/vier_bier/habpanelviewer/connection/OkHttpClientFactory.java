package de.vier_bier.habpanelviewer.connection;

import com.burgstaller.okhttp.AuthenticationCacheInterceptor;
import com.burgstaller.okhttp.CachingAuthenticatorDecorator;
import com.burgstaller.okhttp.DispatchingAuthenticator;
import com.burgstaller.okhttp.basic.BasicAuthenticator;
import com.burgstaller.okhttp.digest.CachingAuthenticator;
import com.burgstaller.okhttp.digest.Credentials;
import com.burgstaller.okhttp.digest.DigestAuthenticator;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import de.vier_bier.habpanelviewer.connection.ssl.CertificateManager;
import okhttp3.OkHttpClient;

public class OkHttpClientFactory {
    private static OkHttpClientFactory ourInstance;

    public Credentials mCred = null;
    private String mHost;
    private String mRealm;

    public static synchronized OkHttpClientFactory getInstance() {
        if (ourInstance == null) {
            ourInstance = new OkHttpClientFactory();
        }
        return ourInstance;
    }

    public OkHttpClient create() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS);

        builder.sslSocketFactory(CertificateManager.getInstance().getSocketFactory(),
                CertificateManager.getInstance().getTrustManager())
                .hostnameVerifier((s, session) -> {
                    try {
                        Certificate[] certificates = session.getPeerCertificates();
                        for (Certificate certificate : certificates) {
                            if (!(certificate instanceof X509Certificate)) {
                                return false;
                            }
                            if (CertificateManager.getInstance().isTrusted((X509Certificate) certificate)) {
                                return true;
                            }
                        }
                    } catch (SSLException e) {
                        // return false;
                    }
                    return false;
                });

        if (mCred != null) {
            // create a copy so it can not be nulled again
            Credentials copy = new Credentials(mCred.getUserName(), mCred.getPassword());

            final Map<String, CachingAuthenticator> authCache = new ConcurrentHashMap<>();
            final BasicAuthenticator basicAuthenticator = new BasicAuthenticator(copy);
            final DigestAuthenticator digestAuthenticator = new DigestAuthenticator(copy);

            // note that all auth schemes should be registered as lowercase!
            DispatchingAuthenticator authenticator = new DispatchingAuthenticator.Builder()
                    .with("digest", digestAuthenticator)
                    .with("basic", basicAuthenticator)
                    .build();

            builder.authenticator(new CachingAuthenticatorDecorator(authenticator, authCache))
                    .addInterceptor(new AuthenticationCacheInterceptor(authCache));
        }

        return builder.build();
    }

    public void setAuth(String user, String pass) {
        mCred = new Credentials(user, pass);
    }

    public void removeAuth() {
        mCred = null;
    }

    public void setHost(String host) {
        mHost = host;
    }

    public void setRealm(String realm) {
        mRealm = realm;
    }

    public String getHost() {
        return mHost;
    }

    public String getRealm() {
        return mRealm;
    }
}
