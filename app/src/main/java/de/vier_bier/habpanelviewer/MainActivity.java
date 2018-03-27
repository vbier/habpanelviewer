package de.vier_bier.habpanelviewer;

import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.nsd.NsdManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
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
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jakewharton.processphoenix.ProcessPhoenix;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Date;

import de.vier_bier.habpanelviewer.command.AdminHandler;
import de.vier_bier.habpanelviewer.command.BluetoothHandler;
import de.vier_bier.habpanelviewer.command.CommandQueue;
import de.vier_bier.habpanelviewer.command.FlashHandler;
import de.vier_bier.habpanelviewer.command.InternalCommandHandler;
import de.vier_bier.habpanelviewer.command.ScreenHandler;
import de.vier_bier.habpanelviewer.command.VolumeHandler;
import de.vier_bier.habpanelviewer.command.WebViewHandler;
import de.vier_bier.habpanelviewer.command.log.CommandLogActivity;
import de.vier_bier.habpanelviewer.help.HelpActivity;
import de.vier_bier.habpanelviewer.openhab.IConnectionListener;
import de.vier_bier.habpanelviewer.openhab.ServerConnection;
import de.vier_bier.habpanelviewer.openhab.ServerDiscovery;
import de.vier_bier.habpanelviewer.reporting.BatteryMonitor;
import de.vier_bier.habpanelviewer.reporting.BrightnessMonitor;
import de.vier_bier.habpanelviewer.reporting.ConnectedIndicator;
import de.vier_bier.habpanelviewer.reporting.PressureMonitor;
import de.vier_bier.habpanelviewer.reporting.ProximityMonitor;
import de.vier_bier.habpanelviewer.reporting.SensorMissingException;
import de.vier_bier.habpanelviewer.reporting.TemperatureMonitor;
import de.vier_bier.habpanelviewer.reporting.VolumeMonitor;
import de.vier_bier.habpanelviewer.reporting.motion.IMotionDetector;
import de.vier_bier.habpanelviewer.reporting.motion.MotionDetector;
import de.vier_bier.habpanelviewer.reporting.motion.MotionDetectorCamera2;
import de.vier_bier.habpanelviewer.reporting.motion.MotionVisualizer;
import de.vier_bier.habpanelviewer.settings.SetPreferenceActivity;
import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;
import de.vier_bier.habpanelviewer.status.StatusInfoActivity;

/**
 * Main activity showing the Webview for openHAB.
 */
