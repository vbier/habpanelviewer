package vier_bier.de.habpanelviewer;

/**
 * Interface for being notified about state changes in openHAB items.
 */
public interface StateListener {
    void updateState(String name, String value);
}
