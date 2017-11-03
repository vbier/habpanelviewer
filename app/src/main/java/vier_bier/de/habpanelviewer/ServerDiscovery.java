package vier_bier.de.habpanelviewer;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.util.concurrent.atomic.AtomicReference;

/**
 * mDNS discovery for openHAB.
 */
class ServerDiscovery {
    private static final String TAG = "ServerDiscovery";

    private final NsdManager mNsdManager;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private final AtomicReference<String> mUrl = new AtomicReference<>();

    ServerDiscovery(NsdManager nsdManager) {
        mNsdManager = nsdManager;
    }

    synchronized void discover(final DiscoveryListener l, final boolean discoverHttps) {
        if (mDiscoveryListener != null) {
            return;
        }

        Log.v(TAG, "starting discovery...");
        mDiscoveryListener = new NsdDiscoveryListener();
        mUrl.set(null);

        String[] types = new String[]{"_openhab-server._tcp"};
        if (discoverHttps) {
            types = new String[]{"_openhab-server-ssl._tcp", "_openhab-server._tcp"};
        }

        try {
            for (String serviceType : types) {
                try {
                    synchronized (mUrl) {
                        Log.v(TAG, "starting discovery for " + serviceType + "...");
                        mNsdManager.discoverServices(
                                serviceType, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
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
            mDiscoveryListener = null;
        }

        l.notFound();
        Log.v(TAG, "discovery finished.");
    }

    void terminate() {
        stopDiscovery();
    }

    private synchronized void stopDiscovery() {
        if (mDiscoveryListener != null) {
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
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
