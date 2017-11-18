package vier_bier.de.habpanelviewer.openhab;

/**
 * Created by volla on 17.11.17.
 */

public interface SubscriptionListener {
    void itemUpdated(String name, String value);
}
