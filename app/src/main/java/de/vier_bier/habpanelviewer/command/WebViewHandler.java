package de.vier_bier.habpanelviewer.command;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.vier_bier.habpanelviewer.ClientWebView;

/**
 * Handler for SHOW_START_URL, SHOW_URL, SHOW_DASHBOARD, RELOAD command.
 */
public class WebViewHandler implements ICommandHandler {
    private final Pattern SHOW_PATTERN = Pattern.compile("SHOW_(URL|DASHBOARD) (.+)");

    private ClientWebView mWebView;

    public WebViewHandler(ClientWebView webView) {
        mWebView = webView;
    }

    @Override
    public boolean handleCommand(String cmd) {
        final Matcher m = SHOW_PATTERN.matcher(cmd);
        if (m.matches()) {
            final String type = m.group(1);

            mWebView.post(() -> {
                if ("URL".matches(type)) {
                    mWebView.loadUrl(m.group(2));
                } else {
                    mWebView.loadDashboard(m.group(2));
                }
            });
        } else if ("SHOW_START_URL".equals(cmd)) {
            mWebView.post(() -> mWebView.loadStartUrl());
        } else if ("RELOAD".equals(cmd)) {
            mWebView.post(() -> mWebView.reload());
        } else {
            return false;
        }

        return true;
    }
}
