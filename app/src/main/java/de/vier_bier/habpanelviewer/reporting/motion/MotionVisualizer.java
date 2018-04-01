package de.vier_bier.habpanelviewer.reporting.motion;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.support.design.widget.NavigationView;
import android.view.SurfaceView;

import java.util.ArrayList;

import de.vier_bier.habpanelviewer.R;

/**
 * Visualizes motion areas on the given mSurface view.
 */
public class MotionVisualizer implements IMotionListener {
    private final SurfaceView mMotionView;
    private final NavigationView mNavigationView;
    private final SharedPreferences mPreferences;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int mMotionTextWidth;
    private final int mDarkTextWidth;

    private final int mCameraRotation;
    private int mCorrectionAngle;

    public MotionVisualizer(SurfaceView motionView, NavigationView navigationView, SharedPreferences preferences, int cameraRotation, int scaledSize) {
        mMotionView = motionView;
        mNavigationView = navigationView;
        mPreferences = preferences;
        mCameraRotation = cameraRotation;

        int newDeviceRotation = ((Activity) mMotionView.getContext()).getWindowManager().getDefaultDisplay().getRotation();
        setDeviceRotation(newDeviceRotation);

        mMotionView.setZOrderOnTop(true);
        mMotionView.getHolder().setFormat(PixelFormat.TRANSPARENT);

        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setTextSize(scaledSize);

        Rect bounds = new Rect();
        mPaint.getTextBounds(mNavigationView.getContext().getString(R.string.motion), 0, 6, bounds);
        mMotionTextWidth = bounds.width();

        mPaint.getTextBounds(mNavigationView.getContext().getString(R.string.tooDark), 0, 8, bounds);
        mDarkTextWidth = bounds.width();
    }

    @Override
    public void motionDetected(ArrayList<Point> differing) {
        boolean showPreview = mPreferences.getBoolean("pref_motion_detection_preview", false);
        boolean motionDetection = mPreferences.getBoolean("pref_motion_detection_enabled", false);

        if (showPreview && motionDetection && mMotionView.getHolder().getSurface().isValid()) {
            final Canvas canvas = mMotionView.getHolder().lockCanvas();

            if (canvas != null) {
                try {
                    int boxes = Integer.parseInt(mPreferences.getString("pref_motion_detection_granularity", "20"));
                    float xsize = canvas.getWidth() / (float) boxes;
                    float ysize = canvas.getHeight() / (float) boxes;

                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

                    if (mNavigationView.isShown()) {
                        Rect r1 = new Rect();
                        mNavigationView.getGlobalVisibleRect(r1);

                        Rect r2 = new Rect();
                        mMotionView.getGlobalVisibleRect(r2);

                        if (r1.left == 0) {
                            // drawer from left
                            canvas.clipRect(r1.right - r2.left, 0, r2.right, r2.bottom);
                        } else {
                            // drawer from right
                            canvas.clipRect(0, 0, r1.left - r2.left, r2.bottom);
                        }
                    }

                    canvas.drawText(mNavigationView.getContext().getString(R.string.motion), (canvas.getWidth() - mMotionTextWidth) / 2, 50, mPaint);

                    for (Point p : differing) {
                        Point c = correctSensorRotation(p, mCorrectionAngle, boxes);
                        canvas.drawRect(c.x * xsize, c.y * ysize, c.x * xsize + xsize, c.y * ysize + ysize, mPaint);
                    }
                } finally {
                    mMotionView.getHolder().unlockCanvasAndPost(canvas);
                }
            }
        }
    }

    private Point correctSensorRotation(Point p, int correctionAngle, int boxes) {
        if (correctionAngle == 270) {
            return new Point(boxes - 1 - p.y, boxes - 1 - p.x);
        } else if (correctionAngle == 180) {
            return new Point(boxes - 1 - p.x, p.y);
        } else if (correctionAngle == 90) {
            return new Point(p.y, boxes - 1 - p.x);
        } else {
            return new Point(p.x, boxes - 1 - p.y);
        }
    }

    @Override
    public void noMotion() {
        boolean showPreview = mPreferences.getBoolean("pref_motion_detection_preview", false);
        boolean motionDetection = mPreferences.getBoolean("pref_motion_detection_enabled", false);

        if (showPreview && motionDetection && mMotionView.getHolder().getSurface().isValid()) {
            final Canvas canvas = mMotionView.getHolder().lockCanvas();
            if (canvas != null) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                mMotionView.getHolder().unlockCanvasAndPost(canvas);
            }
        }
    }

    @Override
    public void tooDark() {
        boolean showPreview = mPreferences.getBoolean("pref_motion_detection_preview", false);
        boolean motionDetection = mPreferences.getBoolean("pref_motion_detection_enabled", false);

        if (showPreview && motionDetection && mMotionView.getHolder().getSurface().isValid()) {
            final Canvas canvas = mMotionView.getHolder().lockCanvas();
            if (canvas != null) {
                canvas.drawText(mNavigationView.getContext().getString(R.string.tooDark), (canvas.getWidth() - mDarkTextWidth) / 2, 50, mPaint);
                mMotionView.getHolder().unlockCanvasAndPost(canvas);
            }
        }
    }

    public void setDeviceRotation(int newDeviceRotation) {
        mCorrectionAngle = (mCameraRotation - newDeviceRotation * 90 + 360) % 360;
    }
}
