package de.vier_bier.habpanelviewer;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

public class PermissionValidatingActivity extends Activity {
    String[] mMissingPerms;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(null);

        mMissingPerms = getIntent().getStringArrayExtra(Constants.INTENT_FLAG_PERMISSIONS);

        if (mMissingPerms.length == 0) {
            setResult(Activity.RESULT_OK);
            finish();
        } else {
            ActivityCompat.requestPermissions(this, mMissingPerms, Constants.REQUEST_VALIDATE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == Constants.REQUEST_VALIDATE) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            boolean permissionsDisabled = false;

            int idx = 0;
            SharedPreferences.Editor editor1 = prefs.edit();
            for (String permission : mMissingPerms) {
                if (grantResults[idx] != PackageManager.PERMISSION_GRANTED) {

                    String[] prefKeys = PermissionUtil.getDependingPreferences(permission);
                    for (String key : prefKeys) {
                        editor1.putBoolean(key, false);
                    }
                    permissionsDisabled = true;
                }

                idx++;
            }
            editor1.apply();

            setResult(permissionsDisabled ? Activity.RESULT_CANCELED : Activity.RESULT_OK);
            finish();
        }

    }
}
