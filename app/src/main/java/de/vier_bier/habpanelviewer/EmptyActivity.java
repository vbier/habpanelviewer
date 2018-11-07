package de.vier_bier.habpanelviewer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

public class EmptyActivity extends ScreenControllingActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean dim = getIntent().getExtras() != null && getIntent().getExtras().getBoolean("dim");
        if (dim) {
            setContentView(R.layout.activity_empty);

            final WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
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
}
