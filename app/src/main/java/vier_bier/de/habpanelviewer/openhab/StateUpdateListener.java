package vier_bier.de.habpanelviewer.openhab;

/**
 * Item subscription listener
 */
public interface StateUpdateListener {
    /**
     * The state of an item has changed.
     */
    void itemUpdated(String name, String value);
}
