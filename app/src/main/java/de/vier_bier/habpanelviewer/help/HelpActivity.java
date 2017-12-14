package de.vier_bier.habpanelviewer.help;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

import com.mukesh.MarkdownView;

import de.vier_bier.habpanelviewer.R;

/**
 * Activity showing the help markdown file.
 */
public class HelpActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        setContentView(R.layout.help_main);

        final MarkdownView markdownView = findViewById(R.id.activity_help_webview);
        markdownView.loadMarkdownFromAssets("help.md");
    }
}
