package de.vier_bier.habpanelviewer.reporting;

/**
 * Exception for indicating a missing device sensor.
 */
class SensorMissingException extends Exception {
    SensorMissingException(String msg) {
        super(msg);
    }
}
