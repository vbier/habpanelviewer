package de.vier_bier.habpanelviewer.openhab.average;

import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import de.vier_bier.habpanelviewer.openhab.FutureState;

/**
 * Thread that does cyclic propagation of state averages or timed state updates
 */
public class AveragePropagator extends Thread {
    private final IStatePropagator mStatePropagator;
    private final AtomicBoolean mRunning = new AtomicBoolean(true);

    private final BlockingQueue<Average> mAvgQueue = new DelayQueue<>();
    private final HashMap<String, Average> mAverages = new HashMap<>();

    private final BlockingQueue<FutureState> mFutureStateQueue = new DelayQueue<>();
    private final HashMap<String, FutureState> mFutureStates = new HashMap<>();

    public AveragePropagator(IStatePropagator statePropagator) {
        super("AveragePropagator");
        setDaemon(true);

        mStatePropagator = statePropagator;
        start();
    }


    public void clear() {
        mAverages.clear();
    }

    public void setStateIn(String item, String state, int timeout) {
        if (item != null && !item.isEmpty()) {
            synchronized (mFutureStates) {
                FutureState futureState = mFutureStates.get(item);

                if (futureState == null) {
                    futureState = new FutureState(item, timeout, state);
                    mFutureStates.put(item, futureState);
                    mFutureStateQueue.add(futureState);
                } else {
                    mFutureStateQueue.remove(futureState);
                    futureState.resetTime();
                    mFutureStateQueue.add(futureState);
                }
            }
        }
    }

    public boolean addStateToAverage(String item, Float state, int updateInterval) {
        boolean isFirstValue = false;

        if (item != null && !item.isEmpty() && state != null) {
            FloatAverage avg = (FloatAverage) mAverages.get(item);

            if (avg == null) {
                isFirstValue = true;
                avg = new FloatAverage(item, updateInterval);
                mAverages.put(item, avg);
                mAvgQueue.add(avg);
            } else if (avg.setInterval(updateInterval)) {
                // the update interval changed
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
                // first get all averages that are due in the next second and propagate them
                Average a;
                while ((a = mAvgQueue.poll(1, TimeUnit.SECONDS)) != null) {
                    String avgValue = a.getAverage();
                    if (avgValue != null) {
                        mStatePropagator.updateState(a.getItemName(), avgValue);
                    }

                    a.resetTime();
                    mAvgQueue.add(a);
                }

                // we get here approximately every second
                FutureState futureState;
                synchronized (mFutureStates) {
                    while ((futureState = mFutureStateQueue.poll(1, TimeUnit.MILLISECONDS)) != null) {
                        mStatePropagator.updateState(futureState.getItemName(), futureState.getItemState());
                        mFutureStates.remove(futureState.getItemName());
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void terminate() {
        mRunning.set(false);
    }
}
