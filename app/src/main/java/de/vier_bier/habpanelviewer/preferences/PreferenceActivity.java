package de.vier_bier.habpanelviewer.preferences;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import de.vier_bier.habpanelviewer.Constants;
import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.ScreenControllingActivity;
import de.vier_bier.habpanelviewer.UiUtil;

/**
 * Activity for setting preferences.
 */
public class PreferenceActivity extends ScreenControllingActivity implements PreferenceCallback {
    private static final String TAG_NESTED = "TAG_NESTED";

    private Toolbar mToolbar;
    private MenuItem mUpItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_preferences);

        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String theme = prefs.getString(Constants.PREF_THEME, "dark");

        if ("dark".equals(theme)) {
            mToolbar.setPopupTheme(R.style.ThemeOverlay_AppCompat_Dark);
        } else {
            mToolbar.setPopupTheme(R.style.ThemeOverlay_AppCompat_Light);
        }

        if (savedInstanceState == null) {
            PreferencesMain mPrefFragment = new PreferencesMain();
            mPrefFragment.setArguments(getIntent().getExtras());

            getFragmentManager().beginTransaction().add(R.id.preferences_fragment_container, mPrefFragment).commit();
        }
    }

    @Override
    public void onBackPressed() {
        // this if statement is necessary to navigate through nested and main fragments
        if (getFragmentManager().getBackStackEntryCount() == 0) {
            super.onBackPressed();
        } else {
            mUpItem.setEnabled(getFragmentManager().getBackStackEntryCount() > 1);
            UiUtil.tintItemPreV21(mUpItem, getApplicationContext(), getTheme());
            getFragmentManager().popBackStack();
        }
    }

    @Override
    public void onNestedPreferenceSelected(String id) {
        mUpItem.setEnabled(true);
        UiUtil.tintItemPreV21(mUpItem, getApplicationContext(), getTheme());
        getFragmentManager().beginTransaction().replace(R.id.preferences_fragment_container,
                PreferenceFragment.newInstance(id, getIntent().getExtras()), TAG_NESTED).addToBackStack(TAG_NESTED).commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.preferences_toolbar_menu, menu);

        mUpItem = menu.findItem(R.id.action_back);
        UiUtil.tintItemPreV21(mUpItem, getApplicationContext(), getTheme());

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_pref_export) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        Constants.REQUEST_WRITE_EXTERNAL_STORAGE);
            } else {
                PreferenceUtil.saveSharedPreferencesToFile(this, mToolbar);
            }

            return true;
        }

        if (id == R.id.action_pref_import) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        Constants.REQUEST_READ_EXTERNAL_STORAGE);
            } else {
                PreferenceUtil.loadSharedPreferencesFromFile(this, mToolbar);
            }
            return true;
        }

        if (id == R.id.action_back) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case Constants.REQUEST_READ_EXTERNAL_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    PreferenceUtil.loadSharedPreferencesFromFile(this, mToolbar);
                }
            }
            case Constants.REQUEST_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    PreferenceUtil.saveSharedPreferencesToFile(this, mToolbar);
                }
            }
        }
    }

    @Override
    public View getScreenOnView() {
        return mToolbar;
    }
}

