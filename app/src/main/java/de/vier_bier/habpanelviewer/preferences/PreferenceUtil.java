package de.vier_bier.habpanelviewer.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;

import com.jakewharton.processphoenix.ProcessPhoenix;
import com.obsez.android.lib.filechooser.ChooserDialog;

import de.vier_bier.habpanelviewer.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

class PreferenceUtil {
    static void askRestart(Activity activity, DialogInterface.OnClickListener okListener,
                           DialogInterface.OnClickListener cancelListener) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.RestartRequired)
                .setMessage(R.string.WantToRestart)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, okListener)
                .setNegativeButton(android.R.string.no, cancelListener).create().show();
    }

    static void saveSharedPreferencesToFile(Context ctx) {
        ChooserDialog d = new ChooserDialog().with(ctx)
                .withFilter(true, false)
                .withStartFile(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOCUMENTS).getPath())
                .withRowLayoutView(R.layout.li_row_textview)
                .withResources(R.string.chooseTargetDirectory, R.string.okay, R.string.cancel)
                .withChosenListener((path, pathFile) -> saveSharedPreferencesToFile(ctx, new File(path, "HPV.prefs")));

        d.build().show();
    }

    static void loadSharedPreferencesFromFile(Activity ctx) {
        new ChooserDialog().with(ctx)
                .withFilter(file -> "HPV.prefs".equals(file.getName()) || file.isDirectory())
                .withStartFile(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOCUMENTS).getPath())
                .withResources(R.string.choose_file, R.string.okay, R.string.cancel)
                .withRowLayoutView(R.layout.li_row_textview)
                .withChosenListener((path, pathFile) -> {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
                    String theme = prefs.getString("pref_theme", "dark");
                    loadSharedPreferencesFromFile(ctx, pathFile);

                    if (!theme.equals(prefs.getString("pref_theme", "dark"))) {
                        askRestart(ctx, (dialog, whichButton) -> {
                            ctx.finish();
                            ProcessPhoenix.triggerRebirth(ctx.getApplication());
                        }, (dialog, whichButton) -> {
                            ctx.finish();
                            ctx.startActivity(ctx.getIntent());
                        });
                    }
                })
                .build()
                .show();
    }

    private static void saveSharedPreferencesToFile(Context ctx, File dst) {
        try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(dst))) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
            output.writeObject(pref.getAll());
            Toast.makeText(ctx, R.string.prefsExported, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(ctx, R.string.prefsExportFailed, Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressWarnings({ "unchecked" })
    private static void loadSharedPreferencesFromFile(Context ctx, File src) {
        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(src))) {
            SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(ctx).edit();
            prefEdit.clear();
            Map<String, ?> entries = (Map<String, ?>) input.readObject();
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                Object v = entry.getValue();
                String key = entry.getKey();

                if (v instanceof Boolean)
                    prefEdit.putBoolean(key, (Boolean) v);
                else if (v instanceof Float)
                    prefEdit.putFloat(key, (Float) v);
                else if (v instanceof Integer)
                    prefEdit.putInt(key, (Integer) v);
                else if (v instanceof Long)
                    prefEdit.putLong(key, (Long) v);
                else if (v instanceof String)
                    prefEdit.putString(key, ((String) v));
            }
            prefEdit.apply();
            Toast.makeText(ctx, R.string.prefsImported, Toast.LENGTH_SHORT).show();
        } catch (IOException | ClassNotFoundException e) {
            Toast.makeText(ctx, R.string.prefsImportFailed, Toast.LENGTH_SHORT).show();
        }
    }
}
