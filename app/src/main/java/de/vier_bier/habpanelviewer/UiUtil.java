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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

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

    public static void showDialog(final Context context, final String title, final String text) {
        showButtonDialog(context, title, text, android.R.string.ok, null, -1, null);
    }

    public static void showCancelDialog(final Context context, final String title, final String text,
                                        final DialogInterface.OnClickListener yesListener,
                                        final DialogInterface.OnClickListener noListener) {
        showButtonDialog(context, title, text, android.R.string.yes, yesListener, android.R.string.no, noListener);
    }

    public static void showButtonDialog(final Context context, final String title, final String text,
                                        final int button1TextId,
                                        final DialogInterface.OnClickListener button1Listener,
                                        final int button2TextId,
                                        final DialogInterface.OnClickListener button2Listener) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            if (title != null) {
                builder.setTitle(title);
            }
            builder.setMessage(text);
            if (button1TextId != -1) {
                builder.setPositiveButton(button1TextId, button1Listener);
            }
            if (button2TextId != -1) {
                builder.setNegativeButton(button2TextId, button2Listener);
            }
            builder.show();
        });
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

    public static boolean themeChanged(String theme, Activity ctx) {
        Resources.Theme dummy = ctx.getResources().newTheme();
        dummy.applyStyle(getThemeId(theme), true);

        TypedValue a = new TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.windowBackground, a, true);
        TypedValue b = new TypedValue();
        dummy.resolveAttribute(android.R.attr.windowBackground, b, true);

        return a.data != b.data;
    }

    public static boolean themeChanged(SharedPreferences prefs, Activity ctx) {
        String theme = prefs.getString(Constants.PREF_THEME, "dark");
        return themeChanged(theme, ctx);
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

    public static void showPasswordDialog(final Context ctx, final String host, final String realm,
                                          final CredentialsListener l) {
        showPasswordDialog(ctx, host, realm, l, true);
    }

    private static void showPasswordDialog(final Context ctx, final String host, final String realm,
                                           final CredentialsListener l, boolean showWarning) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            // host only contains the complete URL for the SSE and REST connections using basic auth
            if (showWarning && host.toLowerCase().startsWith("http:")) {
                showCancelDialog(ctx, ctx.getString(R.string.credentials_required),
                        ctx.getString(R.string.httpWithBasicAuth),
                        (dialogInterface, i) -> showPasswordDialog(ctx, host, realm, l, false), null);
                return;
            }

            final AlertDialog alert = new AlertDialog.Builder(ctx)
                    .setCancelable(false)
                    .setTitle(R.string.credentials_required)
                    .setMessage(ctx.getString(R.string.host_realm, host, realm))
                    .setView(R.layout.dialog_credentials)
                    .setPositiveButton(R.string.okay, (dialog12, id) -> {
                        EditText userT = ((AlertDialog) dialog12).findViewById(R.id.username);
                        EditText passT = ((AlertDialog) dialog12).findViewById(R.id.password);
                        CheckBox storeCB = ((AlertDialog) dialog12).findViewById(R.id.checkBox);

                        l.credentialsEntered(host, realm, userT.getText().toString(), passT.getText().toString(), storeCB.isChecked());
                    })
                    .setNegativeButton(R.string.cancel, (dialogInterface, i) -> l.credentialsCancelled()).create();

            if (!(ctx instanceof Activity) || !((Activity) ctx).isFinishing()) {
                alert.show();
            }
        });
    }

    public interface CredentialsListener {
        void credentialsEntered(String host, String realm, String user, String password, boolean store);

        void credentialsCancelled();
    }
}
