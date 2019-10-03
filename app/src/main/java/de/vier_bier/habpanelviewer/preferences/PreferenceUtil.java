package de.vier_bier.habpanelviewer.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

import com.obsez.android.lib.filechooser.ChooserDialog;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

import de.vier_bier.habpanelviewer.Constants;
import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.UiUtil;

class PreferenceUtil {
    private static final String TAG = "HPV-PreferenceUtil";

    static void saveSharedPreferencesToFile(Context ctx, View v) {
        ChooserDialog d = new ChooserDialog().with(ctx)
                .withFilter(true, false)
                .withStartFile(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOCUMENTS).getPath())
                .withRowLayoutView(R.layout.row_filechooser)
                .withResources(R.string.chooseTargetDirectory, R.string.okay, R.string.cancel)
                .withChosenListener((path, pathFile) -> saveSharedPreferencesToFile(ctx, v, new File(path, "HPV.prefs")));

        d.build().show();
    }

    static void loadSharedPreferencesFromFile(Activity ctx, View v) {
        new ChooserDialog().with(ctx)
                .withFilter(file -> "HPV.prefs".equals(file.getName()) || file.isDirectory())
                .withStartFile(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOCUMENTS).getPath())
                .withResources(R.string.choose_file, R.string.okay, R.string.cancel)
                .withRowLayoutView(R.layout.row_filechooser)
                .withChosenListener((path, pathFile) -> {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
                    try {
                        loadSharedPreferencesFromFile(ctx, pathFile);

                        if (UiUtil.themeChanged(prefs, ctx)) {
                            UiUtil.showSnackBar(v, R.string.themeChangedRestartRequired, R.string.action_restart,
                                    view -> EventBus.getDefault().post(new Constants.Restart()));
                        } else {
                            UiUtil.showSnackBar(v, R.string.prefsImported);
                        }
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                        UiUtil.showSnackBar(v, R.string.prefsImportFailed);
                    }
                })
                .build()
                .show();
    }

    private static void saveSharedPreferencesToFile(Context ctx, View v, File dst) {
        try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(dst))) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
            output.writeObject(pref.getAll());
            UiUtil.showSnackBar(v, R.string.prefsExported);
        } catch (IOException e) {
            UiUtil.showSnackBar(v, R.string.prefsExportFailed);
        }
    }

    @SuppressWarnings({ "unchecked" })
    private static void loadSharedPreferencesFromFile(Context ctx, File src) throws IOException, ClassNotFoundException {
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
                else {
                    Log.d(TAG, "could not restore preference of class " + v.getClass());
                }
            }
            prefEdit.apply();
        }
    }
}
