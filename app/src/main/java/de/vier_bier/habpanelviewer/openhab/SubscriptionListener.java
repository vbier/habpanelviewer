package de.vier_bier.habpanelviewer.openhab;

/**
 * Listener for item subscriptions.
 */
public interface SubscriptionListener extends StateUpdateListener {
    void itemInvalid(String name);
}
