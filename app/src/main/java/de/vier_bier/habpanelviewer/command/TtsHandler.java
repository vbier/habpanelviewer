package de.vier_bier.habpanelviewer.command;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TtsHandler implements ICommandHandler {
    private final Pattern TTS_PATTERN = Pattern.compile("TTS_(SPEAK|SET_LANG) (.*)");

    private final Context mContext;

    private TextToSpeech mTTS;
    private int mStatus;

    public TtsHandler(Context ctx) {
        mContext = ctx;
    }

    @Override
    public boolean handleCommand(Command cmd) {
        final String cmdStr = cmd.getCommand();

        Matcher m = TTS_PATTERN.matcher(cmdStr);
        if (m.matches()) {
            cmd.start();

            final String ttsCmd = m.group(1);
            final String arg = m.group(2);

            if (mTTS == null) {
                mTTS = new TextToSpeech(mContext, i -> {
                    mStatus = i;

                    doHandleCommand(ttsCmd, arg, cmd);
                });
            } else {
                doHandleCommand(ttsCmd, arg, cmd);
            }
        }

        return m.matches();
    }

    private void doHandleCommand(String ttsCmd, String arg, Command cmd) {
        if (mStatus == TextToSpeech.SUCCESS) {
            if ("SPEAK".equalsIgnoreCase(ttsCmd)) {
                mTTS.speak(arg, TextToSpeech.QUEUE_ADD, null);
            } else {
                Locale l = new Locale(arg);

                if (mTTS.isLanguageAvailable(l) >= TextToSpeech.LANG_AVAILABLE) {
                    mTTS.setLanguage(l);
                } else {
                    cmd.failed("Given locale invalid: " + arg);
                }
            }
            cmd.finished();
        } else {
            cmd.failed("Could not initialize TTS engine!");
        }
    }
}
