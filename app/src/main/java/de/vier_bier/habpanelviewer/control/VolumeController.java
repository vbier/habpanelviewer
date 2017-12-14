package de.vier_bier.habpanelviewer.control;

import android.content.SharedPreferences;
import android.media.AudioManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.openhab.StateUpdateListener;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Controller for the device volume.
 */
public class VolumeController implements StateUpdateListener {
    private AudioManager mAudioManager;
    private ServerConnection mServerConnection;

    private boolean enabled;
    private String volumeItemName;
    private final int mMaxVolume;

    private ApplicationStatus mStatus;

    public VolumeController(AudioManager audioManager, ServerConnection serverConnection) {
        mAudioManager = audioManager;
        mServerConnection = serverConnection;

        EventBus.getDefault().register(this);

        mMaxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        mStatus = status;
        addStatusItems();
    }

    private void addStatusItems() {
        if (mStatus == null) {
            return;
        }

        int volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        if (isEnabled()) {
            mStatus.set("Volume Control", "enabled\n" + volumeItemName + "=" + mServerConnection.getState(volumeItemName)
                    + "\nCurrent volume is " + volume + ", max. is " + mMaxVolume);
        } else {
            mStatus.set("Volume Control", "disabled\nCurrent volume is " + volume + ", max. is " + mMaxVolume);
        }
    }

    private boolean isEnabled() {
        return enabled;
    }

    public void updateFromPreferences(SharedPreferences prefs) {
        volumeItemName = prefs.getString("pref_volume_item", "");
        enabled = prefs.getBoolean("pref_volume_enabled", false);

        mServerConnection.subscribeItems(this, volumeItemName);
    }

    @Override
    public void itemUpdated(String name, String value) {
        Log.i("Habpanelview", "volume item state=" + value);

        try {
            int volume = Integer.parseInt(value);
            if (volume > 0 && volume <= mMaxVolume) {
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
            } else {
                Log.w("Habpanelview", "volume item state out of bounds: " + value);
            }
        } catch (NumberFormatException e) {
            Log.e("Habpanelview", "failed to parse volume from volume item state: " + value);
        }

        addStatusItems();
    }
}
