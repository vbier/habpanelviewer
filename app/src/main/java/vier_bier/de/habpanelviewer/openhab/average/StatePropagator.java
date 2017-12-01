package vier_bier.de.habpanelviewer.openhab.average;

/**
 * Propagates item state updates.
 */
public interface StatePropagator {
    void updateState(String item, String state);
}
