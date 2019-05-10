package de.vier_bier.habpanelviewer.openhab;

import com.burgstaller.okhttp.AuthenticationCacheInterceptor;
import com.burgstaller.okhttp.CachingAuthenticatorDecorator;
import com.burgstaller.okhttp.DispatchingAuthenticator;
import com.burgstaller.okhttp.basic.BasicAuthenticator;
import com.burgstaller.okhttp.digest.CachingAuthenticator;
import com.burgstaller.okhttp.digest.Credentials;
import com.burgstaller.okhttp.digest.DigestAuthenticator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.webbitserver.EventSourceConnection;
import org.webbitserver.EventSourceHandler;
import org.webbitserver.WebServer;
import org.webbitserver.WebServers;
import org.webbitserver.handler.ServerHeaderHandler;
import org.webbitserver.handler.StringHttpHandler;
import org.webbitserver.handler.authentication.BasicAuthenticationHandler;
import org.webbitserver.handler.authentication.InMemoryPasswords;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import de.vier_bier.habpanelviewer.connection.ssl.CertificateManager;
import okhttp3.OkHttpClient;

import static de.vier_bier.habpanelviewer.openhab.SseConnection.Status.CERTIFICATE_ERROR;
import static de.vier_bier.habpanelviewer.openhab.SseConnection.Status.CONNECTED;
import static de.vier_bier.habpanelviewer.openhab.SseConnection.Status.FAILURE;
import static de.vier_bier.habpanelviewer.openhab.SseConnection.Status.RECONNECTING;
import static de.vier_bier.habpanelviewer.openhab.SseConnection.Status.UNAUTHORIZED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SseConnectionTest {
    private WebServer mWebServer;
    private SseConnection mSseConnection;
    private String[] mCreds = new String[2];
    private AtomicBoolean mCertValid = new AtomicBoolean();

    @Before
    public void createServer() {
        mWebServer = WebServers.createWebServer(8080);
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
        };
    }

    @After
    public void die() throws InterruptedException, ExecutionException {
        System.out.println("SseConnectionTest.die");
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
        runAndWait(() -> mSseConnection.credentialsEntered(), CONNECTED);
    }

    @Test
    public void testHttpConnect() throws InterruptedException, ExecutionException {
        mWebServer.add("/rest/events", new EmptyEventSourceHandler()).start().get();
        mSseConnection.connected();

        runAndWait(() -> mSseConnection.setServerUrl("http://localhost:8080"), CONNECTED);
    }

    @Test
    public void testCertError() throws IOException, ExecutionException, InterruptedException, GeneralSecurityException {
        try (InputStream keystore = getClass().getResourceAsStream("/keystore")) {
            mWebServer.setupSsl(keystore, "webbit")
                    .add(new ServerHeaderHandler("My Server"))
                    .add(new StringHttpHandler("text/plain", "body"));

            mWebServer.add("/rest/events", new EmptyEventSourceHandler()).start().get();

            runAndWait(() -> {
                mSseConnection.connected();
                mSseConnection.setServerUrl("https://localhost:8080");
            }, CERTIFICATE_ERROR);

            String storeFile = getClass().getResource("/keystore").getFile();
            CertificateManager.getInstance().setTrustStore(new File(storeFile), "webbit");
            mCertValid.set(true);

            runAndWait(() -> mSseConnection.connect(), CONNECTED);
        }
    }

    @Test
    public void testReConnect() throws InterruptedException, ExecutionException {
        mWebServer.add("/rest/events", new EmptyEventSourceHandler()).start().get();

        mSseConnection.connected();
        runAndWait(() -> mSseConnection.setServerUrl("http://localhost:8080"), CONNECTED);

        runAndWait(() -> mWebServer.stop().get(), RECONNECTING);

        mWebServer = WebServers.createWebServer(8080);
        mWebServer.add("/rest/events", new EmptyEventSourceHandler()).start().get();
        runAndWait(() -> mSseConnection.setServerUrl("http://localhost:8080"), CONNECTED);
    }

    private void runAndWait(ExceptionRaisingRunnable r, SseConnection.Status status) throws InterruptedException {
        runAndWait(r, status, 5);
    }

    private void runAndWait(ExceptionRaisingRunnable r, SseConnection.Status status, int seconds) throws InterruptedException {
        AtomicBoolean statusFound = new AtomicBoolean();
        CountDownLatch statusLatch = new CountDownLatch(1);
        ISseConnectionListener l = newStatus -> {
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
        assertTrue("Timeout waiting for status " + status, statusFound.get());
    }

    @FunctionalInterface
    interface ExceptionRaisingRunnable {
        void run() throws Exception;
    }

    class EmptyEventSourceHandler implements EventSourceHandler {
        @Override
        public void onOpen(EventSourceConnection connection) {
        }

        @Override
        public void onClose(EventSourceConnection connection) {
        }
    }
}
