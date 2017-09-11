package vier_bier.de.habpanelviewer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, StateListener, ConnectionListener {

    private WebView mWebView;
    private String flashItemState;
    private String screenOnItemState;
    private SSEClient sseClient;
    private boolean allowScrolling;
    private Pattern screenOnPattern;
    private Pattern flashOnPattern;
    private Pattern flashPulsatingPattern;

    private FlashControlService mFlashService;
    private PowerManager.WakeLock screenLock;

    //TODO.vb. validate regexes

    @Override
    protected void onDestroy() {
        stopEventSource();

        mFlashService.terminate();
        mFlashService = null;

        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        screenLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "HabpanelViewer");

        setContentView(R.layout.activity_main);

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        mFlashService = new FlashControlService((CameraManager) getSystemService(Context.CAMERA_SERVICE));

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

    @Override
    public void onStart() {
        super.onStart();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        boolean appEnabled = prefs.getBoolean("pref_app_enabled", false);

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        MenuItem i = navigationView.getMenu().findItem(R.id.action_start_app);
        i.setVisible(appEnabled);
        if (appEnabled) {
            i.setTitle("Launch " + prefs.getString("pref_app_name", ""));
        }

        allowScrolling = prefs.getBoolean("pref_scrolling", false);

        String pulsatingRegexpStr = prefs.getString("pref_flash_pulse_regex", "");
        flashPulsatingPattern = null;
        if (!pulsatingRegexpStr.isEmpty()) {
            try {
                flashPulsatingPattern = Pattern.compile(pulsatingRegexpStr);
            } catch (PatternSyntaxException e) {
                // is handled in the preferences
            }
        }

        String steadyRegexpStr = prefs.getString("pref_flash_steady_regex", "");
        flashOnPattern = null;
        if (!steadyRegexpStr.isEmpty()) {
            try {
                flashOnPattern = Pattern.compile(steadyRegexpStr);
            } catch (PatternSyntaxException e) {
                // is handled in the preferences
            }
        }

        String onRegexpStr = prefs.getString("pref_screen_on_regex", "");
        screenOnPattern = null;
        if (!onRegexpStr.isEmpty()) {
            try {
                screenOnPattern = Pattern.compile(onRegexpStr);
            } catch (PatternSyntaxException e) {
                // is handled in the preferences
            }
        }

        Boolean isDesktop = prefs.getBoolean("pref_desktop_mode", false);
        Boolean isJavascript = prefs.getBoolean("pref_javascript", false);

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setUseWideViewPort(isDesktop);
        webSettings.setLoadWithOverviewMode(isDesktop);
        webSettings.setJavaScriptEnabled(isJavascript);

        loadStartUrl();

        // make sure screen lock is released
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (screenLock.isHeld()) {
            screenLock.release();
        }

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
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            String app = prefs.getString("pref_app_package", "");

            if (!app.isEmpty()) {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(app);

                if (launchIntent != null) {
                    startActivity(launchIntent);
                } else {
                    Snackbar snackbar = Snackbar
                            .make(mWebView, "Could not find app for package " + app, Snackbar.LENGTH_LONG).setAction("CHANGE", new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    showPreferences();
                                }
                            });
                    snackbar.show();
                }
            }
        } else if (id == R.id.action_exit) {
            System.exit(0);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void showPreferences() {
        Intent intent = new Intent();
        intent.setClass(MainActivity.this, SetPreferenceActivity.class);
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
        String url = prefs.getString("pref_url", "http://openhabianpi:8080");

        HashSet<String> items = new HashSet<>();

        boolean flashEnabled = prefs.getBoolean("pref_flash_enabled", false);
        if (flashEnabled) {
            items.add(prefs.getString("pref_flash_item", "Alarm_State"));
        }

        boolean screenOnEnabled = prefs.getBoolean("pref_screen_enabled", false);
        if (screenOnEnabled) {
            items.add(prefs.getString("pref_screen_item", "gDoors"));
        }

        sseClient = new SSEClient(url, items);
        sseClient.setStateListener(this);
        sseClient.setConnectionListener(this);
        sseClient.connect();
        Log.i("Habpanelview", "EventSource started");
    }

    private synchronized void stopEventSource() {
        if (sseClient != null) {
            sseClient.close();
            sseClient = null;
        }
    }

    @Override
    public void updateState(String name, String value) {
        SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        String flashItemName = mySharedPreferences.getString("pref_flash_item", "Alarm_State");
        String screenOnItemName = mySharedPreferences.getString("pref_screen_item", "gDoors");

        if (name.equals(flashItemName)) {
            setFlashItemState(value);
        }

        if (name.equals(screenOnItemName)) {
            setScreenOnItemState(value);
        }
    }

    private void setScreenOnItemState(final String state) {
        if (screenOnItemState != null && screenOnItemState.equals(state)) {
            Log.i("Habpanelview", "unchanged screen on item state=" + state);
            return;
        }

        Log.i("Habpanelview", "screen on item state=" + state + ", old state=" + screenOnItemState);
        screenOnItemState = state;

        runOnUiThread(new Runnable() {
            public void run() {
                if (screenOnPattern != null && screenOnPattern.matcher(state).matches()) {
                    if (!screenLock.isHeld()) {
                        screenLock.acquire();
                        screenLock.release();
                    }
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }
        });
    }

    private void setFlashItemState(String state) {
        if (flashItemState != null && flashItemState.equals(state)) {
            Log.i("Habpanelview", "unchanged flash item state=" + state);
            return;
        }

        Log.i("Habpanelview", "flash item state=" + state + ", old state=" + flashItemState);
        flashItemState = state;

        if (flashOnPattern != null && flashOnPattern.matcher(state).matches()) {
            mFlashService.enableFlash();
        } else if (flashPulsatingPattern != null && flashPulsatingPattern.matcher(state).matches()) {
            mFlashService.pulseFlash();
        } else {
            mFlashService.disableFlash();
        }
    }

    @Override
    public void connected(final String url) {
        runOnUiThread(new Runnable() {
            public void run() {
                ((TextView) findViewById(R.id.textView)).setText(url);
            }
        });
    }

    @Override
    public void disconnected() {
        runOnUiThread(new Runnable() {
            public void run() {
                ((TextView) findViewById(R.id.textView)).setText(R.string.not_connected);
            }
        });
    }
}
