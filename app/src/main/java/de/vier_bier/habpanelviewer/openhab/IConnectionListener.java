package de.vier_bier.habpanelviewer.openhab;

/**
 * Interface to be notified about connected and disconnected state.
 */
public interface IConnectionListener {
    void connected(String url);

    void disconnected();
}
