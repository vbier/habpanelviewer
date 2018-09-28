package de.vier_bier.habpanelviewer.openhab;

import android.support.annotation.NonNull;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class FutureState extends ItemState implements Delayed {
    private final int delayInMillis;
    private long origin = System.currentTimeMillis();

    public FutureState(String item, int interval, String state) {
        super(item, state);

        delayInMillis = interval * 1000;

        resetTime();
    }

    public void resetTime() {
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

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FutureState && mItemName.equals(((FutureState) obj).mItemName);
    }
}
