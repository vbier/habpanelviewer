package de.vier_bier.habpanelviewer.command;

import android.media.AudioManager;
import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler for MUTE, UNMUTE, SET_VOLUME commands.
 */
public class VolumeHandler implements CommandHandler {
    private final Pattern SET_PATTERN = Pattern.compile("SET_VOLUME ([0-9]+)");

    private AudioManager mAudioManager;

    private final int mMaxVolume;
    private int mVolume = -1;

    public VolumeHandler(AudioManager audioManager) {
        mAudioManager = audioManager;
        mMaxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    @Override
    public boolean handleCommand(String cmd) {
        Matcher m = SET_PATTERN.matcher(cmd);

        if ("MUTE".equals(cmd)) {
            mVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI);
        } else if ("UNMUTE".equals(cmd)) {
            if (mVolume != -1) {
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mVolume, AudioManager.FLAG_SHOW_UI);
                mVolume = -1;
            }
        } else if (m.matches()) {
            mVolume = -1;
            String value = m.group(1);

            try {
                int volume = Integer.parseInt(value);
                if (volume < 0) {
                    volume = 0;
                } else if (volume > mMaxVolume) {
                    volume = mMaxVolume;
                }

                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI);
            } catch (NumberFormatException e) {
                Log.e("Habpanelview", "failed to parse volume from command: " + cmd);
            }
        } else {
            return false;
        }


        return true;
    }
}
