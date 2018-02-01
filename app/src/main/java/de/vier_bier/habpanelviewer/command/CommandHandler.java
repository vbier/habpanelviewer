package de.vier_bier.habpanelviewer.command;

/**
 * Handler for commands sent from openHAB.
 */
interface CommandHandler {
    boolean handleCommand(String cmd);
}
