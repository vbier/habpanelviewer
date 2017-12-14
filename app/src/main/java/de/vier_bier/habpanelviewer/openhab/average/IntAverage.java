package de.vier_bier.habpanelviewer.openhab.average;

/**
 * Integer average.
 */
public class IntAverage extends Average<Integer> {
    public IntAverage(String item, int interval) {
        super(item, interval, 0);

        resetTime();
    }

    @Override
    public void removeFromTotal(Integer value) {
        total = total - value;
    }

    @Override
    public void addToTotal(Integer value) {
        total = total + value;
    }

    @Override
    public Integer divideTotal(int count) {
        return total / count;
    }
}
