package de.vier_bier.habpanelviewer.control;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.openhab.StateUpdateListener;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

/**
 * Controller for the device volume.
 */
public class VolumeController implements StateUpdateListener {
    private Context mCtx;
    private AudioManager mAudioManager;
    private ServerConnection mServerConnection;

    private boolean enabled;
    private String volumeItemName;
    private final int mMaxVolume;

    private ApplicationStatus mStatus;

    public VolumeController(Context ctx, AudioManager audioManager, ServerConnection serverConnection) {
        mCtx = ctx;
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
            String status = mCtx.getString(R.string.enabled);
            if (!volumeItemName.isEmpty()) {
                status += "\n" + volumeItemName + "=" + mServerConnection.getState(volumeItemName);
            }
            mStatus.set(mCtx.getString(R.string.pref_volume), status + "\n" + mCtx.getString(R.string.volumeIsOf, volume, mMaxVolume));
        } else {
            mStatus.set(mCtx.getString(R.string.pref_volume), mCtx.getString(R.string.disabled)
                    + "\n" + mCtx.getString(R.string.volumeIsOf, volume, mMaxVolume));
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
