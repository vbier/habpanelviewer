package de.vier_bier.habpanelviewer.openhab;

/**
 * Listener for item subscriptions.
 */
interface ISubscriptionListener extends IStateUpdateListener {
    void itemInvalid(String name);
}
