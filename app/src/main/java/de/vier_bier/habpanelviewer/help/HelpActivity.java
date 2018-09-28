package de.vier_bier.habpanelviewer.help;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;

import com.mukesh.MarkdownView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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


        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String theme = prefs.getString("pref_theme", "dark");

        loadMarkdownFromAssets(getString(R.string.helpFile), "dark".equals(theme));
    }

    public void loadMarkdownFromAssets(String assetsFilePath, boolean dark) {
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
