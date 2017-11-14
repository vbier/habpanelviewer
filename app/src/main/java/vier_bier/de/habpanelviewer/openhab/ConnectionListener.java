package vier_bier.de.habpanelviewer.openhab;

/**
 * Interface to be notified about connected and disconnected state.
 */
public interface ConnectionListener {
    void connected(String url);

    void disconnected();
}
