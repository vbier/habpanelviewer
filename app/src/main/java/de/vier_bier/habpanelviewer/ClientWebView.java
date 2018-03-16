package de.vier_bier.habpanelviewer;

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
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;
import android.widget.EditText;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;

import de.vier_bier.habpanelviewer.openhab.IConnectionListener;
import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;

/**
 * WebView
 */
public class ClientWebView extends WebView {
    private boolean mAllowMixedContent;
    private boolean mDraggingPrevented;
    private String mServerURL;
    private String mStartPage;
    private boolean mKioskMode;

    private final BroadcastReceiver mNetworkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm == null ? null : cm.getActiveNetworkInfo();

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

    synchronized void initialize(final IConnectionListener cl) {
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
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (isHabPanelUrl(url)) {
                    evaluateJavascript("angular.element(document.body).scope().$root.kioskMode", s -> {
                        mKioskMode = Boolean.parseBoolean(s);
                        Log.d("Kiosk", "HABPanel page loaded. kioskMode=" + mKioskMode);
                    });
                }
            }

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
                        .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                            try {
                                ConnectionUtil.addCertificate(error.getCertificate());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            handler.proceed();
                        })
                        .setNegativeButton(android.R.string.no, (dialog, whichButton) -> loadData("<html><body><h1>" + getContext().getString(R.string.certInvalid)
                                + "</h1><h2>" + getContext().getString(R.string.sslCert) + "https://" + host + " "
                                + reason + ".</h2>" + certInfo + "</body></html>", "text/html", "UTF-8")).show();
            }


            @Override
            public void onReceivedHttpAuthRequest(WebView view, final HttpAuthHandler handler, final String host, final String realm) {
                AlertDialog.Builder dialog = new AlertDialog.Builder(getContext())
                        .setCancelable(false)
                        .setTitle(R.string.login_required)
                        .setMessage(getContext().getString(R.string.host_realm, host, realm))
                        .setView(R.layout.dialog_login)
                        .setPositiveButton(R.string.okay, (dialog12, id) -> {
                            EditText userT = ((AlertDialog) dialog12).findViewById(R.id.username);
                            EditText passT = ((AlertDialog) dialog12).findViewById(R.id.password);

                            handler.proceed(userT.getText().toString(), passT.getText().toString());
                        }).setNegativeButton(R.string.cancel, (dialog1, which) -> handler.cancel());

                final AlertDialog alert = dialog.create();
                alert.show();
            }
        });

        setOnTouchListener((v, event) -> (event.getAction() == MotionEvent.ACTION_MOVE && mDraggingPrevented));

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

    public void loadStartUrl() {
        String url = mStartPage;
        if ("".equals(url)) {
            url = mServerURL;
        }

        if ("".equals(url)) {
            post(() -> loadData("<html><body><h1>" + getContext().getString(R.string.configMissing)
                    + "</h1><h2>" + getContext().getString(R.string.startPageNotSet) + "."
                    + getContext().getString(R.string.specifyUrlInSettings)
                    + "</h2></body></html>", "text/html", "UTF-8"));
            return;
        }

        final String startPage = url;
        mKioskMode = isHabPanelUrl(startPage) && startPage.toLowerCase().contains("kiosk=on");
        post(() -> {
            if (getUrl() == null || !startPage.equalsIgnoreCase(getUrl())) {
                loadUrl(startPage);
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
        NetworkInfo activeNetwork = cm == null ? null : cm.getActiveNetworkInfo();

        boolean loadStartUrl = false;
        boolean reloadUrl = false;
        if (mStartPage == null || !mStartPage.equalsIgnoreCase(prefs.getString("pref_start_url", ""))) {
            mStartPage = prefs.getString("pref_start_url", "");
            loadStartUrl = true;
        }
        if (mServerURL == null || !mServerURL.equalsIgnoreCase(prefs.getString("pref_server_url", "!$%"))) {
            mServerURL = prefs.getString("pref_server_url", "");
            loadStartUrl = mStartPage == null || mStartPage.isEmpty();
        }
        if (mAllowMixedContent != prefs.getBoolean("pref_allow_mixed_content", false)) {
            mAllowMixedContent = prefs.getBoolean("pref_allow_mixed_content", false);
            reloadUrl = true;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(mAllowMixedContent ? WebSettings.MIXED_CONTENT_ALWAYS_ALLOW : WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }

        if (activeNetwork != null && activeNetwork.isConnectedOrConnecting()) {
            if (loadStartUrl) {
                loadStartUrl();
            } else if (reloadUrl) {
                reload();
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

    @Override
    public void reload() {
        if (isShowingHabPanel()) {
            Log.d("Kiosk", "reloading habpanel: mKioskMode = " + mKioskMode + ", url = " + getUrl());

            String urlStr = getUrl();

            try {
                URI uri = new URI(urlStr);
                String fragment = uri.getFragment();
                boolean urlMatchesKioskMode;

                if (mKioskMode) {
                    urlMatchesKioskMode = fragment != null && fragment.contains("kiosk=on");
                } else {
                    urlMatchesKioskMode = fragment == null || !fragment.contains("kiosk=on");
                }

                if (!urlMatchesKioskMode) {
                    String[] fragParts = fragment == null ? new String[]{""} : fragment.split("\\?");
                    fragment = fragParts[0] + "?kiosk=" + (mKioskMode ? "on" : "off");

                    urlStr = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(),
                            uri.getQuery(), fragment).toString();

                    Log.d("Kiosk", "loading url = " + urlStr);
                    loadUrl(urlStr);
                    return;
                }
            } catch (URISyntaxException e) {
                // call super.reload below
            }
        }

        Log.d("Kiosk", "reloading page");
        super.reload();
    }

    public void enterUrl(Context ctx) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle("Enter URL");

        final EditText input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(getUrl());
        input.selectAll();
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String url = input.getText().toString();
            mKioskMode = isHabPanelUrl(url) && url.toLowerCase().contains("kiosk=on");
            loadUrl(url);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    public void clearPasswords() {
        WebViewDatabase.getInstance(getContext()).clearHttpAuthUsernamePassword();
        clearCache(true);
    }

    public boolean isShowingHabPanel() {
        return isHabPanelUrl(getUrl());
    }

    public void toggleKioskMode() {
        Log.d("Kiosk", "toggleKioskMode: " + mKioskMode + "->" + !mKioskMode);

        mKioskMode = !mKioskMode;
        reload();
    }

    private boolean isHabPanelUrl(final String url) {
        return url != null && url.toLowerCase().contains("/habpanel/");
    }

    public void loadDashboard(String panelName) {
        loadUrl(mServerURL + "/habpanel/index.html#/view/" + panelName + "?kiosk=" + (mKioskMode ? "on" : "off"));
    }
}
