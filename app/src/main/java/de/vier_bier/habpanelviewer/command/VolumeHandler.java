package de.vier_bier.habpanelviewer.command;

import android.content.Context;
import android.media.AudioManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.vier_bier.habpanelviewer.R;

/**
 * Handler for MUTE, UNMUTE, SET_VOLUME commands.
 */
public class VolumeHandler implements ICommandHandler {
    private final Pattern SET_PATTERN = Pattern.compile("SET_VOLUME ([0-9]+)");

    private final Context mCtx;
    private final AudioManager mAudioManager;

    private final int mMaxVolume;
    private int mVolume = -1;

    public VolumeHandler(Context ctx, AudioManager audioManager) {
        mCtx = ctx;
        mAudioManager = audioManager;
        mMaxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    @Override
    public boolean handleCommand(Command cmd) {
        final String cmdStr = cmd.getCommand();

        Matcher m = SET_PATTERN.matcher(cmdStr);

        if ("MUTE".equals(cmdStr)) {
            cmd.start();
            mVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI);
        } else if ("UNMUTE".equals(cmdStr)) {
            if (mVolume == -1) {
                cmd.failed(mCtx.getString(R.string.device_not_muted));
            } else {
                cmd.start();
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

                cmd.start();
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI);
            } catch (NumberFormatException e) {
                cmd.failed("failed to parse volume from command");
            }
        } else {
            return false;
        }

        cmd.finished();
        return true;
    }
}
