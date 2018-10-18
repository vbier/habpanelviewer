package de.vier_bier.habpanelviewer.preferences;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.ScreenControllingActivity;

/**
 * Activity for setting preferences.
 */
public class SetPreferenceActivity extends ScreenControllingActivity {
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 123;
    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 124;
    private PereferencesFragment mPrefFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.preferences_main);

        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String theme = prefs.getString("pref_theme", "dark");

        if ("dark".equals(theme)) {
            myToolbar.setPopupTheme(R.style.ThemeOverlay_AppCompat_Dark);
        } else {
            myToolbar.setPopupTheme(R.style.ThemeOverlay_AppCompat_Light);
        }

        mPrefFragment = new PereferencesFragment();
        mPrefFragment.setArguments(getIntent().getExtras());
        getFragmentManager().beginTransaction().replace(R.id.preferences_fragment_container, mPrefFragment).commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.preferences_toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_pref_export) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            } else {
                PreferenceUtil.saveSharedPreferencesToFile(this);
            }

            return true;
        }

        if (id == R.id.action_pref_import) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
            } else {
                PreferenceUtil.loadSharedPreferencesFromFile(this);
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    PreferenceUtil.loadSharedPreferencesFromFile(this);
                }
            }
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    PreferenceUtil.saveSharedPreferencesToFile(this);
                }
            }
        }
    }

    @Override
    public View getScreenOnView() {
        return mPrefFragment.getView();
    }
}

