package de.vier_bier.habpanelviewer;

import android.Manifest;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.room.Room;

import com.google.android.material.navigation.NavigationView;
import com.jakewharton.processphoenix.ProcessPhoenix;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.vier_bier.habpanelviewer.command.AdminHandler;
import de.vier_bier.habpanelviewer.command.BluetoothHandler;
import de.vier_bier.habpanelviewer.command.CommandQueue;
import de.vier_bier.habpanelviewer.command.FlashHandler;
import de.vier_bier.habpanelviewer.command.InternalCommandHandler;
import de.vier_bier.habpanelviewer.command.ScreenHandler;
import de.vier_bier.habpanelviewer.command.TtsHandler;
import de.vier_bier.habpanelviewer.command.VolumeHandler;
import de.vier_bier.habpanelviewer.command.WebViewHandler;
import de.vier_bier.habpanelviewer.command.log.CommandLogActivity;
import de.vier_bier.habpanelviewer.connection.ConnectionStatistics;
import de.vier_bier.habpanelviewer.connection.ssl.CertificateManager;
import de.vier_bier.habpanelviewer.db.AppDatabase;
import de.vier_bier.habpanelviewer.db.CredentialManager;
import de.vier_bier.habpanelviewer.help.HelpActivity;
import de.vier_bier.habpanelviewer.openhab.ISseConnectionListener;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.openhab.SseConnection;
import de.vier_bier.habpanelviewer.preferences.PreferenceActivity;
import de.vier_bier.habpanelviewer.reporting.AccelerometerMonitor;
import de.vier_bier.habpanelviewer.reporting.BatteryMonitor;
import de.vier_bier.habpanelviewer.reporting.BrightnessMonitor;
import de.vier_bier.habpanelviewer.reporting.ConnectedIndicator;
import de.vier_bier.habpanelviewer.reporting.DockingStateMonitor;
import de.vier_bier.habpanelviewer.reporting.IDeviceMonitor;
import de.vier_bier.habpanelviewer.reporting.PressureMonitor;
import de.vier_bier.habpanelviewer.reporting.ProximityMonitor;
import de.vier_bier.habpanelviewer.reporting.ScreenMonitor;
import de.vier_bier.habpanelviewer.reporting.TemperatureMonitor;
import de.vier_bier.habpanelviewer.reporting.VolumeMonitor;
import de.vier_bier.habpanelviewer.reporting.motion.Camera;
import de.vier_bier.habpanelviewer.reporting.motion.IMotionDetector;
import de.vier_bier.habpanelviewer.reporting.motion.MotionDetector;
import de.vier_bier.habpanelviewer.reporting.motion.MotionVisualizer;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;
import de.vier_bier.habpanelviewer.status.StatusInfoActivity;

/**
 * Main activity showing the Webview for openHAB.
 */
