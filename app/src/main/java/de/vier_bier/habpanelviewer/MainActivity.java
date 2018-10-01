package de.vier_bier.habpanelviewer;

import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
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
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jakewharton.processphoenix.ProcessPhoenix;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
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
import de.vier_bier.habpanelviewer.reporting.BatteryMonitor;
import de.vier_bier.habpanelviewer.reporting.BrightnessMonitor;
import de.vier_bier.habpanelviewer.reporting.ConnectedIndicator;
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
import de.vier_bier.habpanelviewer.settings.SetPreferenceActivity;
import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;
import de.vier_bier.habpanelviewer.status.ApplicationStatus;
import de.vier_bier.habpanelviewer.status.StatusInfoActivity;

/**
 * Main activity showing the Webview for openHAB.
 */
public class MainActivity extends ScreenControllingActivity
        implements NavigationView.OnNavigationItemSelectedListener, IConnectionListener {
    private static final String TAG = "HPV-MainActivity";

    private final static int REQUEST_PICK_APPLICATION = 12352;

    private ClientWebView mWebView;
    private TextView mTextView;

    private ConnectionStatistics mConnections;
    private ServerConnection mServerConnection;
    private AppRestartingExceptionHandler mRestartingExceptionHandler;
    private NetworkTracker mNetworkTracker;
    private FlashHandler mFlashService;
    private IMotionDetector mMotionDetector;
    private MotionVisualizer mMotionVisualizer;
    private ConnectedIndicator mConnectedReporter;
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

        if (mCam != null) {
            mCam.terminate();
            mCam = null;
        }

        if (mServerConnection != null) {
            mServerConnection.terminate();
            mServerConnection = null;
        }

        if (mConnectedReporter != null) {
            mConnectedReporter.terminate();
            mConnectedReporter = null;
        }

        for (IDeviceMonitor m : mMonitors) {
            m.terminate();
        }

        if (mCommandQueue != null) {
            mCommandQueue.terminate();
            mCommandQueue = null;
        }

        if (mNetworkTracker != null) {
            mNetworkTracker.terminate();
            mNetworkTracker = null;
        }
        if (mWebView != null) {
            mWebView.unregister();
        }
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        long start = System.currentTimeMillis();

        super.onCreate(savedInstanceState);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        boolean introShown = prefs.getBoolean("pref_intro_shown", false);
        if (!introShown) {
            mIntroShowing = true;
            showIntro();
        }

        EventBus.getDefault().register(this);
        setContentView(R.layout.activity_main);

        try {
            ConnectionUtil.getInstance().setContext(this);
            Log.d(TAG, "SSL context initialized");
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "failed to initialize ConnectionUtil", e);
            Toast.makeText(this, R.string.sslFailed, Toast.LENGTH_LONG).show();
        }

        // inflate navigation header to make sure the textview holding the connection text is created
        NavigationView navigationView = findViewById(R.id.nav_view);
        LinearLayout navHeader = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.nav_header_main, null);
        navigationView.addHeaderView(navHeader);
        navigationView.setNavigationItemSelectedListener(this);

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
            mServerConnection.addConnectionListener(this);

            mNetworkTracker.addListener(mServerConnection);
        }

        if (mConnections == null) {
            mConnections = new ConnectionStatistics(this);
        }

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mFlashService == null) {
                try {
                    mFlashService = new FlashHandler(this, (CameraManager) getSystemService(Context.CAMERA_SERVICE));
                } catch (CameraAccessException | IllegalAccessException e) {
                    Log.d(TAG, "Could not create flash controller");
                }
            }
        }

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            if (mCam == null) {
                mCam = new Camera(this, findViewById(R.id.previewView), prefs);
            }

            if (mMotionVisualizer == null) {
                int scaledSize = getResources().getDimensionPixelSize(R.dimen.motionFontSize);
                final SurfaceView motionView = findViewById(R.id.motionView);
                mMotionVisualizer = new MotionVisualizer(motionView, navigationView, prefs, mCam.getSensorOrientation(), scaledSize);

                mMotionDetector = new MotionDetector(this, mCam, mMotionVisualizer, mServerConnection);
            }
        }

        String lastVersion = prefs.getString("pref_app_version", "");
        if (!lastVersion.equals("") && !BuildConfig.VERSION_NAME.equals(lastVersion)) {
            SharedPreferences.Editor editor1 = prefs.edit();
            editor1.putString("pref_app_version", BuildConfig.VERSION_NAME);
            editor1.apply();

            final String relText = ResourcesUtil.fetchReleaseNotes(this, lastVersion);
            UiUtil.showScrollDialog(this, getString(R.string.updated),
                    getString(R.string.updatedText),
                    relText);
        }

        if (mConnectedReporter == null) {
            mConnectedReporter = new ConnectedIndicator(this, mServerConnection);
        }

        mMonitors.add(new BatteryMonitor(this, mServerConnection));
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
        mMonitors.add(new ProximityMonitor(this, m, mServerConnection));
        mMonitors.add(new BrightnessMonitor(this, m, mServerConnection));
        mMonitors.add(new PressureMonitor(this, m, mServerConnection));
        mMonitors.add(new TemperatureMonitor(this, m, mServerConnection));

        if (mCommandQueue == null) {
            mCommandQueue = new CommandQueue(this, mServerConnection);
            mCommandQueue.addHandler(new InternalCommandHandler(this, mMotionDetector, mServerConnection));
            mCommandQueue.addHandler(new AdminHandler(this));
            mCommandQueue.addHandler(new BluetoothHandler(this, (BluetoothManager) getSystemService(BLUETOOTH_SERVICE)));
            mCommandQueue.addHandler(new ScreenHandler((PowerManager) getSystemService(POWER_SERVICE), this));
            mCommandQueue.addHandler(new VolumeHandler(this, (AudioManager) getSystemService(Context.AUDIO_SERVICE)));

            if (mFlashService != null) {
                mCommandQueue.addHandler(mFlashService);
            }
        }

        showInitialToastMessage(restartCount);

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
        }, mNetworkTracker);
        mCommandQueue.addHandler(new WebViewHandler(mWebView));

        Log.d(TAG, "onCreate: " + (System.currentTimeMillis() - start));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mMotionVisualizer != null) {
            mMotionVisualizer.setDeviceRotation(getWindowManager().getDefaultDisplay().getRotation());
        }

        if (mCam != null) {
            mCam.setDeviceRotation(getWindowManager().getDefaultDisplay().getRotation());
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
        String version = BuildConfig.VERSION_NAME;
        if (version.endsWith("pre")) {
            Date buildDate = new Date(BuildConfig.TIMESTAMP);
            version += " (" + UiUtil.formatDateTime(buildDate) + ")";
        }
        status.set(getString(R.string.app_name), "Version: " + version);

        if (mFlashService == null) {
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

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case Camera.MY_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
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
        NavigationView navigationView = findViewById(R.id.nav_view);
        DrawerLayout.LayoutParams params = (DrawerLayout.LayoutParams) navigationView.getLayoutParams();
        final String menuPos = prefs.getString("pref_menu_position", "");
        if (menuPos.equalsIgnoreCase(getString(R.string.left))) {
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

        if (mCam != null) {
            mCam.updateFromPreferences(prefs);
        }

        if (mMotionDetector != null) {
            mMotionDetector.updateFromPreferences(prefs);
        }

        SurfaceView motionView = findViewById(R.id.motionView);
        boolean showPreview = prefs.getBoolean("pref_motion_detection_preview", false);

        if (showPreview) {
            motionView.setVisibility(View.VISIBLE);
        } else {
            motionView.setVisibility(View.INVISIBLE);
        }
        motionView.setLayoutParams(motionView.getLayoutParams());
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
        } else if (id == R.id.action_intro) {
            showIntro();
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
        runOnUiThread(() -> mTextView.setText(R.string.notConnected));
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
        intent.putExtra("camera_enabled", mCam != null);
        intent.putExtra("flash_enabled", mFlashService != null);
        intent.putExtra("motion_enabled", mMotionDetector != null && mCam != null && mCam.canBeUsed());

        for (IDeviceMonitor m : mMonitors) {
            m.disablePreferences(intent);
        }

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

    private void showIntro() {
        new Thread(() -> runOnUiThread(() -> startActivity(new Intent(MainActivity.this, IntroActivity.class)))).start();
    }
}
