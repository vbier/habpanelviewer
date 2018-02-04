package de.vier_bier.habpanelviewer;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * UI utility methods.
 */
public class UiUtil {
    public static synchronized String formatDateTime(Date d) {
        return d == null ? "-" : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()).format(d);
    }

    public static void showDialog(final Activity activity, final String title, final String text) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setTitle(title);
                builder.setMessage(text);
                builder.setPositiveButton(android.R.string.ok, null);
                builder.show();
            }
        });
    }

    public static void showScrollDialog(Context ctx, String title, String text, String scrollText) {
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx)
                .setTitle(title)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setView(LayoutInflater.from(ctx).inflate(R.layout.scrollable_text_dialog, null))
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();

        TextView titleView = dialog.findViewById(R.id.release_notes_title);
        titleView.setText(text);
        TextView textView = dialog.findViewById(R.id.release_notes_text);
        textView.setText(scrollText);
    }
}
