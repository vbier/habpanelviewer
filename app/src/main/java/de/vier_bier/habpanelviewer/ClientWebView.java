package de.vier_bier.habpanelviewer;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.Build;
import androidx.appcompat.app.AlertDialog;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.EditText;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;

import de.vier_bier.habpanelviewer.db.CredentialsHelper;
import de.vier_bier.habpanelviewer.openhab.IConnectionListener;
import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;

/**
 * WebView
 */
public class ClientWebView extends WebView implements NetworkTracker.INetworkListener {
    private static final String TAG = "HPV-ClientWebView";

    private boolean mAllowMixedContent;
    private boolean mDraggingPrevented;
    private String mServerURL;
    private String mStartPage;
    private boolean mKioskMode;
    private boolean mHwAccelerated;
    private NetworkTracker mNetworkTracker;
    private boolean mDarkTheme;
    private boolean mImmersive;
    private boolean mTrackBrowserConnection;
    private boolean mLogBrowserMsg;

    public ClientWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            postDelayed(() -> setSystemUiVisibility(
                    (mImmersive ? View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY : 0)), 500);
        }
    }

    synchronized void initialize(final IConnectionListener cl, final NetworkTracker nt) {
        mNetworkTracker = nt;
        Log.d(TAG, "registering as network listener...");
        mNetworkTracker.addListener(this);

        setLayerType(LAYER_TYPE_SOFTWARE, null);

        setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (mTrackBrowserConnection && consoleMessage.message().contains("SSE error, closing EventSource")) {
                    cl.disconnected();
                }
                return !mLogBrowserMsg || super.onConsoleMessage(consoleMessage);
            }
        });

        setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (isHabPanelUrl(url)) {
                    mKioskMode = url.toLowerCase().contains("kiosk=on");

                    if (!mKioskMode) {
                        evaluateJavascript("angular.element(document.body).scope().$root.kioskMode",
                                s -> mKioskMode = Boolean.parseBoolean(s));
                    }
                    Log.d(TAG, "habpanel page loaded. url=" + url + ", kioskMode=" + mKioskMode);
                }
            }

            @Override
            public void onReceivedSslError(WebView view, final SslErrorHandler handler, final SslError error) {
                Log.d(TAG, "onReceivedSslError: " + error.getUrl());

                SslCertificate cert = error.getCertificate();
                if (ConnectionUtil.getInstance().isTrusted(error.getCertificate())) {
                    Log.d(TAG, "certificate is trusted: " + error.getUrl());

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

                AlertDialog alert = new AlertDialog.Builder(getContext())
                        .setTitle(getContext().getString(R.string.certInvalid))
                        .setMessage(getContext().getString(R.string.sslCert) + " https://" + host + " " + reason + ".\n\n"
                                + certInfo.replaceAll("<br/>", "\n") + "\n"
                                + getContext().getString(R.string.storeSecurityException))
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                            try {
                                ConnectionUtil.getInstance().addCertificate(error.getCertificate());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            handler.proceed();
                        })
                        .setNegativeButton(android.R.string.no, (dialog, whichButton) -> loadData("<html><body><h1>" + getContext().getString(R.string.certInvalid)
                                + "</h1><h2>" + getContext().getString(R.string.sslCert) + "https://" + host + " "
                                + reason + ".</h2>" + certInfo + "</body></html>", "text/html", "UTF-8")).create();

                if (getContext() != null && !((Activity) getContext()).isFinishing()) {
                    alert.show();
                }
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView view, final HttpAuthHandler handler, final String host, final String realm) {
                Log.i(TAG, "realm " + realm);
                CredentialsHelper.getInstance(getContext()).handleAuthRequest(host, realm, handler, () -> {
                    final AlertDialog alert = new AlertDialog.Builder(getContext())
                            .setCancelable(false)
                            .setTitle(R.string.credentials_required)
                            .setMessage(getContext().getString(R.string.host_realm, host, realm))
                            .setView(R.layout.dialog_credentials)
                            .setPositiveButton(R.string.okay, (dialog12, id) -> {
                                EditText userT = ((AlertDialog) dialog12).findViewById(R.id.username);
                                EditText passT = ((AlertDialog) dialog12).findViewById(R.id.password);
                                CheckBox storeCB = ((AlertDialog) dialog12).findViewById(R.id.checkBox);

                                if (storeCB.isChecked()) {
                                    CredentialsHelper.getInstance(getContext()).registerCredentials(host, realm, userT.getText().toString(), passT.getText().toString());
                                }

                                handler.proceed(userT.getText().toString(), passT.getText().toString());
                            }).setNegativeButton(R.string.cancel, (dialog1, which) -> handler.cancel())
                            .create();

                    if (getContext() != null && !((Activity) getContext()).isFinishing()) {
                        alert.show();
                    }
                });
            }
        });

        setOnTouchListener((v, event) -> (event.getAction() == MotionEvent.ACTION_MOVE && mDraggingPrevented));

        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);
        }

        WebSettings webSettings = getSettings();
        webSettings.setDomStorageEnabled(true);
    }

    public void loadStartUrl() {
        String url = mStartPage;
        if ("".equals(url)) {
            url = mServerURL;
        }

        if (url == null || "".equals(url)) {
            post(() -> showHtml(getContext().getString(R.string.configMissing),
                    getContext().getString(R.string.startPageNotSetHTML)));
            return;
        }

        final String startPage = url;
        mKioskMode = isHabPanelUrl(startPage) && startPage.toLowerCase().contains("kiosk=on");
        Log.d(TAG, "loadStartUrl: loading start page " + startPage + "...");

        post(() -> {
            if (getUrl() == null || !startPage.equalsIgnoreCase(getUrl())) {
                loadUrl(startPage);
            }
        });
    }

    void updateFromPreferences(SharedPreferences prefs) {
        String theme = prefs.getString("pref_theme", "dark");
        mDarkTheme = "dark".equals(theme);
        mImmersive = prefs.getBoolean("pref_immersive", false);
        mTrackBrowserConnection = prefs.getBoolean("pref_track_browser_connection", false);
        mLogBrowserMsg = prefs.getBoolean("pref_log_browser_messages", false);

        boolean isDesktop = prefs.getBoolean("pref_desktop_mode", false);
        boolean isJavascript = prefs.getBoolean("pref_javascript", false);
        boolean isAutoplay = prefs.getBoolean("pref_autoplay_video", false);
        boolean cacheDeactivated = prefs.getBoolean("pref_disable_cache", false);

        mDraggingPrevented = prefs.getBoolean("pref_prevent_dragging", false);

        WebSettings webSettings = getSettings();
        webSettings.setUseWideViewPort(isDesktop);
        webSettings.setLoadWithOverviewMode(isDesktop);
        webSettings.setJavaScriptEnabled(isJavascript);
        webSettings.setCacheMode(cacheDeactivated ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_DEFAULT);
        webSettings.setMediaPlaybackRequiresUserGesture(!isAutoplay);

        boolean loadStartUrl = false;
        boolean reloadUrl = false;
        if (mStartPage == null || !mStartPage.equalsIgnoreCase(prefs.getString("pref_start_url", ""))) {
            mStartPage = prefs.getString("pref_start_url", "");
            loadStartUrl = true;
        }
        loadStartUrl = loadStartUrl || isShowingErrorPage();

        if (mServerURL == null || !mServerURL.equalsIgnoreCase(prefs.getString("pref_server_url", "!$%"))) {
            mServerURL = prefs.getString("pref_server_url", "");
            loadStartUrl = loadStartUrl || mStartPage == null || mStartPage.isEmpty();
        }
        if (mAllowMixedContent != prefs.getBoolean("pref_allow_mixed_content", false)) {
            mAllowMixedContent = prefs.getBoolean("pref_allow_mixed_content", false);
            reloadUrl = true;
        }

        if (mHwAccelerated != prefs.getBoolean("pref_hardware_accelerated", false)) {
            mHwAccelerated = prefs.getBoolean("pref_hardware_accelerated", false);

            if (mHwAccelerated) {
                setLayerType(LAYER_TYPE_HARDWARE, null);
            } else {
                setLayerType(LAYER_TYPE_SOFTWARE, null);
            }
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(mAllowMixedContent ? WebSettings.MIXED_CONTENT_ALWAYS_ALLOW : WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }

        if (mNetworkTracker.isConnected()) {
            if (loadStartUrl) {
                loadStartUrl();
            } else if (reloadUrl) {
                reload();
            }
        } else {
            showHtml(getContext().getString(R.string.waitingNetwork),
                    getContext().getString(R.string.notConnectedReloadPendingHTML));
        }
    }

    private void showHtml(String title, String text) {
        String bgColor = "white";
        String fgColor = "black";
        if (mDarkTheme) {
            bgColor = "black";
            fgColor = "white";
        }

        loadData("<html><body style=\"background-color:" + bgColor
                + "; color:" + fgColor + " \"><h1>" + title + "</h1><h2>" + text
                + "</h2></body></html>", "text/html", "UTF-8");
    }

    public void unregister() {
        Log.d(TAG, "unregistering as network listener...");
        mNetworkTracker.removeListener(this);
    }

    @Override
    public void reload() {
        if (isShowingHabPanel()) {
            Log.d(TAG, "reloading habpanel: mKioskMode = " + mKioskMode + ", url = " + getUrl());

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

                    Log.d(TAG, "loading url = " + urlStr);
                    loadUrl(urlStr);
                    return;
                }
            } catch (URISyntaxException e) {
                // call super.reload below
            }
        }

        Log.d(TAG, "reloading page");
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
        CredentialsHelper.getInstance(getContext()).clearCredentials();
    }

    public boolean isShowingHabPanel() {
        return isHabPanelUrl(getUrl());
    }

    public void toggleKioskMode() {
        Log.d(TAG, "toggleKioskMode: " + mKioskMode + "->" + !mKioskMode);

        mKioskMode = !mKioskMode;
        reload();
    }

    private boolean isShowingErrorPage() {
        String url = getUrl();

        return url != null && url.startsWith("data:");
    }

    private boolean isHabPanelUrl(final String url) {
        return url != null && url.toLowerCase().contains("/habpanel/");
    }

    public void loadDashboard(String panelName) {
        loadUrl(mServerURL + "/habpanel/index.html#/view/" + panelName + "?kiosk=" + (mKioskMode ? "on" : "off"));
    }

    @Override
    public boolean canGoBack() {
        return canGoBackOrForward(-1);
    }

    @Override
    public void goBack() {
        goBackOrForward(-1);
    }

    @Override
    public boolean canGoForward() {
        return canGoBackOrForward(1);
    }

    @Override
    public void goForward() {
        goBackOrForward(1);
    }

    @Override
    public boolean canGoBackOrForward(int steps) {
        int increment = steps < 0 ? -1 : 1;

        WebBackForwardList list = copyBackForwardList();

        int count = 0;
        int startIdx = list.getCurrentIndex();
        for (int i = startIdx + increment; i < list.getSize() && i >= 0; i += increment) {
            WebHistoryItem item = list.getItemAtIndex(i);

            if (!item.getOriginalUrl().startsWith("data:")) {
                count += increment;

                if (count == steps) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void goBackOrForward(int steps) {
        int increment = steps < 0 ? -1 : 1;

        WebBackForwardList list = copyBackForwardList();

        int count = 0;
        int intCount = 0;
        int startIdx = list.getCurrentIndex();
        for (int i = startIdx + increment; i < list.getSize() && i >= 0; i += increment) {
            intCount += increment;
            WebHistoryItem item = list.getItemAtIndex(i);

            if (!item.getOriginalUrl().startsWith("data:")) {
                count += increment;

                if (count == steps) {
                    super.goBackOrForward(intCount);
                    return;
                }
            }
        }
    }

    @Override
    public void disconnected() {
        Log.d(TAG, "disconnected: showing error message...");
        showHtml(getContext().getString(R.string.waitingNetwork),
                getContext().getString(R.string.notConnectedReloadPendingHTML));
    }

    @Override
    public void connected() {
        Log.d(TAG, "connected: loading start URL...");
        loadStartUrl();
    }
}
