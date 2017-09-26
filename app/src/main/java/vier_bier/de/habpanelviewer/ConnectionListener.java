package vier_bier.de.habpanelviewer;

/**
 * Interface to be notified about connected and disconnected state.
 */
interface ConnectionListener {
    void connected(String url);

    void disconnected();
}
