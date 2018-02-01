package de.vier_bier.habpanelviewer;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;

import de.vier_bier.habpanelviewer.openhab.ConnectionListener;
import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;

/**
 * WebView
 */
public class ClientWebView extends WebView {
    private boolean mDraggingPrevented;
    private boolean mKioskMode;
    private String mServerURL;
    private String mStartPanel;

    private final BroadcastReceiver mNetworkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

            if (activeNetwork != null && activeNetwork.isConnectedOrConnecting()) {
                loadStartUrl();
            } else {
                loadData("<html><body><h1>" + getContext().getString(R.string.waitingNetwork)
                        + "</h1><h2>" + getContext().getString(R.string.notConnected)
                        + "</h2></body></html>", "text/html", "UTF-8");
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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void setKeepScreenOn(boolean keepScreenOn) {
        // disable chromium power save blocker
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        for (StackTraceElement ele : stackTraceElements) {
            if (ele.getClassName().contains("PowerSaveBlocker")) {
                return;
            }
        }

        super.setKeepScreenOn(keepScreenOn);
    }

    synchronized void initialize(final ConnectionListener cl) {
        setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (consoleMessage.message().contains("SSE error, closing EventSource")) {
                    cl.disconnected();
                }

                return super.onConsoleMessage(consoleMessage);
            }
        });

        setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, final SslErrorHandler handler, final SslError error) {
                Log.d("SSL", "onReceivedSslError: " + error.getUrl());

                SslCertificate cert = error.getCertificate();
                if (ConnectionUtil.isTrusted(error.getCertificate())) {
                    Log.d("SSL", "certificate is trusted: " + error.getUrl());

                    handler.proceed();
                    return;
                }

                String h;
                try {
                    URL url = new URL(error.getUrl());
                    h = url.getHost();
                } catch (MalformedURLException e) {
                    h = getContext().getString(R.string.unknownHost);
                }

                final String host = h;

                String r = getContext().getString(R.string.notValid);
                switch (error.getPrimaryError()) {
                    case SslError.SSL_DATE_INVALID:
                        r = getContext().getString(R.string.invalidDate);
                        break;
                    case SslError.SSL_EXPIRED:
                        r = getContext().getString(R.string.expired);
                        break;
                    case SslError.SSL_IDMISMATCH:
                        r = getContext().getString(R.string.hostnameMismatch);
                        break;
                    case SslError.SSL_NOTYETVALID:
                        r = getContext().getString(R.string.notYetValid);
                        break;
                    case SslError.SSL_UNTRUSTED:
                        r = getContext().getString(R.string.untrusted);
                        break;
                }

                final String reason = r;

                String c = getContext().getString(R.string.issuedBy) + cert.getIssuedBy().getDName() + "<br/>";
                c += getContext().getString(R.string.issuedTo) + cert.getIssuedTo().getDName() + "<br/>";
                c += getContext().getString(R.string.validFrom) + SimpleDateFormat.getDateInstance().format(cert.getValidNotBeforeDate()) + "<br/>";
                c += getContext().getString(R.string.validUntil) + SimpleDateFormat.getDateInstance().format(cert.getValidNotAfterDate()) + "<br/>";

                final String certInfo = c;

                new AlertDialog.Builder(ClientWebView.this.getContext())
                        .setTitle(getContext().getString(R.string.certInvalid))
                        .setMessage(getContext().getString(R.string.sslCert) + "https://" + host + " " + reason + ".\n\n"
                                + certInfo.replaceAll("<br/>", "\n") + "\n"
                                + getContext().getString(R.string.storeSecurityException))
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                try {
                                    ConnectionUtil.addCertificate(error.getCertificate());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                handler.proceed();
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                loadData("<html><body><h1>" + getContext().getString(R.string.certInvalid)
                                        + "</h1><h2>" + getContext().getString(R.string.sslCert) + "https://" + host + " "
                                        + reason + ".</h2>" + certInfo + "</body></html>", "text/html", "UTF-8");
                            }
                        }).show();
            }
        });

        setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return (event.getAction() == MotionEvent.ACTION_MOVE && mDraggingPrevented);
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
                    loadData("<html><body><h1>" + getContext().getString(R.string.configMissing)
                            + "</h1><h2>" + getContext().getString(R.string.serverNotFound) + "."
                            + getContext().getString(R.string.specifyServerInSettings)
                            + "</h2></body></html>", "text/html", "UTF-8");
                }
            });
            return;
        }

        String panel = prefs.getString("pref_panel", "");
        url += "/habpanel/index.html#/";

        if (!panel.isEmpty()) {
            url += "view/" + panel;

            Boolean isKiosk = prefs.getBoolean("pref_kiosk_mode", false);
            if (isKiosk) {
                url += "?kiosk=on";
            }
        }

        final String newUrl = url;
        post(new Runnable() {
            @Override
            public void run() {
                if (getUrl() == null || !newUrl.equalsIgnoreCase(getUrl())) {
                    loadUrl("about:blank");
                    loadUrl(newUrl);
                }
            }
        });
    }

    void updateFromPreferences(SharedPreferences prefs) {
        Boolean isDesktop = prefs.getBoolean("pref_desktop_mode", false);
        Boolean isJavascript = prefs.getBoolean("pref_javascript", false);
        mDraggingPrevented = prefs.getBoolean("pref_prevent_dragging", false);

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
            loadData("<html><body><h1>" + getContext().getString(R.string.waitingNetwork)
                    + "</h1><h2>" + getContext().getString(R.string.notConnected)
                    + "</h2></body></html>", "text/html", "UTF-8");
        }
    }

    public void unregister() {
        getContext().unregisterReceiver(mNetworkReceiver);
    }
}
