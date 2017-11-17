package vier_bier.de.habpanelviewer.openhab;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * mDNS discovery for openHAB.
 */
public class ServerDiscovery {
    private static final String TAG = "ServerDiscovery";

    private final NsdManager mNsdManager;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private final AtomicReference<String> mUrl = new AtomicReference<>();

    public ServerDiscovery(NsdManager nsdManager) {
        mNsdManager = nsdManager;
    }

    public synchronized void discover(final DiscoveryListener l, final boolean discoverHttp, final boolean discoverHttps) {
        if (mDiscoveryListener != null) {
            return;
        }

        Log.v(TAG, "starting discovery...");
        mUrl.set(null);

        ArrayList<String> types = new ArrayList<>();
        if (discoverHttps) {
            types.add("_openhab-server-ssl._tcp");
        } else if (discoverHttp) {
            types.add("_openhab-server._tcp");
        }

        try {
            for (String serviceType : types) {
                try {
                    Log.v(TAG, "starting discovery for " + serviceType + "...");
                    mDiscoveryListener = new NsdDiscoveryListener();
                    mNsdManager.discoverServices(
                            serviceType, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);

                    synchronized (mUrl) {
                        Log.v(TAG, "waiting for results...");
                        mUrl.wait(2000);
                    }

                    if (mUrl.get() != null) {
                        Log.v(TAG, "result found: " + mUrl.get());
                        l.found(mUrl.get());
                        return;
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for discovery", e);
                } finally {
                    Log.v(TAG, "stopping discovery for " + serviceType + "...");
                    stopDiscovery();
                }
            }
        } finally {
            stopDiscovery();
        }

        l.notFound();
        Log.v(TAG, "discovery finished.");
    }

    public void terminate() {
        stopDiscovery();
    }

    private synchronized void stopDiscovery() {
        if (mDiscoveryListener != null) {
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
            mDiscoveryListener = null;
        }
    }

    private class ResolveListener implements NsdManager.ResolveListener {
        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Log.v(TAG, "service resolve failed: name= " + serviceInfo.getServiceName() + " " + errorCode);
        }

        @Override
        public void onServiceResolved(final NsdServiceInfo serviceInfo) {
            Log.v(TAG, "service resolved: name= " + serviceInfo.getServiceName());

            final int port = serviceInfo.getPort();
            final String host = serviceInfo.getHost().getHostName();

            synchronized (mUrl) {
                if (serviceInfo.getServiceName().equals("openhab-ssl")) {
                    mUrl.set("https://" + host + ":" + port);
                } else if (mUrl.get() == null) {
                    mUrl.set("http://" + host + ":" + port);
                }
                mUrl.notifyAll();
            }
        }
    }

    private class NsdDiscoveryListener implements NsdManager.DiscoveryListener {
        @Override
        public void onDiscoveryStarted(String regType) {
            Log.v(TAG, "discovery started");
        }

        @Override
        public void onServiceFound(final NsdServiceInfo service) {
            Log.v(TAG, "starting to resolve service " + service.getServiceName() + "...");
            mNsdManager.resolveService(service, new ResolveListener());
        }

        @Override
        public void onServiceLost(NsdServiceInfo service) {
            Log.v(TAG, "service lost: name= " + service.getServiceName());
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Log.v(TAG, "discovery stopped");
        }

        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Log.v(TAG, "discovery start failed: " + errorCode);
            mDiscoveryListener = null;
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            Log.v(TAG, "discovery stop failed: " + errorCode);
        }
    }

    public interface DiscoveryListener {
        void found(String serverUrl);

        void notFound();
    }
}
