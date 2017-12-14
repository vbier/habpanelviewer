package de.vier_bier.habpanelviewer.reporting;

/**
 * Exception for indicating a missing device sensor.
 */
public class SensorMissingException extends Exception {
    public SensorMissingException(int sensorType) {
        super("Device does not have a sensor of type " + sensorType);
    }
}
