package de.vier_bier.habpanelviewer.openhab.average;

/**
 * Float average.
 */
public class FloatAverage extends Average<Float> {
    public FloatAverage(String item, int interval) {
        super(item, interval, 0f);

        resetTime();
    }

    @Override
    public void removeFromTotal(Float value) {
        total = total - value;
    }

    @Override
    public void addToTotal(Float value) {
        total = total + value;
    }

    @Override
    public Float divideTotal(int count) {
        return total / count;
    }
}
