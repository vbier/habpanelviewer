package de.vier_bier.habpanelviewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * UI utility methods.
 */
public class UiUtil {
    private static final String TAG = "HPV-UiUtil";

    static synchronized String formatDateTime(Date d) {
        return d == null ? "-" : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()).format(d);
    }

    public static void showDialog(final Activity activity, final String title, final String text) {
        activity.runOnUiThread(() -> {
            final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(title);
            builder.setMessage(text);
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
        });
    }

    public static void showCancelDialog(final Activity activity, final String title, final String text,
                                        final DialogInterface.OnClickListener okayListener) {
        activity.runOnUiThread(() -> {
            final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(title);
            builder.setMessage(text);
            builder.setPositiveButton(android.R.string.yes, okayListener);
            builder.setNegativeButton(android.R.string.no, null);
            builder.show();
        });
    }

    static void showScrollDialog(Context ctx, String title, String text, String scrollText) {
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx)
                .setTitle(title)
                .setPositiveButton(android.R.string.yes, (dialog1, which) -> dialog1.dismiss())
                .setView(LayoutInflater.from(ctx).inflate(R.layout.scrollable_text_dialog, null))
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();

        TextView titleView = dialog.findViewById(R.id.release_notes_title);
        titleView.setText(text);
        TextView textView = dialog.findViewById(R.id.release_notes_text);
        textView.setText(scrollText);
    }

    public static void showSnackBar(View view, int textId, int actionTextId, View.OnClickListener clickListener) {
        showSnackBar(view, view.getContext().getString(textId), view.getContext().getString(actionTextId), clickListener);
    }

    public static void showSnackBar(View view, int textId) {
        showSnackBar(view, view.getContext().getString(textId), null, null);
    }

    public static void showSnackBar(View view, String text) {
        showSnackBar(view, text, null, null);
    }

    private static void showSnackBar(View view, String text, String actionText, View.OnClickListener clickListener) {
        Snackbar sb = Snackbar.make(view, text, Snackbar.LENGTH_LONG);
        View sbV = sb.getView();
        TextView textView = sbV.findViewById(com.google.android.material.R.id.snackbar_text);
        textView.setMaxLines(3);
        if (actionText != null && clickListener != null) {
            sb.setAction(actionText, clickListener);
        }

        sb.show();
    }

    static int getThemeId(String theme) {
        if ("dark".equals(theme)) {
            return R.style.Theme_AppCompat_NoActionBar;
        }

        return R.style.Theme_AppCompat_Light_NoActionBar;
    }

    public static boolean themeChanged(SharedPreferences prefs, Activity ctx) {
        Resources.Theme dummy = ctx.getResources().newTheme();
        dummy.applyStyle(getThemeId(prefs.getString("pref_theme", "dark")), true);

        TypedValue a = new TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.windowBackground, a, true);
        TypedValue b = new TypedValue();
        dummy.resolveAttribute(android.R.attr.windowBackground, b, true);

        return a.data == b.data;
    }

    @SuppressLint("ResourceType")
    public static void tintItemPreV21(MenuItem item, Context ctx, Resources.Theme theme) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            TypedValue typedValue = new TypedValue();
            theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true);

            try {
                int color = ContextCompat.getColor(ctx, typedValue.resourceId);
                ColorStateList csl = AppCompatResources.getColorStateList(ctx, typedValue.resourceId);
                if (csl != null) {
                    color = csl.getColorForState(new int[] {item.isEnabled() ? android.R.attr.state_enabled : 0}, color);
                }

                Drawable icon = item.getIcon();
                if (icon != null) {
                    icon.mutate();
                    icon.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
                }
            } catch (Resources.NotFoundException e) {
                Log.e(TAG, "Could not tint action bar icon on pre-lollipop device", e);
            }
        }
    }
}
