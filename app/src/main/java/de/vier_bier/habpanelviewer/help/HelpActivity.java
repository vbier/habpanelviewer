package de.vier_bier.habpanelviewer.help;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.mukesh.MarkdownView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.ScreenControllingActivity;
import de.vier_bier.habpanelviewer.UiUtil;

/**
 * Activity showing the help markdown file.
 */
public class HelpActivity extends ScreenControllingActivity {
    private MenuItem mForumItem;
    private MenuItem mWikiItem;
    private MenuItem mFileItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.help_main);

        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String theme = prefs.getString("pref_theme", "dark");

        if ("dark".equals(theme)) {
            myToolbar.setPopupTheme(R.style.Theme_AppCompat_NoActionBar);
        } else {
            myToolbar.setPopupTheme(R.style.Theme_AppCompat_Light_NoActionBar);
        }

        showHelp();
    }

    private void showHelp() {
        if (mFileItem != null) {
            mFileItem.setEnabled(false);
        }

        if (mForumItem != null) {
            mForumItem.setEnabled(true);
        }

        if (mWikiItem != null) {
            mWikiItem.setEnabled(true);
        }

        UiUtil.tintItemPreV21(mForumItem, getApplicationContext(), getTheme());
        UiUtil.tintItemPreV21(mFileItem, getApplicationContext(), getTheme());
        UiUtil.tintItemPreV21(mWikiItem, getApplicationContext(), getTheme());

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String theme = prefs.getString("pref_theme", "dark");

        loadMarkdownFromAssets(getString(R.string.helpFile), "dark".equals(theme));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.help_toolbar_menu, menu);

        mForumItem = menu.findItem(R.id.action_goto_forum);
        mWikiItem = menu.findItem(R.id.action_goto_wiki);
        mFileItem = menu.findItem(R.id.action_show_help);

        UiUtil.tintItemPreV21(mForumItem, getApplicationContext(), getTheme());
        UiUtil.tintItemPreV21(mWikiItem, getApplicationContext(), getTheme());
        UiUtil.tintItemPreV21(mFileItem, getApplicationContext(), getTheme());

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_goto_forum) {
            mFileItem.setEnabled(true);
            mWikiItem.setEnabled(true);
            mForumItem.setEnabled(false);

            UiUtil.tintItemPreV21(mForumItem, getApplicationContext(), getTheme());
            UiUtil.tintItemPreV21(mWikiItem, getApplicationContext(), getTheme());
            UiUtil.tintItemPreV21(mFileItem, getApplicationContext(), getTheme());

            final MarkdownView markdownView = findViewById(R.id.activity_help_webview);
            markdownView.loadUrl("https://community.openhab.org/t/habpanelviewer/34112/");
            return true;
        }

        if (id == R.id.action_goto_wiki) {
            mFileItem.setEnabled(true);
            mWikiItem.setEnabled(false);
            mForumItem.setEnabled(true);

            UiUtil.tintItemPreV21(mForumItem, getApplicationContext(), getTheme());
            UiUtil.tintItemPreV21(mWikiItem, getApplicationContext(), getTheme());
            UiUtil.tintItemPreV21(mFileItem, getApplicationContext(), getTheme());

            final MarkdownView markdownView = findViewById(R.id.activity_help_webview);
            markdownView.loadUrl("https://github.com/vbier/habpanelviewer/wiki");
            return true;
        }

        if (id == R.id.action_show_help) {
            showHelp();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void loadMarkdownFromAssets(String assetsFilePath, boolean dark) {
        try {
            StringBuilder buf = new StringBuilder();
            if (dark) {
                buf.append("<style> body { background:black; color:white } a { color: lightblue; } </style>\n");
            }
            InputStream json = getAssets().open(assetsFilePath);
            BufferedReader in = new BufferedReader(new InputStreamReader(json, "UTF-8"));

            String str;
            while((str = in.readLine()) != null) {
                buf.append(str).append("\n");
            }

            in.close();

            final MarkdownView markdownView = findViewById(R.id.activity_help_webview);
            markdownView.setMarkDownText(buf.toString());
        } catch (IOException var6) {
            var6.printStackTrace();
        }
    }

    @Override
    public View getScreenOnView() {
        return findViewById(R.id.activity_help_webview);
    }
}
