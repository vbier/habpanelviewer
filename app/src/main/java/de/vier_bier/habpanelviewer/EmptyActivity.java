package de.vier_bier.habpanelviewer;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class EmptyActivity extends AppCompatActivity {
    private float mScreenBrightness = 1F;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_empty);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

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

        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                finish();
                return true;
            }
        });
    }

    @Override
    protected void onDestroy() {
        final WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = mScreenBrightness;
        getWindow().setAttributes(layout);

        super.onDestroy();
    }
}
