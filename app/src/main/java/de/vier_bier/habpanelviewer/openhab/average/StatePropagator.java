package de.vier_bier.habpanelviewer.openhab.average;

/**
 * Propagates item state updates.
 */
public interface StatePropagator {
    void updateState(String item, String state);
}
