package de.vier_bier.habpanelviewer;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;

public class EmptyActivity extends ScreenControllingActivity {
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean dim = getIntent().getExtras() != null && getIntent().getExtras().getBoolean("dim");
        if (dim) {
            setContentView(R.layout.activity_empty);

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

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                final WindowManager.LayoutParams layout = getWindow().getAttributes();
                layout.screenBrightness = 0f;
                getWindow().setAttributes(layout);
            }, 500);
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
