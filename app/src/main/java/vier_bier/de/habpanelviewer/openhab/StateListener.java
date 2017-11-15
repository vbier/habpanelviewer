package vier_bier.de.habpanelviewer.openhab;

/**
 * Interface for being notified about state changes in openHAB items.
 */
public interface StateListener {
    void updateState(String name, String value);
}
