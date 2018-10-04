package de.vier_bier.habpanelviewer;

import android.content.Context;
import android.support.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Utility methods for reading resources.
 */
class ResourcesUtil {
    @NonNull
    private static String readRawTextFile(Context ctx, int resId) {
        InputStream inputStream = ctx.getResources().openRawResource(resId);

        InputStreamReader inputreader = new InputStreamReader(inputStream);
        BufferedReader buffreader = new BufferedReader(inputreader);
        String line;
        StringBuilder text = new StringBuilder();

        try {
            while ((line = buffreader.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
        } catch (IOException e) {
            return "";
        }

        return text.toString();
    }

    static String fetchReleaseNotes(Context ctx, String lastVersion) {
        String text = readRawTextFile(ctx, R.raw.releasenotes);

        final int pos = text.indexOf(lastVersion);

        if (pos != -1) {
            text = text.substring(0, pos).trim();
        }

        return text;
    }
}
