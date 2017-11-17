package vier_bier.de.habpanelviewer.control;

import android.content.SharedPreferences;
import android.media.AudioManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import vier_bier.de.habpanelviewer.openhab.StateListener;
import vier_bier.de.habpanelviewer.status.ApplicationStatus;

/**
 * Controller for the device volume.
 */
public class VolumeController implements StateListener {
    private AudioManager mAudioManager;

    private boolean enabled;
    private String volumeItemName;
    private String volumeItemState;
    private final int mMaxVolume;


    private ApplicationStatus mStatus;

    public VolumeController(AudioManager audioManager) {
        mAudioManager = audioManager;
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
            mStatus.set("Volume Control", "enabled\n" + volumeItemName + "=" + volumeItemState
                    + "\nCurrent volume is " + volume + ", max. is " + mMaxVolume);
        } else {
            mStatus.set("Volume Control", "disabled\nCurrent volume is " + volume + ", max. is " + mMaxVolume);
        }
    }

    private boolean isEnabled() {
        return enabled;
    }

    @Override
    public void updateState(String name, String state) {
        if (name.equals(volumeItemName)) {
            if (volumeItemState != null && volumeItemState.equals(state)) {
                Log.i("Habpanelview", "unchanged volume item state=" + state);
                return;
            }

            Log.i("Habpanelview", "volume item state=" + state + ", old state=" + volumeItemState);
            volumeItemState = state;

            try {
                int volume = Integer.parseInt(volumeItemState);
                if (volume > 0 && volume <= mMaxVolume) {
                    mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
                } else {
                    Log.w("Habpanelview", "volume item state out of bounds: " + volumeItemState);
                }
            } catch (NumberFormatException e) {
                Log.e("Habpanelview", "failed to parse volume from volume item state: " + volumeItemState);
            }

            addStatusItems();
        }
    }

    public void updateFromPreferences(SharedPreferences prefs) {
        if (volumeItemName == null || !volumeItemName.equalsIgnoreCase(prefs.getString("pref_volume_item", ""))) {
            volumeItemName = prefs.getString("pref_volume_item", "");
            volumeItemState = null;
        }
        enabled = prefs.getBoolean("pref_volume_enabled", false);

        addStatusItems();
    }
}