public class MainActivity extends ScreenControllingActivity
        implements NavigationView.OnNavigationItemSelectedListener, IConnectionListener {

    private final static int REQUEST_PICK_APPLICATION = 12352;

    private ConnectionStatistics mConnections;
    private ClientWebView mWebView;
    private TextView mTextView;
    private ServerConnection mServerConnection;
    private final Thread.UncaughtExceptionHandler exceptionHandler = Thread.getDefaultUncaughtExceptionHandler();

    private ServerDiscovery mDiscovery;
    private FlashHandler mFlashService;
    private IMotionDetector mMotionDetector;
    private BatteryMonitor mBatteryMonitor;
    private ConnectedIndicator mConnectedReporter;
    private ProximityMonitor mProximityMonitor;
    private BrightnessMonitor mBrightnessMonitor;
    private PressureMonitor mPressureMonitor;
    private TemperatureMonitor mTemperatureMonitor;
    private VolumeMonitor mVolumeMonitor;
    private CommandQueue mCommandQueue;
    private ScreenCapturer mCapturer;

    private int mRestartCount;

    @Override
    protected void onDestroy() {
        destroy();

        super.onDestroy();
    }

    public void destroy() {
        if (mCapturer != null) {
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

        if (mDiscovery != null) {
            mDiscovery.terminate();
            mDiscovery = null;
        }

        if (mServerConnection != null) {
            mServerConnection.terminate();
            mServerConnection = null;
        }

        if (mBatteryMonitor != null) {
            mBatteryMonitor.terminate();
            mBatteryMonitor = null;
        }

        if (mConnectedReporter != null) {
            mConnectedReporter.terminate();
            mConnectedReporter = null;
        }

        if (mProximityMonitor != null) {
            mProximityMonitor.terminate();
            mProximityMonitor = null;
        }

        if (mBrightnessMonitor != null) {
            mBrightnessMonitor.terminate();
            mBrightnessMonitor = null;
        }

        if (mPressureMonitor != null) {
            mPressureMonitor.terminate();
            mPressureMonitor = null;
        }

        if (mTemperatureMonitor != null) {
            mTemperatureMonitor.terminate();
            mTemperatureMonitor = null;
        }

        if (mVolumeMonitor != null) {
            mVolumeMonitor.terminate();
            mVolumeMonitor = null;
        }

        if (mCommandQueue != null) {
            mCommandQueue = null;
        }

        mWebView.unregister();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);

        try {
            ConnectionUtil.initialize(this);
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, R.string.sslFailed, Toast.LENGTH_LONG).show();
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // inflate navigation header to make sure the textview holding the connection text is created
        NavigationView navigationView = findViewById(R.id.nav_view);
        LinearLayout navHeader = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.nav_header_main, null);
        navigationView.addHeaderView(navHeader);
        navigationView.setNavigationItemSelectedListener(this);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        mConnections = new ConnectionStatistics(this);
        mServerConnection = new ServerConnection(this);
        mServerConnection.addConnectionListener(this);

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    mFlashService = new FlashHandler(this, (CameraManager) getSystemService(Context.CAMERA_SERVICE));
                } catch (CameraAccessException | IllegalAccessException e) {
                    Log.d("Habpanelview", "Could not create flash controller");
                }
            }

            final SurfaceView motionView = findViewById(R.id.motionView);

            int scaledSize = getResources().getDimensionPixelSize(R.dimen.motionFontSize);
            MotionVisualizer mv = new MotionVisualizer(motionView, navigationView, prefs, scaledSize);

            boolean newApi = prefs.getBoolean("pref_motion_detection_new_api", Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
            if (newApi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mMotionDetector = new MotionDetectorCamera2(this, (CameraManager) getSystemService(Context.CAMERA_SERVICE), mv, this, mServerConnection);
            } else {
                mMotionDetector = new MotionDetector(this, mv, mServerConnection);
            }
        }

        mDiscovery = new ServerDiscovery((NsdManager) getSystemService(Context.NSD_SERVICE));

        if (prefs.getBoolean("pref_first_start", true)) {
            SharedPreferences.Editor editor1 = prefs.edit();
            editor1.putBoolean("pref_first_start", false);
            editor1.putString("pref_app_version", BuildConfig.VERSION_NAME);
            editor1.apply();

            final String startText = ResourcesUtil.fetchFirstStart(this);
            UiUtil.showScrollDialog(this, "HABPanelViewer", getString(R.string.welcome),
                    startText);

            if (prefs.getString("pref_server_url", "").isEmpty()) {
                mDiscovery.discover(new ServerDiscovery.DiscoveryListener() {
                    @Override
                    public void found(String serverUrl) {
                        SharedPreferences.Editor editor1 = prefs.edit();
                        editor1.putString("pref_server_url", serverUrl);
                        editor1.apply();
                    }

                    @Override
                    public void notFound() {
                    }
                }, true, true);
            }
        } else {
            // make sure old server url preference is used in case it is still set
            if (prefs.getString("pref_server_url", "").isEmpty()) {
                final String oldUrl = prefs.getString("pref_url", "");

                if (!oldUrl.isEmpty()) {
                    SharedPreferences.Editor editor1 = prefs.edit();
                    editor1.putString("pref_server_url", oldUrl);
                    editor1.remove("pref_url");
                    editor1.apply();
                }
            }

            String lastVersion = prefs.getString("pref_app_version", "0.9.2");

            if (!BuildConfig.VERSION_NAME.equals(lastVersion)) {
                SharedPreferences.Editor editor1 = prefs.edit();
                editor1.putString("pref_app_version", BuildConfig.VERSION_NAME);
                editor1.apply();

                final String relText = ResourcesUtil.fetchReleaseNotes(this, lastVersion);
                UiUtil.showScrollDialog(this, getString(R.string.updated),
                        getString(R.string.updatedText),
                        relText);
            }
        }

        mBatteryMonitor = new BatteryMonitor(this, mServerConnection);
        mVolumeMonitor = new VolumeMonitor(this, (AudioManager) getSystemService(Context.AUDIO_SERVICE), mServerConnection);
        mConnectedReporter = new ConnectedIndicator(this, mServerConnection);

        SensorManager m = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        try {
            mProximityMonitor = new ProximityMonitor(this, m, mServerConnection);
        } catch (SensorMissingException e) {
            Log.d("Habpanelview", "Could not create proximity monitor");
        }
        try {
            mBrightnessMonitor = new BrightnessMonitor(this, m, mServerConnection);
        } catch (SensorMissingException e) {
            Log.d("Habpanelview", "Could not create brightness monitor");
        }
        try {
            mPressureMonitor = new PressureMonitor(this, m, mServerConnection);
        } catch (SensorMissingException e) {
            Log.d("Habpanelview", "Could not create pressure monitor");
        }
        try {
            mTemperatureMonitor = new TemperatureMonitor(this, m, mServerConnection);
        } catch (SensorMissingException e) {
            Log.d("Habpanelview", "Could not create temperature monitor");
        }

        ScreenHandler mScreenHandler = new ScreenHandler((PowerManager) getSystemService(POWER_SERVICE), this);
        mCommandQueue = new CommandQueue(this, mServerConnection);
        mCommandQueue.addHandler(new InternalCommandHandler(this, mMotionDetector, mServerConnection));
        mCommandQueue.addHandler(new AdminHandler(this));
        mCommandQueue.addHandler(new BluetoothHandler(this, (BluetoothManager) getSystemService(BLUETOOTH_SERVICE)));
        mCommandQueue.addHandler(mScreenHandler);
        mCommandQueue.addHandler(new VolumeHandler(this, (AudioManager) getSystemService(Context.AUDIO_SERVICE)));
        if (mFlashService != null) {
            mCommandQueue.addHandler(mFlashService);
        }

        mRestartCount = getIntent().getIntExtra("restartCount", 0);
        showInitialToastMessage(mRestartCount);

        mTextView = navHeader.findViewById(R.id.textView);

        mWebView = findViewById(R.id.activity_main_webview);
        mWebView.initialize(new IConnectionListener() {
            @Override
            public void connected(String url) {
            }

            @Override
            public void disconnected() {
                if (prefs.getBoolean("pref_track_browser_connection", false)) {
                    mServerConnection.reconnect();
                }
            }
        });
        mCommandQueue.addHandler(new WebViewHandler(mWebView));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            startActivityForResult(projectionManager.createScreenCaptureIntent(), ScreenCapturer.REQUEST_MEDIA_PROJECTION);
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
            case R.id.menu_clear_passwords:
                mWebView.clearPasswords();
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
        } else if (requestCode == ScreenCapturer.REQUEST_MEDIA_PROJECTION && resultCode == RESULT_OK
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            MediaProjection projection = projectionManager.getMediaProjection(RESULT_OK, data);

            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);

            Point size = new Point();
            getWindowManager().getDefaultDisplay().getSize(size);

            mCapturer = new ScreenCapturer(projection, size.x, size.y, metrics.densityDpi);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        String version = BuildConfig.VERSION_NAME;
        if (version.endsWith("pre")) {
            Date buildDate = new Date(BuildConfig.TIMESTAMP);
            version += " (" + UiUtil.formatDateTime(buildDate) + ")";
        }
        status.set(getString(R.string.app_name), "Version: " + version);

        if (mFlashService == null) {
            status.set(getString(R.string.pref_flash), getString(R.string.unavailable));
        }
        if (mMotionDetector == null) {
            status.set(getString(R.string.pref_motion), getString(R.string.unavailable));
        }
        if (mRestartCount != 0) {
            status.set(getString(R.string.restartCounter), String.valueOf(mRestartCount));
        }
        if (mProximityMonitor == null) {
            status.set(getString(R.string.pref_proximity), getString(R.string.unavailable));
        }
        if (mPressureMonitor == null) {
            status.set(getString(R.string.pref_pressure), getString(R.string.unavailable));
        }
        if (mBrightnessMonitor == null) {
            status.set(getString(R.string.pref_brightness), getString(R.string.unavailable));
        }
        if (mTemperatureMonitor == null) {
            status.set(getString(R.string.pref_temperature), getString(R.string.unavailable));
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

        if (webview.isEmpty()) {
            webview = mWebView.getSettings().getUserAgentString();
        }
        status.set("Webview", webview.trim());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MotionDetector.MY_PERMISSIONS_MOTION_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (mMotionDetector != null) {
                        mMotionDetector.updateFromPreferences(prefs);
                    }
                } else {
                    SharedPreferences.Editor editor1 = prefs.edit();
                    editor1.putBoolean("pref_motion_detection_enabled", false);
                    editor1.apply();
                }
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        int maxRestarts = 5;
        try {
            maxRestarts = Integer.parseInt(prefs.getString("pref_max_restarts", "5"));
        } catch (NumberFormatException e) {
            Log.e("Habpanelview", "could not parse pref_max_restarts value " + prefs.getString("pref_max_restarts", "5") + ". using default 5");
        }

        boolean restartEnabled = prefs.getBoolean("pref_restart_enabled", false);
        if (restartEnabled && mRestartCount < maxRestarts) {
            Thread.setDefaultUncaughtExceptionHandler(new AppRestartingExceptionHandler(this, mRestartCount));
        } else {
            Thread.setDefaultUncaughtExceptionHandler(exceptionHandler);
        }

        boolean showOnLockScreen = prefs.getBoolean("pref_show_on_lock_screen", false);
        if (showOnLockScreen) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        NavigationView navigationView = findViewById(R.id.nav_view);
        DrawerLayout.LayoutParams params = (DrawerLayout.LayoutParams) navigationView.getLayoutParams();
        final String menuPos = prefs.getString("pref_menu_position", "");
        if (menuPos.equalsIgnoreCase(getString(R.string.left))) {
            params.gravity = Gravity.START;
        } else {
            params.gravity = Gravity.END;
        }

        if (mProximityMonitor != null) {
            mProximityMonitor.updateFromPreferences(prefs);
        }
        if (mBrightnessMonitor != null) {
            mBrightnessMonitor.updateFromPreferences(prefs);
        }
        if (mPressureMonitor != null) {
            mPressureMonitor.updateFromPreferences(prefs);
        }
        if (mTemperatureMonitor != null) {
            mTemperatureMonitor.updateFromPreferences(prefs);
        }
        if (mVolumeMonitor != null) {
            mVolumeMonitor.updateFromPreferences(prefs);
        }
        if (mCommandQueue != null) {
            mCommandQueue.updateFromPreferences(prefs);
        }

        mBatteryMonitor.updateFromPreferences(prefs);
        mConnectedReporter.updateFromPreferences(prefs);
        mWebView.updateFromPreferences(prefs);
        mServerConnection.updateFromPreferences(prefs);

        updateMotionPreferences();

        if (prefs.getBoolean("pref_show_context_menu", true)) {
            registerForContextMenu(mWebView);
        } else {
            unregisterForContextMenu(mWebView);
        }
    }

    public void updateMotionPreferences() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        if (mMotionDetector != null) {
            mMotionDetector.updateFromPreferences(prefs);
        }

        TextureView previewView = findViewById(R.id.previewView);
        SurfaceView motionView = findViewById(R.id.motionView);
        boolean showPreview = prefs.getBoolean("pref_motion_detection_preview", false);
        boolean motionDetection = prefs.getBoolean("pref_motion_detection_enabled", false);
        if (motionDetection) {
            previewView.setVisibility(View.VISIBLE);
            if (showPreview) {
                previewView.getLayoutParams().height = 480;
                previewView.getLayoutParams().width = 640;

                motionView.setVisibility(View.VISIBLE);
            } else {
                // if we have no preview, we still need a visible
                // TextureView in order to have a working motion detection.
                // Resize it to 1x1pxs so it does not get in the way.
                previewView.getLayoutParams().height = 1;
                previewView.getLayoutParams().width = 1;

                motionView.setVisibility(View.INVISIBLE);
            }

            previewView.setLayoutParams(previewView.getLayoutParams());
            motionView.setLayoutParams(motionView.getLayoutParams());
        } else {
            previewView.setVisibility(View.INVISIBLE);
            motionView.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onStop() {
        if (mDiscovery != null) {
            // stop discover onStop, but do not set mDiscovery to null as it will be reused
            // in onStart
            mDiscovery.terminate();
        }

        super.onStop();
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

        if (id == R.id.action_settings) {
            showPreferences();
        } else if (id == R.id.action_start_app) {
            Intent launchIntent = getLaunchIntent();

            if (launchIntent != null) {
                startActivityForResult(launchIntent, REQUEST_PICK_APPLICATION);
            }
        } else if (id == R.id.action_info) {
            showInfoScreen();
        } else if (id == R.id.action_cmd_log) {
            showCmdLogScreen();
        } else if (id == R.id.action_help) {
            showHelpScreen();
        } else if (id == R.id.action_restart) {
            destroy();
            ProcessPhoenix.triggerRebirth(this);
        } else if (id == R.id.action_exit) {
            destroy();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask();
            } else {
                finish();
            }
            Runtime.getRuntime().exit(0);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawers();
        return true;
    }

    @Override
    public void connected(final String url) {
        mConnections.connected();
        runOnUiThread(() -> mTextView.setText(url));
    }

    @Override
    public void disconnected() {
        mConnections.disconnected();
        runOnUiThread(() -> mTextView.setText(R.string.not_connected));
    }

    private Intent getLaunchIntent() {
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);

        Intent chooser = new Intent(Intent.ACTION_PICK_ACTIVITY);
        chooser.putExtra(Intent.EXTRA_TITLE, "Select App to start");
        chooser.putExtra(Intent.EXTRA_INTENT, main);

        return chooser;
    }

    private void showInitialToastMessage(int restartCount) {
        String toastMsg = "";
        if (restartCount > 0) {
            toastMsg += getString(R.string.appRestarted);
        }

        if (!toastMsg.isEmpty()) {
            Toast.makeText(this, toastMsg.trim(), Toast.LENGTH_LONG).show();
        }
    }

    private void showPreferences() {
        Intent intent = new Intent(MainActivity.this, SetPreferenceActivity.class);
        intent.putExtra("flash_enabled", mFlashService != null);
        intent.putExtra("motion_enabled", mMotionDetector != null);
        intent.putExtra("proximity_enabled", mProximityMonitor != null);
        intent.putExtra("pressure_enabled", mPressureMonitor != null);
        intent.putExtra("brightness_enabled", mBrightnessMonitor != null);
        intent.putExtra("temperature_enabled", mTemperatureMonitor != null);
        startActivityForResult(intent, 0);
    }

    private void showInfoScreen() {
        Intent intent = new Intent();
        intent.setClass(MainActivity.this, StatusInfoActivity.class);

        startActivityForResult(intent, 0);
    }

    private void showCmdLogScreen() {
        Intent intent = new Intent();
        intent.setClass(MainActivity.this, CommandLogActivity.class);

        startActivityForResult(intent, 0);
    }

    private void showHelpScreen() {
        Intent intent = new Intent();
        intent.setClass(MainActivity.this, HelpActivity.class);
        startActivityForResult(intent, 0);
    }
}
