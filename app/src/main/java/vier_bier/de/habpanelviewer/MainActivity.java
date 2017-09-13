package vier_bier.de.habpanelviewer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, ConnectionListener {

    private WebView mWebView;
    private TextView mTextView;
    private SSEClient sseClient;
    private Thread.UncaughtExceptionHandler exceptionHandler = Thread.getDefaultUncaughtExceptionHandler();

    private boolean allowScrolling;

    private FlashController mFlashService;
    private ScreenController mScreenService;

    //TODO.vb. add info menu entries showing capabilities
    //TODO.vb. force screen to on while regexp is met
    //TODO.vb. load list of available panels from habpanel for selection in preferences
    //TODO.vb. add functionality to take pictures (face detection) and upload to network depending on openHAB item
    //TODO.vb. use opencv for motion detection
    //         https://www.codeproject.com/Articles/791145/Motion-Detection-in-Android-Howto
    //         https://stackoverflow.com/questions/27406303/opencv-in-android-studio
    //TODO.vb. check if proximity sensor can be used
    //TODO.vb. check if light sensor can be used
    //TODO.vb. adapt volume settings: System.Settings & System.Secure.Settings
    //         https://gist.github.com/shrikant0013/fc3e67b4b898294a03e4eba1b527f898 for how to obtain a specific permission
    //TODO.vb. restart on crash

    @Override
    protected void onDestroy() {
        stopEventSource();

        if (mFlashService != null) {
            mFlashService.terminate();
            mFlashService = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        setContentView(R.layout.activity_main);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        LinearLayout navHeader = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.nav_header_main, null);
        navigationView.addHeaderView(navHeader);

        navigationView.setNavigationItemSelectedListener(this);


        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            try {
                mFlashService = new FlashController((CameraManager) getSystemService(Context.CAMERA_SERVICE));
            } catch (CameraAccessException e) {
                Log.d("Habpanelview", "Could not create flash controller");
            }
        }
        mScreenService = new ScreenController((PowerManager) getSystemService(POWER_SERVICE), this);

        showToastMessage();

        mTextView = navHeader.findViewById(R.id.textView);

        mWebView = ((WebView) findViewById(R.id.activity_main_webview));
        mWebView.setWebViewClient(new WebViewClient());
        mWebView.setWebChromeClient(new WebChromeClient());

        mWebView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return (event.getAction() == MotionEvent.ACTION_MOVE && !allowScrolling);
            }
        });

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(mWebView, true);
    }

    private void showToastMessage() {
        String toastMsg = "";
        if (getIntent().getBooleanExtra("crash", false)) {
            toastMsg += "App restarted after crash\n";
        }

        if (mFlashService == null) {
            toastMsg += "No back-facing camera with flash found on this device\n";
        }

        if (mScreenService == null) {
            toastMsg += "Unable to control screen backlight on this device\n";
        }

        if (!toastMsg.isEmpty()) {
            Toast.makeText(this, toastMsg.trim(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        if (prefs.getBoolean("pref_restart_enabled", false)) {
            Thread.setDefaultUncaughtExceptionHandler(new AppRestartingExceptionHandler(this));
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

        allowScrolling = prefs.getBoolean("pref_scrolling", false);

        if (mFlashService != null) {
            mFlashService.updateFromPreferences(prefs);
        }

        if (mScreenService != null) {
            mScreenService.updateFromPreferences(prefs);

            // make sure screen lock is released on start
            mScreenService.screenOff();
        }

        Boolean isDesktop = prefs.getBoolean("pref_desktop_mode", false);
        Boolean isJavascript = prefs.getBoolean("pref_javascript", false);

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setUseWideViewPort(isDesktop);
        webSettings.setLoadWithOverviewMode(isDesktop);
        webSettings.setJavaScriptEnabled(isJavascript);

        loadStartUrl();

        startEventSource();
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
            loadStartUrl();
        } else if (id == R.id.action_reload) {
            mWebView.reload();
        } else if (id == R.id.action_start_app) {
            Intent launchIntent = getLaunchIntent(this);

            if (launchIntent != null) {
                startActivity(launchIntent);
            }
        } else if (id == R.id.action_info) {
            showInfoScreen();
        } else if (id == R.id.action_exit) {
            System.exit(0);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
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

    private void showPreferences() {
        Intent intent = new Intent();
        intent.setClass(MainActivity.this, SetPreferenceActivity.class);
        intent.putExtra("flash_enabled", mFlashService != null);
        startActivityForResult(intent, 0);
    }

    private void showInfoScreen() {
        Intent intent = new Intent();
        intent.setClass(MainActivity.this, InfoActivity.class);

        if (mFlashService == null) {
            intent.putExtra("Flash Control", "unavailable");
        } else if (mFlashService.isEnabled()) {
            intent.putExtra("Flash Control", "enabled\n" + mFlashService.getItemName() + "=" + mFlashService.getItemState());
        } else {
            intent.putExtra("Flash Control", "disabled");
        }
        if (mScreenService == null) {
            intent.putExtra("Backlight Control", "unavailable");
        } else if (mScreenService.isEnabled()) {
            intent.putExtra("Backlight Control", "enabled\n" + mScreenService.getItemName() + "=" + mScreenService.getItemState());
        } else {
            intent.putExtra("Backlight Control", "disabled");
        }

        startActivityForResult(intent, 0);
    }

    private void loadStartUrl() {
        SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        String url = mySharedPreferences.getString("pref_url", "http://openhabianpi:8080");
        String panel = mySharedPreferences.getString("pref_panel", "");

        url += "/habpanel/index.html#/";

        if (!panel.isEmpty()) {
            url += "view/" + panel;
        }

        Boolean isKiosk = mySharedPreferences.getBoolean("pref_kiosk_mode", false);
        if (isKiosk) {
            url += "?kiosk=on";
        }

        if (!url.equals(mWebView.getUrl())) {
            // reload when kiosk mode changed
            boolean reload = mWebView.getUrl() != null && mWebView.getUrl().contains("?kiosk=on") != isKiosk;

            mWebView.clearCache(true);
            mWebView.loadUrl(url);

            if (reload) {
                mWebView.reload();
            }
        }
    }

    private synchronized void startEventSource() {
        if (sseClient != null) {
            stopEventSource();
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        String url = prefs.getString("pref_url", "");

        HashSet<String> items = new HashSet<>();

        boolean flashEnabled = mFlashService != null && prefs.getBoolean("pref_flash_enabled", false);
        if (flashEnabled) {
            items.add(prefs.getString("pref_flash_item", ""));
        }

        boolean screenOnEnabled = prefs.getBoolean("pref_screen_enabled", false);
        if (screenOnEnabled) {
            items.add(prefs.getString("pref_screen_item", ""));
        }

        sseClient = new SSEClient(url, items);
        sseClient.setConnectionListener(this);
        if (mFlashService != null) {
            sseClient.addStateListener(mFlashService);
        }
        if (mScreenService != null) {
            sseClient.addStateListener(mScreenService);
        }
        sseClient.connect();
        Log.i("Habpanelview", "EventSource started");
    }

    private synchronized void stopEventSource() {
        if (sseClient != null) {
            if (mFlashService != null) {
                sseClient.removeStateListener(mFlashService);
            }
            if (mScreenService != null) {
                sseClient.removeStateListener(mScreenService);
            }
            sseClient.close();
            sseClient = null;
        }
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
}
