package de.vier_bier.habpanelviewer.openhab.average;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * Numeric delayed average.
 */
public abstract class Average<R extends Number> implements Delayed {
    private final ArrayList<Sample<R>> samples = new ArrayList<>();
    private final String itemName;

    private int delayInMillis;
    R total;

    private long origin = System.currentTimeMillis();

    Average(String item, int interval, R zero) {
        itemName = item;
        delayInMillis = interval * 1000;
        total = zero;

        resetTime();
    }

    public void add(R state) {
        Sample<R> newSample = new Sample<>(state);

        samples.add(newSample);
        addToTotal(newSample.getValue());
    }

    public String getAverage() {
        removeOldSamples(System.currentTimeMillis());

        if (samples.isEmpty()) {
            return null;
        }

        R avg = divideTotal(samples.size());
        return String.valueOf(avg);
    }

    private void removeOldSamples(long time) {
        Iterator<Sample<R>> i = samples.iterator();
        Sample<R> s;
        while (i.hasNext()) {
            s = i.next();

            if (s.getTime() < time - delayInMillis) {
                i.remove();
                removeFromTotal(s.getValue());
            } else {
                return;
            }
        }
    }

    public abstract void removeFromTotal(R value);

    public abstract void addToTotal(R value);

    public abstract R divideTotal(int count);

    String getItemName() {
        return itemName;
    }

    void resetTime() {
        origin = System.currentTimeMillis();
    }

    @Override
    public long getDelay(@NonNull TimeUnit timeUnit) {
        return timeUnit.convert(delayInMillis - (System.currentTimeMillis() - origin), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(@NonNull Delayed delayed) {
        if (delayed == this) {
            return 0;
        }

        return Long.compare(getDelay(TimeUnit.MILLISECONDS), delayed.getDelay(TimeUnit.MILLISECONDS));
    }

    boolean setInterval(int interval) {
        if (delayInMillis != interval * 1000) {
            delayInMillis = interval * 1000;

            return true;
        }

        return false;
    }
}
