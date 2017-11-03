package vier_bier.de.habpanelviewer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.camera2.CameraManager;
import android.net.nsd.NsdManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
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

import java.util.ArrayList;
import java.util.Date;

import vier_bier.de.habpanelviewer.flash.FlashController;
import vier_bier.de.habpanelviewer.motion.IMotionDetector;
import vier_bier.de.habpanelviewer.motion.MotionDetector;
import vier_bier.de.habpanelviewer.motion.MotionDetectorCamera2;
import vier_bier.de.habpanelviewer.motion.MotionListener;
import vier_bier.de.habpanelviewer.motion.MotionVisualizer;
import vier_bier.de.habpanelviewer.screen.ScreenController;
import vier_bier.de.habpanelviewer.status.ApplicationStatus;
import vier_bier.de.habpanelviewer.status.StatusInfoActivity;

/**
 * Main activity showing the Webview for openHAB.
 */
public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, ConnectionListener {

    private ClientWebView mWebView;
    private TextView mTextView;
    private SSEClient mSseClient;
    private Thread.UncaughtExceptionHandler exceptionHandler = Thread.getDefaultUncaughtExceptionHandler();

    private ServerDiscovery mDiscovery;
    private FlashController mFlashService;
    private ScreenController mScreenService;
    private IMotionDetector mMotionDetector;
    private ApplicationStatus mStatus;

    private int mRestartCount;

    //TODO.vb. adapt volume settings: AudioManager
    //TODO.vb. load list of available panels from HABPanel for selection in preferences
    //TODO.vb. add functionality to take pictures (face detection) and upload to network depending on openHAB item
    //TODO.vb. check if proximity sensor can be used
    //TODO.vb. check if light sensor can be used

    @Override
    protected void onDestroy() {
        destroy();

        super.onDestroy();
    }

    void destroy() {
        if (mFlashService != null) {
            mFlashService.terminate();
            mFlashService = null;
        }

        if (mMotionDetector != null) {
            mMotionDetector.shutdown();
            mMotionDetector = null;
        }

        if (mDiscovery != null) {
            mDiscovery.terminate();
            mDiscovery = null;
        }

        if (mSseClient != null) {
            mSseClient.terminate();
            mSseClient = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        setContentView(R.layout.activity_main);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // inflate navigation header to make sure the textview holding the connection text is created
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        LinearLayout navHeader = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.nav_header_main, null);
        navigationView.addHeaderView(navHeader);
        navigationView.setNavigationItemSelectedListener(this);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    mFlashService = new FlashController((CameraManager) getSystemService(Context.CAMERA_SERVICE));
                } catch (CameraException e) {
                    Log.d("Habpanelview", "Could not create flash controller");
                }
            }

            final SurfaceView motionView = ((SurfaceView) findViewById(R.id.motionView));

            MotionListener ml = new MotionListener.MotionAdapter() {
                @Override
                public void motionDetected(ArrayList<Point> differing) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mScreenService.screenOn();
                        }
                    });
                }
            };

            int scaledSize = getResources().getDimensionPixelSize(R.dimen.motionFontSize);
            MotionVisualizer mv = new MotionVisualizer(motionView, navigationView, prefs, ml, scaledSize);

            boolean newApi = prefs.getBoolean("pref_motion_detection_new_api", Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
            if (newApi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mMotionDetector = new MotionDetectorCamera2(this, (CameraManager) getSystemService(Context.CAMERA_SERVICE), mv, this);
            } else {
                mMotionDetector = new MotionDetector(this, mv);
            }
        }

        mDiscovery = new ServerDiscovery((NsdManager) getSystemService(Context.NSD_SERVICE));

        if (prefs.getBoolean("pref_first_start", true)) {
            SharedPreferences.Editor editor1 = prefs.edit();
            editor1.putBoolean("pref_first_start", false);
            editor1.apply();

            if (prefs.getString("pref_url", "").isEmpty()) {
                mDiscovery.discover(new ServerDiscovery.DiscoveryListener() {
                    @Override
                    public void found(String serverUrl) {
                        SharedPreferences.Editor editor1 = prefs.edit();
                        editor1.putString("pref_url", serverUrl);
                        editor1.apply();
                    }

                    @Override
                    public void notFound() {
                        Toast.makeText(MainActivity.this, "Could not find openHAB server!", Toast.LENGTH_LONG).show();
                    }
                }, true, true);
            }
        }

        mScreenService = new ScreenController((PowerManager) getSystemService(POWER_SERVICE), this);
        mSseClient = new SSEClient(this);
        mSseClient.setConnectionListener(this);

        if (mFlashService != null) {
            mSseClient.addStateListener(mFlashService);
        }
        if (mScreenService != null) {
            mSseClient.addStateListener(mScreenService);
        }

        mRestartCount = getIntent().getIntExtra("restartCount", 0);
        showInitialToastMessage(mRestartCount);

        mTextView = navHeader.findViewById(R.id.textView);

        mWebView = ((ClientWebView) findViewById(R.id.activity_main_webview));
        mWebView.initialize();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ApplicationStatus status) {
        mStatus = status;

        addStatusItems();
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

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        MenuItem i = navigationView.getMenu().findItem(R.id.action_start_app);
        Intent launchIntent = getLaunchIntent(this);
        i.setVisible(launchIntent != null);
        if (launchIntent != null) {
            i.setTitle("Launch " + prefs.getString("pref_app_name", "App"));
        }

        if (mFlashService != null) {
            mFlashService.updateFromPreferences(prefs);
        }

        if (mScreenService != null) {
            mScreenService.updateFromPreferences(prefs);

            // make sure screen lock is released on start
            mScreenService.screenOff();
        }

        if (mMotionDetector != null) {
            mMotionDetector.updateFromPreferences(prefs);
        }

        mWebView.updateFromPreferences(prefs);
        mSseClient.updateFromPreferences(prefs);

        TextureView previewView = ((TextureView) findViewById(R.id.previewView));
        SurfaceView motionView = ((SurfaceView) findViewById(R.id.motionView));
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

        /**
         AudioManager audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
         audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
         int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
         **/
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
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
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
        } else if (id == R.id.action_goto_start_url) {
            mWebView.loadStartUrl();
        } else if (id == R.id.action_reload) {
            mWebView.reload();
        } else if (id == R.id.action_start_app) {
            Intent launchIntent = getLaunchIntent(this);

            if (launchIntent != null) {
                startActivity(launchIntent);
            }
        } else if (id == R.id.action_info) {
            showInfoScreen();
        } else if (id == R.id.action_restart) {
            destroy();
            ProcessPhoenix.triggerRebirth(this);
        } else if (id == R.id.action_exit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask();
            } else {
                finish();
            }
            Runtime.getRuntime().exit(0);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void connected(final String url) {
        runOnUiThread(new Runnable() {
            public void run() {
                mTextView.setText(url);
            }
        });
    }

    @Override
    public void disconnected() {
        runOnUiThread(new Runnable() {
            public void run() {
                mTextView.setText(R.string.not_connected);
            }
        });
    }

    private static Intent getLaunchIntent(Activity activity) {
        Intent launchIntent = null;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        if (prefs.getBoolean("pref_app_enabled", false)) {
            String app = prefs.getString("pref_app_package", "");

            if (!app.isEmpty()) {
                launchIntent = activity.getPackageManager().getLaunchIntentForPackage(app);
            }
        }

        return launchIntent;
    }

    private void showInitialToastMessage(int restartCount) {
        String toastMsg = "";
        if (restartCount > 0) {
            toastMsg += "App restarted after crash";
        }

        if (mFlashService == null) {
            toastMsg += "No back-facing camera with flash found on this device\n";
        }

        if (mScreenService == null) {
            toastMsg += "Unable to control screen backlight on this device\n";
        }

        if (mMotionDetector == null) {
            toastMsg += "Unable to detect motion on this device\n";
        }

        if (!toastMsg.isEmpty()) {
            Toast.makeText(this, toastMsg.trim(), Toast.LENGTH_LONG).show();
        }
    }

    private void showPreferences() {
        Intent intent = new Intent(MainActivity.this, SetPreferenceActivity.class);
        intent.putExtra("flash_enabled", mFlashService != null);
        intent.putExtra("motion_enabled", mMotionDetector != null);
        intent.putExtra("screen_enabled", mScreenService != null);
        startActivityForResult(intent, 0);
    }

    private void showInfoScreen() {
        Intent intent = new Intent();
        intent.setClass(MainActivity.this, StatusInfoActivity.class);

        addStatusItems();

        startActivityForResult(intent, 0);
    }

    private void addStatusItems() {
        if (mStatus == null) {
            return;
        }

        Date buildDate = new Date(BuildConfig.TIMESTAMP);
        mStatus.set("HABPanelViewer", "Version: " + BuildConfig.VERSION_NAME + "\nBuild date: " + buildDate.toString());

        if (mFlashService == null) {
            mStatus.set("Flash Control", "unavailable");
        }
        if (mScreenService == null) {
            mStatus.set("Backlight Control", "unavailable");
        }
        if (mMotionDetector == null) {
            mStatus.set("Motion Detection", "unavailable");
        }
        if (mRestartCount != 0) {
            mStatus.set("Restart Counter", String.valueOf(mRestartCount));
        }
    }

}
