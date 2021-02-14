package de.vier_bier.habpanelviewer.openhab;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OpenhabSseConnectionTest {
    @Test
    public void testBuildUrl() {
        OpenhabSseConnection c = new OpenhabSseConnection();
        c.setServerUrl("URL");
        c.setItemNames("cmdName");
        assertEquals("URL/rest/events?topics=openhab/items/cmdName/command", c.buildUrl());

        c.setItemNames("cmdName","item1", "item2");
        assertEquals("URL/rest/events?topics=openhab/items/item1/statechanged,openhab/items/item2/statechanged,openhab/items/cmdName/command", c.buildUrl());

        c.setServer("URL", OpenhabSseConnection.OHVersion.OH2);
        assertEquals("URL/rest/events?topics=smarthome/items/item1/statechanged,smarthome/items/item2/statechanged,smarthome/items/cmdName/command", c.buildUrl());

        c.setItemNames("cmdName");
        assertEquals("URL/rest/events?topics=smarthome/items/cmdName/command", c.buildUrl());
    }
}
