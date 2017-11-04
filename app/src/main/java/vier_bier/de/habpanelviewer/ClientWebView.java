package vier_bier.de.habpanelviewer;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * WebView
 */
public class ClientWebView extends WebView {
    private boolean mScrollingAllowed;
    private boolean mIgnoreCertErrors;
    private boolean mKioskMode;
    private String mServerURL;
    private String mStartPanel;

    private BroadcastReceiver mNetworkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

            if (activeNetwork != null && activeNetwork.isConnectedOrConnecting()) {
                loadStartUrl();
            } else {
                loadData("<html><body><h1>Waiting for network connection...</h1><h2>The device is currently not connected to the network. Once the connection has been established, the configured HABPanel page will automatically be loaded.</h2></body></html>", "text/html", "UTF-8");
            }
        }
    };

    public ClientWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ClientWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ClientWebView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ClientWebView(Context context) {
        super(context);
    }

    synchronized void initialize() {
        setWebChromeClient(new WebChromeClient());

        setWebViewClient(new WebViewClient() {
            /**
             * Ignores certificate errors on the configure server URL.
             */
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                if (mIgnoreCertErrors) {
                    if (error.getUrl().toLowerCase().startsWith(mServerURL.toLowerCase())) {
                        handler.proceed();
                    }
                } else {
                    String host = "unknown host";
                    try {
                        URL url = new URL(error.getUrl());
                        host = url.getHost();
                    } catch (MalformedURLException e) {
                    }

                    String reason = "is not valid";
                    switch (error.getPrimaryError()) {
                        case SslError.SSL_DATE_INVALID:
                            reason = "has an invalid date";
                            break;
                        case SslError.SSL_EXPIRED:
                            reason = "has expired";
                            break;
                        case SslError.SSL_IDMISMATCH:
                            reason = "has a hostname mismatch";
                            break;
                        case SslError.SSL_NOTYETVALID:
                            reason = "is not yet valid";
                            break;
                        case SslError.SSL_UNTRUSTED:
                            reason = "is untrusted";
                            break;
                    }

                    SslCertificate cert = error.getCertificate();
                    String certInfo = cert.toString().replaceAll(";", "<br/>").replaceAll(",", "<br/>&nbsp;");
                    certInfo += "Valid from: " + cert.getValidNotBefore() + "<br/>";
                    certInfo += "Valid until: " + cert.getValidNotAfter() + "<br/>";
                    loadData("<html><body><h1>SSL Certificate Invalid!</h1><h2>The SSL Certificate served by https://" + host + " " + reason + ".</h2>" + certInfo + "</body></html>", "text/html", "UTF-8");
                }
            }
        });

        setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return (event.getAction() == MotionEvent.ACTION_MOVE && !mScrollingAllowed);
            }
        });

        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);
        }

        WebSettings webSettings = getSettings();
        webSettings.setDomStorageEnabled(true);

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        getContext().registerReceiver(mNetworkReceiver, intentFilter);
    }

    void loadStartUrl() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        String url = mServerURL;
        if ("".equals(mServerURL)) {
            post(new Runnable() {
                @Override
                public void run() {
                    loadData("<html><body><h1>Configuration missing</h1><h2>The URL of the openHAB server has not yet been configured. Please specify it in the applications settings.</h2></body></html>", "text/html", "UTF-8");
                }
            });
            return;
        }

        String panel = prefs.getString("pref_panel", "");
        url += "/habpanel/index.html#/";

        if (!panel.isEmpty()) {
            url += "view/" + panel;
        }

        Boolean isKiosk = prefs.getBoolean("pref_kiosk_mode", false);
        if (isKiosk) {
            url += "?kiosk=on";
        } else {
            url += "?kiosk=off";
        }

        final String newUrl = url;
        post(new Runnable() {
            @Override
            public void run() {
                if (getUrl() == null || !newUrl.equalsIgnoreCase(getUrl())) {
                    loadUrl(newUrl);
                }
            }
        });
    }

    void updateFromPreferences(SharedPreferences prefs) {
        Boolean isDesktop = prefs.getBoolean("pref_desktop_mode", false);
        Boolean isJavascript = prefs.getBoolean("pref_javascript", false);
        mScrollingAllowed = prefs.getBoolean("pref_scrolling", true);
        mIgnoreCertErrors = prefs.getBoolean("pref_ignore_ssl_errors", false);

        WebSettings webSettings = getSettings();
        webSettings.setUseWideViewPort(isDesktop);
        webSettings.setLoadWithOverviewMode(isDesktop);
        webSettings.setJavaScriptEnabled(isJavascript);

        ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        boolean loadStartUrl = false;
        if (mServerURL == null || !mServerURL.equalsIgnoreCase(prefs.getString("pref_url", "!$%"))) {
            mServerURL = prefs.getString("pref_url", "");
            loadStartUrl = true;
        }

        if (mStartPanel == null || !mStartPanel.equalsIgnoreCase(prefs.getString("pref_panel", ""))) {
            mStartPanel = prefs.getString("pref_panel", "");
            loadStartUrl = true;
        }

        if (mKioskMode != prefs.getBoolean("pref_kiosk_mode", false)) {
            mKioskMode = prefs.getBoolean("pref_kiosk_mode", false);
            loadStartUrl = true;
        }

        if (activeNetwork != null && activeNetwork.isConnectedOrConnecting()) {
            if (loadStartUrl) {
                loadStartUrl();
            }
        } else {
            loadData("<html><body><h1>Waiting for network connection...</h1><h2>The device is currently not connected to the network. Once the connection has been established, the configured HABPanel page will automatically be loaded.</h2></body></html>", "text/html", "UTF-8");
        }
    }

    @Override
    public void destroy() {
        getContext().unregisterReceiver(mNetworkReceiver);

        super.destroy();
    }
}
