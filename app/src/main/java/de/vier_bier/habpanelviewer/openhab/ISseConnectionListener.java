package de.vier_bier.habpanelviewer.openhab;

public interface ISseConnectionListener extends ISseListener {
    void statusChanged(SseConnection.Status newStatus);
}
