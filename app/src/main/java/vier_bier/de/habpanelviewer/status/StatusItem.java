package vier_bier.de.habpanelviewer.status;

/**
 * Info Item that will be shown in the Status Information Screen
 */
public class StatusItem {
    private static long COUNTER;
    private long id = COUNTER++;

    private String mName;
    private String mValue;

    public StatusItem(String name, String value) {
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
