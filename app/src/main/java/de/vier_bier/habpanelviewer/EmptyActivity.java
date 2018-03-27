package de.vier_bier.habpanelviewer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.WindowManager;

public class EmptyActivity extends ScreenControllingActivity {
    private float mScreenBrightness = 1F;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean dim = getIntent().getExtras() != null && getIntent().getExtras().getBoolean("dim");
        if (dim) {
            setContentView(R.layout.activity_empty);

            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            boolean showOnLockScreen = prefs.getBoolean("pref_show_on_lock_screen", false);
            if (showOnLockScreen) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            }

            final WindowManager.LayoutParams layout = getWindow().getAttributes();

            float screenBrightness = layout.screenBrightness;
            if (screenBrightness != 0) {
                mScreenBrightness = screenBrightness;
            }

            layout.screenBrightness = 0F;
            getWindow().setAttributes(layout);

            View view = findViewById(R.id.blankView);
            view.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

            view.setOnTouchListener((view1, motionEvent) -> {
                finish();
                return true;
            });
        } else {
            finish();
        }
    }

    @Override
    public View getScreenOnView() {
        return findViewById(R.id.blankView);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        boolean dim = intent.getExtras() != null && intent.getExtras().getBoolean("dim");
        if (!dim) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        final WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = mScreenBrightness;
        getWindow().setAttributes(layout);

        super.onDestroy();
    }
}
