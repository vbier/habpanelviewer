package vier_bier.de.habpanelviewer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Utility methods for reading and showing the release notes.
 */
class ReleaseNotesUtil {
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
            return null;
        }
        return text.toString();
    }

    static void showUpdateDialog(Context ctx, String lastVersion) {
        String text = readRawTextFile(ctx, R.raw.releasenotes);
        final int pos = text.indexOf(lastVersion);

        if (pos != -1) {
            text = text.substring(0, pos).trim();
        }

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle("HabPanelViewer Update")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setView(LayoutInflater.from(ctx).inflate(R.layout.scrollable_text_dialog, null))
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();

        TextView titleView = dialog.findViewById(R.id.release_notes_title);
        titleView.setText("The application has been updated. Find the bug fixes and new features below:");
        TextView textView = dialog.findViewById(R.id.release_notes_text);
        textView.setText(text);
    }
}
