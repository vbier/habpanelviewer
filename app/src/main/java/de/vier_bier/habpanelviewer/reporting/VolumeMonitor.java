package de.vier_bier.habpanelviewer.reporting;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Handler;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.openhab.StateUpdateListener;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Monitors the device volume.
 */
public class VolumeMonitor implements StateUpdateListener {
    private Context mCtx;
    private AudioManager mAudioManager;
    private ServerConnection mServerConnection;
    private ApplicationStatus mStatus;

    private boolean mVolumeEnabled;

    private String mVolumeItem;
    private Integer mVolume;
    private Integer mMaxVolume;
    private String mVolumeState;

    private ContentObserver mVolumeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            mVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            mServerConnection.updateState(mVolumeItem, String.valueOf(mVolume));

            addStatusItems();
        }
    };

    public VolumeMonitor(Context context, AudioManager audioManager, ServerConnection serverConnection) {
        mCtx = context;
        mAudioManager = audioManager;
        mServerConnection = serverConnection;

        EventBus.getDefault().register(this);

        mMaxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    public synchronized void updateFromPreferences(SharedPreferences prefs) {
        if (mVolumeEnabled != prefs.getBoolean("pref_volume_enabled", false)) {
            mVolumeEnabled = !mVolumeEnabled;

            if (mVolumeEnabled) {
                mCtx.getContentResolver().registerContentObserver(
                        android.provider.Settings.System.CONTENT_URI, true, mVolumeObserver);

                mVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                mServerConnection.updateState(mVolumeItem, String.valueOf(mVolume));
            } else {
                mCtx.getContentResolver().unregisterContentObserver(mVolumeObserver);
            }
        }

        mVolumeItem = prefs.getString("pref_volume_item", "");
        mServerConnection.subscribeItems(this, mVolumeItem);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        mStatus = status;
        addStatusItems();
    }

    public void terminate() {
        mCtx.getContentResolver().unregisterContentObserver(mVolumeObserver);
    }

    @Override
    public void itemUpdated(String name, String value) {
        mVolumeState = value;
        addStatusItems();
    }

    private synchronized void addStatusItems() {
        if (mStatus == null) {
            return;
        }

        if (mVolumeEnabled) {
            String state = mCtx.getString(R.string.enabled);
            if (!mVolumeItem.isEmpty()) {
                state += "\n" + mCtx.getString(R.string.volumeIsOf, mVolume, mMaxVolume, mVolumeItem, mVolumeState);
            }
            mStatus.set(mCtx.getString(R.string.pref_volume), state);
        } else {
            mStatus.set(mCtx.getString(R.string.pref_volume), mCtx.getString(R.string.disabled));
        }
    }
}
