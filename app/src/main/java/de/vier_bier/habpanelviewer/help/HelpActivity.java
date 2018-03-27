package de.vier_bier.habpanelviewer.help;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.WindowManager;

import com.mukesh.MarkdownView;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.ScreenControllingActivity;

/**
 * Activity showing the help markdown file.
 */
public class HelpActivity extends ScreenControllingActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean showOnLockScreen = prefs.getBoolean("pref_show_on_lock_screen", false);
        if (showOnLockScreen) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        setContentView(R.layout.help_main);

        final MarkdownView markdownView = findViewById(R.id.activity_help_webview);
        markdownView.loadMarkdownFromAssets(getString(R.string.helpFile));
    }

    @Override
    public View getScreenOnView() {
        return findViewById(R.id.activity_help_webview);
    }
}
