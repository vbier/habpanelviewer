package de.vier_bier.habpanelviewer.status;

/**
 * Info Item that will be shown in the Status Information Screen
 */
public class StatusItem {
    private static long COUNTER;
    private final long id = COUNTER++;

    private final String mName;
    private String mValue;

    StatusItem(String name, String value) {
        mName = name;
        mValue = value;
    }

    public String getName() {
        return mName;
    }

    public String getValue() {
        return mValue;
    }

    public long getId() {
        return id;
    }

    public void setValue(String value) {
        mValue = value;
    }
}
