package de.vier_bier.habpanelviewer.help;

import android.os.Bundle;
import android.view.View;

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

        setContentView(R.layout.help_main);

        final MarkdownView markdownView = findViewById(R.id.activity_help_webview);
        markdownView.loadMarkdownFromAssets(getString(R.string.helpFile));
    }

    @Override
    public View getScreenOnView() {
        return findViewById(R.id.activity_help_webview);
    }
}
