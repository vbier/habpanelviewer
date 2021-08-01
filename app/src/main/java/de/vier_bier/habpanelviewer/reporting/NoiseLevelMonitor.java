package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import de.vier_bier.habpanelviewer.Constants;
import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.IStateUpdateListener;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Monitors noise level (by using the microphone) and reports to openHAB.
 */
public class NoiseLevelMonitor implements IDeviceMonitor, IStateUpdateListener {
    private static final String TAG = "HPV-MicMonitor";

    private final Context mCtx;
    private final ServerConnection mServerConnection;
    private ReportingThread mReporter = new ReportingThread();

    private boolean mNoiseLevelEnabled;
    private String mNoiseLevelItem;
    private Float mNoiseLevel;
    private String mNoiseLevelState;
    private int mNoiseLevelInterval;

    public NoiseLevelMonitor(Context context, ServerConnection serverConnection) {
        mCtx = context;
        mServerConnection = serverConnection;

        EventBus.getDefault().register(this);
    }

    @Override
    public void disablePreferences(Intent intent) { }

    @Override
    public synchronized void terminate() {
        EventBus.getDefault().unregister(this);

        if (mReporter != null) {
            mReporter.shutdown();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        if (mNoiseLevelEnabled) {
            String state = mCtx.getString(R.string.enabled);
            if (!mNoiseLevelItem.isEmpty()) {
                state += "\n" + mCtx.getString(R.string.noiseLevel, mNoiseLevel, mNoiseLevelItem, mNoiseLevelState);
            }
            status.set(mCtx.getString(R.string.pref_noiseLevel), state);
        } else {
            status.set(mCtx.getString(R.string.pref_noiseLevel), mCtx.getString(R.string.disabled));
        }
    }

    @Override
    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        if (mNoiseLevelEnabled != prefs.getBoolean(Constants.PREF_NOISE_LEVEL_ENABLED, false)) {
            mNoiseLevelEnabled = !mNoiseLevelEnabled;

            if (mNoiseLevelEnabled) {
                Log.d(TAG, "starting media recorder...");

                mReporter.report();
            } else {
                Log.d(TAG, "stopping media recorder...");
                mReporter.stopReporting();
            }
        }

        mNoiseLevelItem = prefs.getString(Constants.PREF_NOISE_LEVEL_ITEM, "");
        mNoiseLevelInterval = Integer.parseInt(prefs.getString(Constants.PREF_NOISE_LEVEL_INTERVALL, "5"));

        mServerConnection.subscribeItems(this, mNoiseLevelItem);
    }



    @Override
    public void itemUpdated(String name, String value) {
        if (name.equals(mNoiseLevelItem)) {
            mNoiseLevelState = value;
        }
    }

    private class ReportingThread extends Thread {
        private MediaRecorder mRecorder = null;
        private AtomicBoolean isRunning = new AtomicBoolean(true);

        public ReportingThread() {
            start();
        }

        @Override
        public void run() {
            mRecorder = new MediaRecorder();

            while (isRunning.get()) {
                try {
                    Thread.sleep(mNoiseLevelInterval * 1000);
                    mNoiseLevel = (float) (20 * Math.log10(mRecorder.getMaxAmplitude()*0.96518));

                    mServerConnection.updateState(mNoiseLevelItem, String.valueOf(mNoiseLevel));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }

        public void report() {
            if (mRecorder != null) {
                mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                mRecorder.setOutputFile("/dev/null");

                try {
                    mRecorder.prepare();
                } catch (IllegalStateException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                mRecorder.start();
            }
        }

        public void stopReporting() {
            if (mRecorder != null) {
                mRecorder.stop();
                mRecorder.reset();
            }
        }

        public void shutdown() {
            isRunning.set(false);
        }
    }
}
