package vier_bier.de.habpanelviewer;

/**
 * Created by volla on 10.09.17.
 */

public interface ConnectionListener {
    void connected(String url);

    void disconnected();
}
