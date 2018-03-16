package de.vier_bier.habpanelviewer.openhab;

/**
 * Item subscription listener
 */
public interface IStateUpdateListener {
    /**
     * The state of an item has changed.
     */
    void itemUpdated(String name, String value);
}
