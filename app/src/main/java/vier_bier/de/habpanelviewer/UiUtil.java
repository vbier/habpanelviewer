package vier_bier.de.habpanelviewer;

import android.app.Activity;
import android.support.v7.app.AlertDialog;

/**
 * UI utility methods.
 */
public class UiUtil {
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
}
