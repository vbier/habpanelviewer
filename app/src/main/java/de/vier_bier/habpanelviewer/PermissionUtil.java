package de.vier_bier.habpanelviewer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;

public class PermissionUtil {
    private static final HashMap<String, String[]> PERMISSIONS = new HashMap<>();

    static {
        PERMISSIONS.put(Manifest.permission.RECORD_AUDIO, new String[]{Constants.PREF_ALLOW_WEBRTC});
        PERMISSIONS.put(Manifest.permission.CAMERA, new String[]{Constants.PREF_ALLOW_WEBRTC,
                Constants.PREF_MOTION_DETECTION_ENABLED, Constants.PREF_MOTION_DETECTION_PREVIEW});
    }

    public static String[] getMissingPermissions(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        ArrayList<String> missing = new ArrayList<>();

        for (String permission : PERMISSIONS.keySet()) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                String[] prefKeys = PERMISSIONS.get(permission);

                boolean prefEnabled = false;
                for (String key : prefKeys) {
                    prefEnabled = prefEnabled || prefs.getBoolean(key, false);
                }

                if (prefEnabled) {
                    missing.add(permission);
                }
            }
        }

        return missing.toArray(new String[0]);
    }

    public static String[] getDependingPreferences(String permission) {
        return PERMISSIONS.get(permission);
    }

    public static Intent createRequestPermissionsIntent(Context context) {
        Intent mainIntent = new Intent(context, PermissionValidatingActivity.class);
        String[] missingPermissions = getMissingPermissions(context);

        if (missingPermissions.length > 0) {
            mainIntent.putExtra(Constants.INTENT_FLAG_PERMISSIONS, missingPermissions);
            return mainIntent;
        }

        return null;
    }
}
