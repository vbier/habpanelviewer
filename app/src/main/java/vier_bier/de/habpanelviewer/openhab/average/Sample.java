package vier_bier.de.habpanelviewer.openhab.average;

/**
 * Created by volla on 01.12.17.
 */

public class Sample<N extends Number> {
    private N fValue;
    private long fTime;

    public Sample(N value) {
        fValue = value;
        fTime = System.currentTimeMillis();
    }

    public N getValue() {
        return fValue;
    }

    public long getTime() {
        return fTime;
    }
}
