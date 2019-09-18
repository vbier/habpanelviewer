package de.vier_bier.habpanelviewer.connection;

import android.content.Context;
import android.content.res.Resources;

import com.burgstaller.okhttp.AuthenticationCacheInterceptor;
import com.burgstaller.okhttp.CachingAuthenticatorDecorator;
import com.burgstaller.okhttp.DispatchingAuthenticator;
import com.burgstaller.okhttp.basic.BasicAuthenticator;
import com.burgstaller.okhttp.digest.CachingAuthenticator;
import com.burgstaller.okhttp.digest.Credentials;
import com.burgstaller.okhttp.digest.DigestAuthenticator;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.connection.ssl.CertificateManager;
import de.vier_bier.habpanelviewer.db.Credential;
import de.vier_bier.habpanelviewer.db.CredentialManager;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;
import okhttp3.OkHttpClient;

/**
 * Holds information about online/offline times.
 */
public class ConnectionStatistics {
    private final Context mCtx;
    private final long mStartTime = System.currentTimeMillis();
    private State mState = State.DISCONNECTED;

    private long mLastOnlineTime = -1;
    private long mLastOfflineTime = mStartTime;
    private long mOfflinePeriods = 0;
    private long mOfflineMillis = 0;
    private long mOfflineMaxMillis = 0;
    private long mOfflineAverage = 0;
    private long mOnlinePeriods = 0;
    private long mOnlineMillis = 0;
    private long mOnlineMaxMillis = 0;
    private long mOnlineAverage = 0;

    public ConnectionStatistics(Context context) {
        mCtx = context;
        EventBus.getDefault().register(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        long now = System.currentTimeMillis();

        long currentOnlineTime = mState == State.CONNECTED ? now - mLastOnlineTime : 0;
        long currentOfflineTime = mState == State.DISCONNECTED ? now - mLastOfflineTime : 0;
        long averageOnlineTime = mState == State.CONNECTED ? (mOnlineAverage * mOnlinePeriods + currentOnlineTime) / (mOnlinePeriods + 1) : mOnlineAverage;
        long averageOfflineTime = mState == State.DISCONNECTED ? (mOfflineAverage * mOfflinePeriods + currentOfflineTime) / (mOfflinePeriods + 1) : mOfflineAverage;

        status.set(mCtx.getString(R.string.connectionStatistics),
                mCtx.getString(R.string.connectionDetails,
                        toDuration(now - mStartTime),
                        toDuration(mOnlineMillis + currentOnlineTime),
                        (mOnlinePeriods + (mState == State.CONNECTED ? 1 : 0)),
                        toDuration(Math.max(currentOnlineTime, mOnlineMaxMillis)),
                        toDuration(averageOnlineTime),
                        toDuration(mOfflineMillis + currentOfflineTime),
                        (mOfflinePeriods + (mState == State.DISCONNECTED ? 1 : 0)),
                        toDuration(Math.max(currentOfflineTime, mOfflineMaxMillis)),
                        toDuration(averageOfflineTime)));
    }

    private String toDuration(long durationMillis) {
        if (durationMillis < 1000) {
            return "-";
        }

        Resources res = mCtx.getResources();

        String retVal = "";

        // days
        if (durationMillis > 86400000) {
            long days = durationMillis / 86400000;

            retVal += res.getQuantityString(R.plurals.days, (int) days, days);
            durationMillis %= 86400000;
        }

        // hours
        if (durationMillis > 3600000) {
            long hours = durationMillis / 3600000;

            retVal += res.getQuantityString(R.plurals.hours, (int) hours, hours);
            durationMillis %= 3600000;
        }

        // minutes
        if (durationMillis > 60000) {
            long minutes = durationMillis / 60000;

            retVal += res.getQuantityString(R.plurals.minutes, (int) minutes, minutes);
            durationMillis %= 60000;
        }

        // seconds
        if (durationMillis > 1000) {
            long seconds = durationMillis / 1000;

            retVal += res.getQuantityString(R.plurals.seconds, (int) seconds, seconds);
        }

        return retVal.substring(0, retVal.length() - 1);
    }

    public synchronized void disconnected() {
        if (mState == State.CONNECTED) {
            mLastOfflineTime = System.currentTimeMillis();

            long duration = mLastOfflineTime - mLastOnlineTime;
            mOnlineMillis += duration;

            if (duration > mOnlineMaxMillis) {
                mOnlineMaxMillis = duration;
            }

            mOnlineAverage = (mOnlineAverage * mOnlinePeriods + duration) / ++mOnlinePeriods;
            mState = State.DISCONNECTED;
        }
    }

    public synchronized void connected() {
        if (mState == State.DISCONNECTED) {
            mLastOnlineTime = System.currentTimeMillis();

            if (mLastOnlineTime > -1) {
                long duration = mLastOnlineTime - mLastOfflineTime;
                mOfflineMillis += duration;

                if (duration > mOfflineMaxMillis) {
                    mOfflineMaxMillis = duration;
                }

                mOfflineAverage = (mOfflineAverage * mOfflinePeriods + duration) / ++mOfflinePeriods;
                mState = State.CONNECTED;
            }
        }
    }

    public void terminate() {
        EventBus.getDefault().unregister(this);
    }

    private enum State {
        CONNECTED, DISCONNECTED
    }

    public static class OkHttpClientFactory implements CredentialManager.CredentialsListener {
        private static OkHttpClientFactory ourInstance;

        private String mRestUser;
        private String mRestPasswd;

        public static synchronized OkHttpClientFactory getInstance() {
            if (ourInstance == null) {
                ourInstance = new OkHttpClientFactory();
            }
            return ourInstance;
        }

        public OkHttpClient create() {
            return create(mRestUser, mRestPasswd);
        }

        OkHttpClient create(final String user, final String passwd) {
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

            if (user != null || passwd != null) {
                final Map<String, CachingAuthenticator> authCache = new ConcurrentHashMap<>();
                final BasicAuthenticator basicAuthenticator = new BasicAuthenticator(new Credentials(user, passwd));
                final DigestAuthenticator digestAuthenticator = new DigestAuthenticator(new Credentials(user, passwd));

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

        @Override
        public void credentialsEntered() {
            Credential c = CredentialManager.getInstance().getRestCredentials();
            mRestPasswd = c == null ? null : c.getPasswd();
            mRestUser = c == null ? null : c.getUser();
        }
    }
}
