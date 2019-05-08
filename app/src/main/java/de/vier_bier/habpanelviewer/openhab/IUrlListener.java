package de.vier_bier.habpanelviewer.openhab;

/**
 * Interface to be notified when the web view changes URL.
 */
public interface IUrlListener {
    void changed(String url, boolean isHabPanelUrl);
}
