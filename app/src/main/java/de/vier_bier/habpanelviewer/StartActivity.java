package de.vier_bier.habpanelviewer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import de.vier_bier.habpanelviewer.connection.OkHttpClientFactory;
import de.vier_bier.habpanelviewer.db.CredentialManager;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;

public class StartActivity extends AppCompatActivity {
    private static final String TAG = "HPV-StartActivity";

    private Action mAction = Action.SHOW_RELEASE_NOTES;
    private Button mOkB;
    private Button mCancelB;
    private ScrollView mScrollV;
    private EditText mPasswd;

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
        mPasswd = findViewById(R.id.start_passwd);
    }

    @Override
    public void onStart() {
        Log.v(TAG, "onStart: " + mAction.name());
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

        if (mAction == Action.OPEN_DB) {
            statusTView.setText(getString(R.string.openingDB));

            CredentialManager.State dbState = CredentialManager.getInstance().getDatabaseState(this);
            boolean needsCredentials = prefs.contains(Constants.REST_REALM);
            final String password = mPasswd.getText().toString();

            View.OnClickListener cancelListener = null;
            TextView textView = findViewById(R.id.start_text);
            if (CredentialManager.getInstance().hasDatabase()) {
                mAction = Action.INIT_HTTP_FACTORY;
            } else if (dbState == CredentialManager.State.DOES_NOT_EXIST) {
                textView.setText(R.string.EnterMaster);

                cancelListener = v -> {
                    CredentialManager.getInstance().openDb(this, null);

                    SharedPreferences.Editor editor1 = prefs.edit();
                    editor1.putBoolean(Constants.PREF_DB_ASKED_ENCRYPTION, true);
                    editor1.apply();

                    mAction = Action.INIT_HTTP_FACTORY;
                    onStart();
                };
            } else if (dbState == CredentialManager.State.UNENCRYPTED) {
                if (prefs.contains(Constants.PREF_DB_ASKED_ENCRYPTION)) {
                    CredentialManager.getInstance().openDb(this, null);
                    mAction = Action.INIT_HTTP_FACTORY;
                } else {
                    textView.setText(R.string.UnencryptedEnterMaster);

                    cancelListener = v -> {
                        CredentialManager.getInstance().openDb(this, null);

                        SharedPreferences.Editor editor1 = prefs.edit();
                        editor1.putBoolean(Constants.PREF_DB_ASKED_ENCRYPTION, true);
                        editor1.apply();

                        mAction = Action.INIT_HTTP_FACTORY;
                        onStart();
                    };
                }
            } else if (needsCredentials) {
                if ("".equals(password)) {
                    textView.setText(R.string.ServerProtectedEnterMaster);
                } else {
                    textView.setText(R.string.WrongPassEnterMaster);
                }

                cancelListener = v -> {
                    CredentialManager.getInstance().setDatabaseUsed(false);
                    mAction = Action.STARTING;
                    onStart();
                };
            } else {
                // encrypted but no credentials needed for the rest api
                mAction = Action.STARTING;
            }

            if (mAction == Action.OPEN_DB) {
                boolean showCancel = cancelListener != null;

                updateUi(View.VISIBLE, showCancel ? View.VISIBLE : View.GONE, View.VISIBLE, View.VISIBLE, (view) -> new AsyncTask<Void, Void, Void>() {
                    String password = null;

                    @Override
                    protected void onPreExecute() {
                        password = mPasswd.getText().toString();
                    }

                    @Override
                    protected Void doInBackground(Void... voids) {
                        if (dbState == CredentialManager.State.UNENCRYPTED) {
                            CredentialManager.getInstance().encryptDb(StartActivity.this, password);
                        } else {
                            CredentialManager.getInstance().openDb(StartActivity.this, password);
                        }

                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void val) {
                        onStart();
                    }
                }.execute(), cancelListener);
            }
        }

        if (mAction == Action.INIT_HTTP_FACTORY) {
            if (prefs.contains(Constants.REST_REALM) && CredentialManager.getInstance().hasDatabase()) {
                mAction = Action.STARTING;

                String host = prefs.getString(Constants.REST_HOST, "");
                String realm = prefs.getString(Constants.REST_REALM, "");
                CredentialManager.getInstance().getRestCredential(host, realm, new CredentialManager.CredentialsListener() {
                    @Override
                    public void credentialsEntered(String user, String pass) {
                        OkHttpClientFactory.getInstance().setHost(host);
                        OkHttpClientFactory.getInstance().setRealm(realm);
                        OkHttpClientFactory.getInstance().setAuth(user, pass);
                        onStart();
                    }

                    @Override
                    public void credentialsCancelled() {
                        onStart();
                    }
                });

                return;
            } else {
                mAction = Action.STARTING;
            }
        }

        if (mAction == Action.STARTING) {
            updateUi(View.GONE, View.GONE, View.GONE, View.GONE, null,null);

            try {
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

            updateUi(View.VISIBLE, View.VISIBLE, View.VISIBLE, View.GONE, (view) -> {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        startActivity(intent);
                    }, (view) -> {
                        SharedPreferences.Editor editor1 = prefs.edit();
                        editor1.putBoolean(Constants.PREF_POWER_SAVE_WARNING_SHOWN, true);
                        editor1.apply();
                        mAction = Action.OPEN_DB;
                        onStart();
                    });
        } else {
            mAction = Action.OPEN_DB;
        }
    }

    private void updateUi(int okVisible, int cancelVisible, int scrollVisible, int passwdVisible,
                          View.OnClickListener okListener, View.OnClickListener cancelListener) {
        mOkB.setVisibility(okVisible);
        mOkB.setOnClickListener(v -> {
            mCancelB.setEnabled(false);
            mOkB.setEnabled(false);
            okListener.onClick(v);
        });
        mCancelB.setVisibility(cancelVisible);
        mCancelB.setOnClickListener(v -> {
            mCancelB.setEnabled(false);
            mOkB.setEnabled(false);
            cancelListener.onClick(v);
        });
        mOkB.setEnabled(passwdVisible != View.VISIBLE);
        mCancelB.setEnabled(true);

        mPasswd.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                mOkB.setEnabled(s.toString().length() > 0);
            }
        });

        mPasswd.setOnKeyListener((v, keyCode, event) -> {
            // If the event is a key-down event on the "enter" button
            if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                mCancelB.setEnabled(false);
                mOkB.setEnabled(false);
                okListener.onClick(v);

                return true;
            }
            return false;
        });

        mScrollV.setVisibility(scrollVisible);
        mPasswd.setVisibility(passwdVisible);
        mPasswd.requestFocus();
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

            updateUi(View.VISIBLE, View.GONE, View.VISIBLE, View.GONE, (view) -> {
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
        SHOW_RELEASE_NOTES, SHOW_INTRO, SHOW_POWER_SAVING_NOTIFICATION, OPEN_DB, INIT_HTTP_FACTORY, STARTING
    }
}
