package de.vier_bier.habpanelviewer.command;

/**
 * Handler for commands sent from openHAB.
 */
interface ICommandHandler {
    boolean handleCommand(Command cmd);
}
