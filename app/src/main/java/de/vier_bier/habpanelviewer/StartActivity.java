package de.vier_bier.habpanelviewer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import de.vier_bier.habpanelviewer.connection.ConnectionStatistics;
import de.vier_bier.habpanelviewer.db.AppDatabase;
import de.vier_bier.habpanelviewer.db.CredentialManager;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

public class StartActivity extends AppCompatActivity {
    private static final String TAG = "HPV-StartActivity";

    private Action mAction = Action.SHOW_RELEASE_NOTES;
    private Button mOkB;
    private Button mCancelB;
    private ScrollView mScrollV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager.setDefaultValues(this, R.xml.preferences_main, false);
        for (int id : new int[]{R.xml.preferences_accelerometer,
                R.xml.preferences_battery,
                R.xml.preferences_brightness,
                R.xml.preferences_browser,
                R.xml.preferences_camera,
                R.xml.preferences_connected,
                R.xml.preferences_connection,
                R.xml.preferences_docking_state,
                R.xml.preferences_motion,
                R.xml.preferences_other,
                R.xml.preferences_pressure,
                R.xml.preferences_proximity,
                R.xml.preferences_reporting,
                R.xml.preferences_restart,
                R.xml.preferences_screen,
                R.xml.preferences_temperature,
                R.xml.preferences_ui,
                R.xml.preferences_usage,
                R.xml.preferences_volume}) {
            PreferenceManager.setDefaultValues(this, id, true);
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        Log.d(TAG, "Version: " + getAppVersion());

        setTheme(UiUtil.getThemeId(prefs.getString(Constants.PREF_THEME, "dark")));
        setContentView(R.layout.activity_start);

        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        mOkB = findViewById(R.id.start_ok);
        mCancelB = findViewById(R.id.start_cancel);
        mScrollV = findViewById(R.id.start_text_scroll);
    }

    @Override
    public void onStart() {
        super.onStart();

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        TextView statusTView = findViewById(R.id.start_status);

        if (mAction == Action.SHOW_RELEASE_NOTES) {
            statusTView.setText(getString(R.string.checkingAppUpdated));
            checkUpgrade(prefs);
        }

        boolean introShown = prefs.getBoolean(Constants.PREF_INTRO_SHOWN, false);
        if (mAction == Action.SHOW_INTRO) {
            if (!introShown) {
                statusTView.setText(getString(R.string.showingIntro));
                showIntro();
            } else {
                mAction = Action.SHOW_POWER_SAVING_NOTIFICATION;
            }
        }

        if (mAction == Action.SHOW_POWER_SAVING_NOTIFICATION) {
            statusTView.setText(getString(R.string.checkingOsSettings));
            checkPowerSaving(prefs);
        }

        if (mAction == Action.STARTING) {
            updateUi(View.GONE, View.GONE, View.GONE,null,null);

            try {
                statusTView.setText(getString(R.string.initPwdDb));
                CredentialManager.getInstance().setDatabase(Room.databaseBuilder(getApplicationContext(),
                        AppDatabase.class, "HPV").build());

                statusTView.setText(getString(R.string.initCertStore));
                File localTrustStoreFile = new File(getFilesDir(), "localTrustStore.bks");
                if (!localTrustStoreFile.exists()) {
                    try (InputStream in = getResources().openRawResource(R.raw.mytruststore)) {
                        try (OutputStream out = new FileOutputStream(localTrustStoreFile)) {
                            // Transfer bytes from in to out
                            byte[] buf = new byte[1024];
                            int len;
                            while ((len = in.read(buf)) > 0) {
                                out.write(buf, 0, len);
                            }
                        }
                    }
                    Log.d(TAG, "Local trust store file created");
                }

                CredentialManager.getInstance().addCredentialsListener(ConnectionStatistics.OkHttpClientFactory.getInstance());
                statusTView.setText(getString(R.string.starting));
            } catch (IOException e) {
                Log.e(TAG, "failed to create local trust store", e);
            }

            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            startActivity(intent);
            this.finish();
        }
    }

    private void checkPowerSaving(SharedPreferences prefs) {
        boolean psWarnShown = prefs.getBoolean(Constants.PREF_POWER_SAVE_WARNING_SHOWN, false);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(getPackageName())
                && !psWarnShown) {
            TextView textView = findViewById(R.id.start_text);
            textView.setText(getString(R.string.diablePowerSaving));

            updateUi(View.VISIBLE, View.VISIBLE, View.VISIBLE, (view) -> {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        startActivity(intent);
                    }, (view) -> {
                        SharedPreferences.Editor editor1 = prefs.edit();
                        editor1.putBoolean(Constants.PREF_POWER_SAVE_WARNING_SHOWN, true);
                        editor1.apply();
                        mAction = Action.STARTING;
                        onStart();
                    });
        } else {
            mAction = Action.STARTING;
        }
    }

    private void updateUi(int okVisible, int cancelVisible, int scrollVisible,
                          View.OnClickListener okListener, View.OnClickListener cancelListener) {
        mOkB.setVisibility(okVisible);
        mOkB.setOnClickListener(okListener);
        mCancelB.setVisibility(cancelVisible);
        mCancelB.setOnClickListener(cancelListener);

        mScrollV.setVisibility(scrollVisible);
    }


    private void checkUpgrade(SharedPreferences prefs) {
        String lastVersion = prefs.getString(Constants.PREF_APP_VERSION, "");

        if (!"".equals(lastVersion) && !BuildConfig.VERSION_NAME.equals(lastVersion)) {
            SharedPreferences.Editor editor1 = prefs.edit();
            editor1.putString(Constants.PREF_APP_VERSION, BuildConfig.VERSION_NAME);
            editor1.apply();

            TextView statusTView = findViewById(R.id.start_status);
            statusTView.setText(getString(R.string.updatedText));

            final String relText = ResourcesUtil.fetchReleaseNotes(this, lastVersion);
            TextView textView = findViewById(R.id.start_text);
            textView.setText(relText);

            updateUi(View.VISIBLE, View.GONE, View.VISIBLE, (view) -> {
                mAction = Action.SHOW_INTRO;
                onStart();
            }, null);
        } else {
            mAction = Action.SHOW_INTRO;
        }
    }

    private void showIntro() {
        Intent intent = new Intent(StartActivity.this, IntroActivity.class);
        intent.putExtra(Constants.INTENT_FLAG_INTRO_ONLY, false);

        new Thread(() -> runOnUiThread(() -> startActivity(intent))).start();
    }

    private String getAppVersion() {
        String version = BuildConfig.VERSION_NAME;
        if (version.endsWith("pre")) {
            Date buildDate = new Date(BuildConfig.TIMESTAMP);
            version += " (" + UiUtil.formatDateTime(buildDate) + ")";
        }

        return version;
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        status.set(getString(R.string.app_name), "Version: " + getAppVersion());
    }

    private enum Action {
        SHOW_RELEASE_NOTES, SHOW_INTRO, SHOW_POWER_SAVING_NOTIFICATION, STARTING
    }
}
