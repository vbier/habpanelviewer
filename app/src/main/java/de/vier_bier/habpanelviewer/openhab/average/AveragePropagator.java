package de.vier_bier.habpanelviewer.openhab.average;

import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread that does cyclic propagation of state averages.
 */
public class AveragePropagator extends Thread {
    private final AtomicBoolean mRunning = new AtomicBoolean(true);
    private final BlockingQueue<Average> mAvgQueue = new DelayQueue<>();
    private final IStatePropagator mStatePropagator;
    private final HashMap<String, Average> mAverages = new HashMap<>();

    public AveragePropagator(IStatePropagator statePropagator) {
        super("AveragePropagator");
        setDaemon(true);

        mStatePropagator = statePropagator;
        start();
    }


    public void clear() {
        mAverages.clear();
    }

    public boolean addStateToAverage(String item, Integer state, int updateInterval) {
        boolean isFirstValue = false;

        if (item != null && !item.isEmpty() && state != null) {
            IntAverage avg = (IntAverage) mAverages.get(item);

            if (avg == null) {
                isFirstValue = true;
                avg = new IntAverage(item, updateInterval);
                mAverages.put(item, avg);
                mAvgQueue.add(avg);
            } else if (avg.setInterval(updateInterval)) {
                // the update intervall changed
                mAvgQueue.remove(avg);
                mAvgQueue.add(avg);
            }

            avg.add(state);
        }

        return isFirstValue;
    }

    @Override
    public void run() {
        while (mRunning.get()) {
            try {
                Average a = mAvgQueue.take();
                String avgValue = a.getAverage();
                if (avgValue != null) {
                    mStatePropagator.updateState(a.getItemName(), avgValue);
                }

                a.resetTime();
                mAvgQueue.add(a);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void terminate() {
        mRunning.set(false);
    }
}
