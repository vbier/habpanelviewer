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
import de.vier_bier.habpanelviewer.openhab.IStateUpdateListener;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Monitors the device volume.
 */
public class VolumeMonitor implements IStateUpdateListener {
    private final Context mCtx;
    private final AudioManager mAudioManager;
    private final ServerConnection mServerConnection;

    private boolean mVolumeEnabled;

    private String mVolumeItem;
    private Integer mVolume;
    private final Integer mMaxVolume;
    private String mVolumeState;

    private final ContentObserver mVolumeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            mVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            mServerConnection.updateState(mVolumeItem, String.valueOf(mVolume));
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
        mVolumeItem = prefs.getString("pref_volume_item", "");

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

        mServerConnection.subscribeItems(this, mVolumeItem);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        if (mVolumeEnabled) {
            String state = mCtx.getString(R.string.enabled);
            if (!mVolumeItem.isEmpty()) {
                state += "\n" + mCtx.getString(R.string.volumeIsOf, mVolume, mMaxVolume, mVolumeItem, mVolumeState);
            }
            status.set(mCtx.getString(R.string.pref_volume), state);
        } else {
            status.set(mCtx.getString(R.string.pref_volume), mCtx.getString(R.string.disabled));
        }
    }

    public void terminate() {
        EventBus.getDefault().unregister(this);
        mCtx.getContentResolver().unregisterContentObserver(mVolumeObserver);
    }

    @Override
    public void itemUpdated(String name, String value) {
        mVolumeState = value;
    }
}