public class MainActivity extends ScreenControllingActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "HPV-MainActivity";

    private final static int REQUEST_PICK_APPLICATION = 12352;

    private ClientWebView mWebView;
    private TextView mUrlTextView;
    private TextView mStatusTextView;

    private ISseConnectionListener mConnectionListener;
    private ConnectionStatistics mConnections;
    private ServerConnection mServerConnection;
    private AppRestartingExceptionHandler mRestartingExceptionHandler;
    private NetworkTracker mNetworkTracker;
    private FlashHandler mFlashService;
    private IMotionDetector mMotionDetector;
    private MotionVisualizer mMotionVisualizer;
    private ConnectedIndicator mConnectedIndicator;
    private CommandQueue mCommandQueue;
    private ScreenCapturer mCapturer;
    private Camera mCam;

    private final ArrayList<IDeviceMonitor> mMonitors = new ArrayList<>();

    private boolean mIntroShowing;

    @Override
    protected void onDestroy() {
        destroy();

        super.onDestroy();
    }

    public void destroy() {
        Log.v(TAG, "in destroy");

        if (mCapturer != null) {
            mCapturer.terminate();
            mCapturer = null;
        }

        if (mFlashService != null) {
            mFlashService.terminate();
            mFlashService = null;
        }

        if (mMotionDetector != null) {
            mMotionDetector.terminate();
            mMotionDetector = null;
        }

        final CountDownLatch l = new CountDownLatch(1);
        if (mCam != null) {
            Log.v(TAG, "terminating camera...");
            mCam.terminate(l);
            mCam = null;
        } else {
            l.countDown();
        }

        if (mServerConnection != null) {
            mServerConnection.terminate(this);
            mServerConnection = null;
        }

        if (mConnectedIndicator != null) {
            mConnectedIndicator.terminate();
            mConnectedIndicator = null;
        }

        for (IDeviceMonitor m : mMonitors) {
            m.terminate();
        }

        if (mCommandQueue != null) {
            mCommandQueue.terminate();
            mCommandQueue = null;
        }

        if (mNetworkTracker != null) {
            mNetworkTracker.terminate(this);
            mNetworkTracker = null;
        }

        if (mConnections != null) {
            mConnections.terminate();
            mConnections = null;
        }
        if (mWebView != null) {
            mWebView.unregister();
        }
        EventBus.getDefault().unregister(this);

        try {
            l.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "failed to terminate camera");
        }
    }

    public Camera getCamera() {
        return mCam;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        startService(new Intent(getBaseContext(), TrackShutdownService.class));

        PreferenceManager.setDefaultValues(this, R.xml.preferences_main, false);
        for (int id : new int[] {R.xml.preferences_battery,
                R.xml.preferences_brightness,
                R.xml.preferences_browser,
                R.xml.preferences_camera,
                R.xml.preferences_connected,
                R.xml.preferences_connection,
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

        String lastVersion = prefs.getString("pref_app_version", "");
        Log.d(TAG, "Version: " + getAppVersion());

        boolean introShown = prefs.getBoolean("pref_intro_shown", false);
        if (!introShown) {
            mIntroShowing = true;
            showIntro();
        }

        EventBus.getDefault().register(this);
        setContentView(R.layout.activity_main);

        // inflate navigation header to make sure the textview holding the connection text is created
        NavigationView navigationView = findViewById(R.id.nav_view);
        LinearLayout navHeader = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.navheader_main, null);
        navigationView.addHeaderView(navHeader);
        navigationView.setNavigationItemSelectedListener(this);

        try {
            CredentialManager.getInstance().setDatabase(Room.databaseBuilder(getApplicationContext(),
                    AppDatabase.class, "HPV").build());

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
            }
            CertificateManager.getInstance().setTrustStore(localTrustStoreFile);

            Log.d(TAG, "SSL protocol initialized");
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "failed to initialize CertificateManager", e);
            UiUtil.showSnackBar(navigationView, R.string.sslFailed);
        }

        CredentialManager.getInstance().addCredentialsListener(ConnectionStatistics.OkHttpClientFactory.getInstance());

        int restartCount = getIntent().getIntExtra("restartCount", 0);

        if (mRestartingExceptionHandler == null) {
            mRestartingExceptionHandler = new AppRestartingExceptionHandler(this,
                    Thread.getDefaultUncaughtExceptionHandler(), restartCount);
        }

        if (mNetworkTracker == null) {
            mNetworkTracker = new NetworkTracker(this);
        }

        if (mServerConnection == null) {
            mServerConnection = new ServerConnection(this);
            mNetworkTracker.addListener(mServerConnection.getSseConnection());
        }

        if (mConnections == null) {
            mConnections = new ConnectionStatistics(this);
        }

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mFlashService == null) {
            mFlashService = new FlashHandler((CameraManager) getSystemService(Context.CAMERA_SERVICE));
        }

        final SurfaceView motionView = findViewById(R.id.motionView);
        final TextureView previewView = findViewById(R.id.previewView);
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            if (mCam == null) {
                mCam = new Camera(this, previewView, prefs);
            }

            if (mMotionVisualizer == null) {
                int scaledSize = getResources().getDimensionPixelSize(R.dimen.motionFontSize);
                mMotionVisualizer = new MotionVisualizer(motionView, navigationView, prefs, mCam.getSensorOrientation(), scaledSize);

                mMotionDetector = new MotionDetector(this, mCam, mMotionVisualizer, mServerConnection);
            }
        } else {
            Log.d(TAG, "no camera feature_front, hiding preview");
            motionView.setVisibility(View.INVISIBLE);
            previewView.setVisibility(View.INVISIBLE);
        }

        if (!"".equals(lastVersion) && !BuildConfig.VERSION_NAME.equals(lastVersion)) {
            SharedPreferences.Editor editor1 = prefs.edit();
            editor1.putString("pref_app_version", BuildConfig.VERSION_NAME);
            editor1.apply();

            final String relText = ResourcesUtil.fetchReleaseNotes(this, lastVersion);
            UiUtil.showScrollDialog(this, getString(R.string.updated),
                    getString(R.string.updatedText),
                    relText);
        }

        if (mConnectedIndicator == null) {
            mConnectedIndicator = new ConnectedIndicator(this, mServerConnection);
        }

        mMonitors.add(new BatteryMonitor(this, mServerConnection));
        mMonitors.add(new DockingStateMonitor(this, mServerConnection));
        mMonitors.add(new ScreenMonitor(this, mServerConnection, new ScreenMonitor.ScreenListener() {
            @Override
            public void screenOn() {
                mWebView.loadStartUrl();
            }

            @Override
            public boolean isActive() {
                return prefs.getBoolean("pref_load_start_url_on_screenon", false);
            }
        }));
        mMonitors.add(new VolumeMonitor(this, (AudioManager) getSystemService(Context.AUDIO_SERVICE), mServerConnection));

        SensorManager m = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mMonitors.add(new AccelerometerMonitor(this, m, mServerConnection));
        mMonitors.add(new ProximityMonitor(this, m, mServerConnection));
        mMonitors.add(new BrightnessMonitor(this, m, mServerConnection));
        mMonitors.add(new PressureMonitor(this, m, mServerConnection));
        mMonitors.add(new TemperatureMonitor(this, m, mServerConnection));

        if (mCommandQueue == null) {
            mCommandQueue = new CommandQueue(mServerConnection);
            mCommandQueue.addHandler(new InternalCommandHandler(this, mMotionDetector, mServerConnection));
            mCommandQueue.addHandler(new AdminHandler(this));
            mCommandQueue.addHandler(new BluetoothHandler(this, (BluetoothManager) getSystemService(BLUETOOTH_SERVICE)));
            mCommandQueue.addHandler(new ScreenHandler((PowerManager) getSystemService(POWER_SERVICE), this));
            mCommandQueue.addHandler(new VolumeHandler(this, (AudioManager) getSystemService(Context.AUDIO_SERVICE)));
            mCommandQueue.addHandler(new TtsHandler(this));

            if (mFlashService != null) {
                mCommandQueue.addHandler(mFlashService);
            }
        }

        mUrlTextView = navHeader.findViewById(R.id.urlTextView);
        mStatusTextView = navHeader.findViewById(R.id.statusTextView);
        MenuItem enterCredMenu = navigationView.getMenu().findItem(R.id.action_enter_credentials);

        mConnectionListener = new ISseConnectionListener() {
            private SseConnection.Status mLastStatus;

            @Override
            public void statusChanged(SseConnection.Status newStatus) {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                String url = prefs.getString("pref_server_url", "");

                runOnUiThread(() -> {
                    mUrlTextView.setText(url);
                    mStatusTextView.setText(newStatus.name());
                    mUrlTextView.setTextColor(newStatus.getColor());
                    enterCredMenu.setVisible(newStatus == SseConnection.Status.UNAUTHORIZED);

                    if (newStatus == SseConnection.Status.CONNECTED) {
                        findViewById(R.id.activity_main_layout).setPadding(0,0,0,0);
                    } else {
                        findViewById(R.id.activity_main_layout).setPadding(10, 10,10,10);
                    }
                });

                if (newStatus == SseConnection.Status.CONNECTED) {
                    mConnections.connected();
                } else {
                    if (mLastStatus == SseConnection.Status.CONNECTED) {
                        mConnections.disconnected();
                    }

                    if (newStatus == SseConnection.Status.UNAUTHORIZED) {
                        UiUtil.showPasswordDialog(MainActivity.this, url, "Rest API", new UiUtil.CredentialsListener() {
                            @Override
                            public void credentialsEntered(String host, String realm, String user, String password, boolean store) {
                                CredentialManager.getInstance().setRestCredentials(user, password, store);
                            }

                            @Override
                            public void credentialsCancelled() {
                            }
                        });

                    }
                }

                mLastStatus = newStatus;
            }
        };

        mServerConnection.addConnectionListener(mConnectionListener);

        if (restartCount > 0) {
            UiUtil.showSnackBar(mUrlTextView, R.string.appRestarted, R.string.disableRestart, view -> {
                mRestartingExceptionHandler.disable();
                SharedPreferences.Editor editor1 = prefs.edit();
                editor1.putBoolean("pref_restart_enabled", false);
                editor1.apply();
            });
        }

        mWebView = findViewById(R.id.activity_main_webview);
        mWebView.initialize(new ISseConnectionListener() {
            private SseConnection.Status mLastStatus;

            @Override
            public void statusChanged(SseConnection.Status newStatus) {
                if (newStatus != SseConnection.Status.CONNECTED && mLastStatus == SseConnection.Status.CONNECTED) {
                    mServerConnection.reconnect();
                }
                mLastStatus = newStatus;
            }
        }, (url, isHabPanelUrl) -> {
            if (prefs.getBoolean("pref_current_url_enabled", false)) {
                mServerConnection.updateState(prefs.getString("pref_current_url_item", ""), url);
            }
        }, mNetworkTracker);
        mCommandQueue.addHandler(new WebViewHandler(mWebView));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        if (mMotionVisualizer != null) {
            mMotionVisualizer.setDeviceRotation(rotation);
        }
        if (mCam != null) {
            mCam.setDeviceRotation(rotation);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.webview_context_menu, menu);

        menu.findItem(R.id.menu_toggle_kiosk).setVisible(mWebView.isShowingHabPanel());
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_reload:
                mWebView.reload();
                break;
            case R.id.menu_goto_start_url:
                mWebView.loadStartUrl();
                break;
            case R.id.menu_goto_url:
                mWebView.enterUrl(this);
                break;
            case R.id.menu_set_start_url:
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                SharedPreferences.Editor editor1 = prefs.edit();
                editor1.putString("pref_start_url", mWebView.getUrl());
                editor1.apply();
                break;
            case R.id.menu_clear_credentials:
                mWebView.clearPasswords();
                UiUtil.showSnackBar(mWebView, R.string.credentialsCleared, R.string.action_restart, view -> restartApp());
                break;
            case R.id.menu_clear_cache:
                mWebView.clearCache(true);
                UiUtil.showSnackBar(mWebView, R.string.cacheCleared, R.string.menu_reload, view -> mWebView.reload());
                break;
            case R.id.menu_toggle_kiosk:
                mWebView.toggleKioskMode();
                break;
            default:
                super.onContextItemSelected(item);
                return false;
        }

        return true;
    }

    public ScreenCapturer getCapturer() {
        return mCapturer;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_APPLICATION && resultCode == RESULT_OK) {
            startActivity(data);
        } else if (requestCode == ScreenCapturer.REQUEST_MEDIA_PROJECTION
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            boolean allowCapture = resultCode == RESULT_OK;
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            if (prefs.getBoolean("pref_capture_screen_enabled", false) != allowCapture) {
                SharedPreferences.Editor editor1 = prefs.edit();
                editor1.putBoolean("pref_capture_screen_enabled", allowCapture);
                editor1.apply();
            }

            if (resultCode == RESULT_OK) {
                MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                MediaProjection projection = projectionManager.getMediaProjection(RESULT_OK, data);

                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(metrics);

                Point size = new Point();
                getWindowManager().getDefaultDisplay().getSize(size);

                if (mCapturer == null) {
                    mCapturer = new ScreenCapturer(projection, size.x, size.y, metrics.densityDpi);
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        status.set(getString(R.string.app_name), "Version: " + getAppVersion());

        if (mFlashService == null || !mFlashService.isAvailable()) {
            status.set(getString(R.string.flashControl), getString(R.string.unavailable));
        }
        if (mMotionDetector == null || mCam == null || !mCam.canBeUsed()) {
            status.set(getString(R.string.pref_motion), getString(R.string.unavailable));
        }

        String webview = "";
        PackageManager pm = getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo("com.google.android.webview", 0);
            webview += "com.google.android.webview " + pi.versionName + "/" + pi.versionCode + "\n";
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        try {
            PackageInfo pi = pm.getPackageInfo("com.android.webview", 0);
            webview += "com.android.webview " + pi.versionName + "/" + pi.versionCode + "\n";
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        String userAgentString = mWebView.getSettings().getUserAgentString();
        userAgentString = userAgentString.replaceAll(".* Chrome/", "");
        userAgentString = userAgentString.replaceAll(" .*", "");

        webview += "user agent " + userAgentString;
        status.set("Webview", webview.trim());
    }

    private String getAppVersion() {
        String version = BuildConfig.VERSION_NAME;
        if (version.endsWith("pre")) {
            Date buildDate = new Date(BuildConfig.TIMESTAMP);
            version += " (" + UiUtil.formatDateTime(buildDate) + ")";
        }

        return version;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == Camera.MY_REQUEST_CAMERA) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mCam != null) {
                    mCam.updateFromPreferences(prefs);
                    updateMotionPreferences();
                }
            } else {
                SharedPreferences.Editor editor1 = prefs.edit();
                editor1.putBoolean("pref_motion_detection_enabled", false);
                editor1.putBoolean("pref_motion_detection_preview", false);
                editor1.apply();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mIntroShowing) {
            // skip initialization if intro is showing
            mIntroShowing = false;
            return;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        final boolean psWarnShown = prefs.getBoolean("pref_powerSavingWarningShown", false);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(getPackageName())
            && !psWarnShown) {

            UiUtil.showCancelDialog(this, getString(R.string.powerSavingEnabled), getString(R.string.diablePowerSaving), (dialogInterface, i) -> {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(intent);
            }, (dialogInterface, i) -> {
                SharedPreferences.Editor editor1 = prefs.edit();
                editor1.putBoolean("pref_powerSavingWarningShown", true);
                editor1.apply();
            });
        }

        updatePrefsDueToMissingPermissions(prefs);

        NavigationView navigationView = findViewById(R.id.nav_view);
        DrawerLayout.LayoutParams params = (DrawerLayout.LayoutParams) navigationView.getLayoutParams();
        final String menuPos = prefs.getString("pref_menu_position", "");
        if (getString(R.string.left).equalsIgnoreCase(menuPos)) {
            params.gravity = Gravity.START;
        } else {
            params.gravity = Gravity.END;
        }

        if (mRestartingExceptionHandler != null) {
            mRestartingExceptionHandler.updateFromPreferences(prefs);
        }

        if (mCommandQueue != null) {
            mCommandQueue.updateFromPreferences(prefs);
        }

        if (mCapturer == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && prefs.getBoolean("pref_capture_screen_enabled", false)) {
            MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            startActivityForResult(projectionManager.createScreenCaptureIntent(), ScreenCapturer.REQUEST_MEDIA_PROJECTION);
        } else if (mCapturer != null && !prefs.getBoolean("pref_capture_screen_enabled", false)) {
            mCapturer.terminate();
            mCapturer = null;
        }

        for (IDeviceMonitor m : mMonitors) {
            m.updateFromPreferences(prefs);
        }

        mConnectedIndicator.updateFromPreferences(prefs);
        mWebView.updateFromPreferences(prefs);
        mServerConnection.updateFromPreferences(prefs, this);

        updateMotionPreferences();

        if (prefs.getBoolean("pref_show_context_menu", true)) {
            registerForContextMenu(mWebView);
        } else {
            unregisterForContextMenu(mWebView);
        }
    }

    private void updatePrefsDueToMissingPermissions(SharedPreferences prefs) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            SharedPreferences.Editor editor1 = prefs.edit();
            editor1.putBoolean("pref_allow_webrtc", false);
            editor1.putBoolean("pref_motion_detection_enabled", false);
            editor1.putBoolean("pref_motion_detection_preview", false);
            editor1.apply();
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            SharedPreferences.Editor editor1 = prefs.edit();
            editor1.putBoolean("pref_allow_webrtc", false);
            editor1.apply();
        }
    }

    public void updateMotionPreferences() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        if (mCam != null) {
            mCam.updateFromPreferences(prefs);
        }

        if (mMotionDetector != null) {
            mMotionDetector.updateFromPreferences(prefs);
        }

        SurfaceView motionView = findViewById(R.id.motionView);
        boolean showPreview = prefs.getBoolean("pref_motion_detection_preview", false);

        if (showPreview && mCam != null) {
            motionView.setVisibility(View.VISIBLE);
        } else {
            motionView.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public View getScreenOnView() {
        return mWebView;
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_preferences) {
            showPreferences();
        } else if (id == R.id.action_start_app) {
            Intent launchIntent = getLaunchIntent();

            if (launchIntent != null) {
                startActivityForResult(launchIntent, REQUEST_PICK_APPLICATION);
            }
        } else if (id == R.id.action_info) {
            startActivity(StatusInfoActivity.class);
        } else if (id == R.id.action_cmd_log) {
            startActivity(CommandLogActivity.class);
        } else if (id == R.id.action_help) {
            startActivity(HelpActivity.class);
        } else if (id == R.id.action_intro) {
            showIntro();
        } else if (id == R.id.action_show_log) {
            startActivity(LogActivity.class);
        } else if (id == R.id.action_restart) {
            restartApp();
        } else if (id == R.id.action_enter_credentials) {
            if (mServerConnection.getSseConnection().getStatus() == SseConnection.Status.UNAUTHORIZED) {
                mConnectionListener.statusChanged(SseConnection.Status.UNAUTHORIZED);

                // leave drawer open so connection status is visible
                return true;
            }
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawers();
        return true;
    }

    private void restartApp() {
        destroy();
        ProcessPhoenix.triggerRebirth(this);
    }

    private Intent getLaunchIntent() {
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);

        Intent chooser = new Intent(Intent.ACTION_PICK_ACTIVITY);
        chooser.putExtra(Intent.EXTRA_TITLE, "Select App to start");
        chooser.putExtra(Intent.EXTRA_INTENT, main);

        return chooser;
    }

    private void showPreferences() {
        Intent intent = new Intent(MainActivity.this, PreferenceActivity.class);
        intent.putExtra("camera_enabled", mCam != null);
        intent.putExtra("flash_enabled", mFlashService != null && mFlashService.isAvailable());
        intent.putExtra("motion_enabled", mMotionDetector != null && mCam != null && mCam.canBeUsed());

        for (IDeviceMonitor m : mMonitors) {
            m.disablePreferences(intent);
        }

        startActivityForResult(intent, 0);
    }

    private void startActivity(Class activityClass) {
        Intent intent = new Intent();
        intent.setClass(MainActivity.this, activityClass);

        startActivityForResult(intent, 0);
    }

    private void showIntro() {
        new Thread(() -> runOnUiThread(() -> startActivity(new Intent(MainActivity.this, IntroActivity.class)))).start();
    }
}
