package de.vier_bier.habpanelviewer.openhab;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import com.burgstaller.okhttp.AuthenticationCacheInterceptor;
import com.burgstaller.okhttp.CachingAuthenticatorDecorator;
import com.burgstaller.okhttp.DispatchingAuthenticator;
import com.burgstaller.okhttp.basic.BasicAuthenticator;
import com.burgstaller.okhttp.digest.CachingAuthenticator;
import com.burgstaller.okhttp.digest.Credentials;
import com.burgstaller.okhttp.digest.DigestAuthenticator;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.webbitserver.EventSourceConnection;
import org.webbitserver.EventSourceHandler;
import org.webbitserver.WebServer;
import org.webbitserver.WebServers;
import org.webbitserver.handler.authentication.BasicAuthenticationHandler;
import org.webbitserver.handler.authentication.InMemoryPasswords;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;

import static de.vier_bier.habpanelviewer.openhab.SseConnection.Status.CONNECTED;
import static de.vier_bier.habpanelviewer.openhab.SseConnection.Status.FAILURE;
import static de.vier_bier.habpanelviewer.openhab.SseConnection.Status.NOT_CONNECTED;
import static de.vier_bier.habpanelviewer.openhab.SseConnection.Status.UNAUTHORIZED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SseConnectionTest {
    private WebServer mWebServer;
    private Looper mLooper;
    private SseConnection mSseConnection;

    private final String[] mCreds = new String[2];
    private final AtomicBoolean mCertValid = new AtomicBoolean();

    @After
    public void destroyLooper() {
        if (mLooper != null) {
            mLooper.quit();
        }
    }

    @Before
    public void createServer() {
        mWebServer = WebServers.createWebServer(8080);

        HandlerThread ht = new HandlerThread("testThreadedDesign thread");
        ht.start();
        mLooper = ht.getLooper();

        mSseConnection = new SseConnection() {
            @Override
            protected OkHttpClient createConnection() {
                OkHttpClient.Builder builder = new OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS);

                if (mCreds[0] != null || mCreds[1] != null) {
                    final Map<String, CachingAuthenticator> authCache = new ConcurrentHashMap<>();
                    final BasicAuthenticator basicAuthenticator = new BasicAuthenticator(new Credentials(mCreds[0], mCreds[1]));
                    final DigestAuthenticator digestAuthenticator = new DigestAuthenticator(new Credentials(mCreds[0], mCreds[1]));

                    // note that all auth schemes should be registered as lowercase!
                    DispatchingAuthenticator authenticator = new DispatchingAuthenticator.Builder()
                            .with("digest", digestAuthenticator)
                            .with("basic", basicAuthenticator)
                            .build();

                    builder.authenticator(new CachingAuthenticatorDecorator(authenticator, authCache))
                            .addInterceptor(new AuthenticationCacheInterceptor(authCache));
                }

                builder.hostnameVerifier((s, sslSession) -> mCertValid.get());

                return builder.build();
            }

            @Override
            public void postDelayed(Runnable r, long delay) {
                // this is a somewhat ugly workaround because we have no looper in the unit test
                r.run();
            }
        };
    }

    @After
    public void die() throws InterruptedException, ExecutionException {
        mSseConnection.disconnect();
        mWebServer.stop().get();
    }

    @Test
    public void testInitialStatus() {
        assertEquals(SseConnection.Status.NOT_CONNECTED, mSseConnection.getStatus());
    }

    @Test
    public void testNoNetwork() {
        mSseConnection.setServerUrl("http://localhost:8080");
        assertEquals(SseConnection.Status.NO_NETWORK, mSseConnection.getStatus());
    }

    @Test
    public void testMissingConfig() {
        mSseConnection.connected();
        assertEquals(SseConnection.Status.URL_MISSING, mSseConnection.getStatus());
    }

    @Test
    public void testServerNotFound() throws InterruptedException {
        mSseConnection.connected();

        runAndWait(() -> mSseConnection.setServerUrl("http://localhost:8081"), FAILURE);
    }

    @Test
    public void testRestNotFound() throws InterruptedException, ExecutionException {
        mWebServer.start().get();
        mSseConnection.connected();

        runAndWait(() -> mSseConnection.setServerUrl("http://localhost:8080"), FAILURE);
    }

    @Test
    public void testAuth() throws InterruptedException, ExecutionException {
        InMemoryPasswords passwords = new InMemoryPasswords().add("joe", "secret");
        mWebServer.add(new BasicAuthenticationHandler(passwords));
        mWebServer.add("/rest/events", new EmptyEventSourceHandler()).start().get();

        mSseConnection.connected();
        runAndWait(() -> mSseConnection.setServerUrl("http://localhost:8080"), UNAUTHORIZED);

        mCreds[0] = "joe";
        mCreds[1] = "secret";
        runAndWait(() -> mSseConnection.credentialsEntered(null, null), CONNECTED);
    }

    @Test
    public void testHttpConnect() throws InterruptedException, ExecutionException {
        mWebServer.add("/rest/events", new EmptyEventSourceHandler()).start().get();
        mSseConnection.connected();

        runAndWait(() -> mSseConnection.setServerUrl("http://localhost:8080"), CONNECTED);
    }

    @Test
    public void testReConnect() throws InterruptedException, ExecutionException {
        mWebServer.add("/rest/events", new EmptyEventSourceHandler()).start().get();

        mSseConnection.connected();
        runAndWait(() -> mSseConnection.setServerUrl("http://localhost:8080"), CONNECTED);

        runAndWait(() -> mWebServer.stop().get(), NOT_CONNECTED);

        mWebServer = WebServers.createWebServer(8080);
        runAndWait(() -> mWebServer.add("/rest/events", new EmptyEventSourceHandler()).start().get(), CONNECTED, 600);
    }

    private void runAndWait(ExceptionRaisingRunnable r, SseConnection.Status status) throws InterruptedException {
        runAndWait(r, status, 5);
    }

    private void runAndWait(ExceptionRaisingRunnable r, SseConnection.Status status, int seconds) throws InterruptedException {
        AtomicBoolean statusFound = new AtomicBoolean();
        CountDownLatch statusLatch = new CountDownLatch(1);
        final AtomicReference<SseConnection.Status> currentStatus = new AtomicReference<>();
        ISseConnectionListener l = newStatus -> {
            currentStatus.set(newStatus);
            if (status == newStatus && !statusFound.getAndSet(true)) {
                statusLatch.countDown();
            }
        };
        mSseConnection.addListener(l);
        try {
            try {
                r.run();
            } catch (Exception e) {
                throw new AssertionError("Unexpected exception", e);
            }

            statusLatch.await(seconds, TimeUnit.SECONDS);
        } finally {
            mSseConnection.removeListener(l);
        }
        assertTrue("Timeout waiting for status " + status + "(is:  " + currentStatus.get() + ")", statusFound.get());
    }

    @FunctionalInterface
    interface ExceptionRaisingRunnable {
        void run() throws Exception;
    }

    static class EmptyEventSourceHandler implements EventSourceHandler {
        EventSourceConnection mConnection;

        public synchronized void closeConnection() {
            mConnection.close();
        }

        @Override
        public synchronized void onOpen(EventSourceConnection connection) {
            mConnection = connection;
        }

        @Override
        public synchronized void onClose(EventSourceConnection connection) {
            mConnection = null;
        }
    }
}
