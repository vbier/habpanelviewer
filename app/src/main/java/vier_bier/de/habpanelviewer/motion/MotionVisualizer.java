package vier_bier.de.habpanelviewer.motion;

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

/**
 * Visualizes motion areas on the given mSurface view.
 */
public class MotionVisualizer implements MotionListener {
    private final SurfaceView mMotionView;
    private final NavigationView mNavigationView;
    private final SharedPreferences mPreferences;
    private final MotionListener mListener;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int mMotionTextWidth;
    private final int mDarkTextWidth;

    public MotionVisualizer(SurfaceView motionView, NavigationView navigationView, SharedPreferences preferences, MotionListener listener, int scaledSize) {
        mMotionView = motionView;
        mNavigationView = navigationView;
        mPreferences = preferences;
        mListener = listener;

        mMotionView.setZOrderOnTop(true);
        mMotionView.getHolder().setFormat(PixelFormat.TRANSPARENT);

        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setTextSize(scaledSize);

        Rect bounds = new Rect();
        mPaint.getTextBounds("Motion", 0, 6, bounds);
        mMotionTextWidth = bounds.width();

        mPaint.getTextBounds("too dark", 0, 8, bounds);
        mDarkTextWidth = bounds.width();
    }

    @Override
    public void motionDetected(ArrayList<Point> differing) {
        boolean showPreview = mPreferences.getBoolean("pref_motion_detection_preview", false);
        boolean motionDetection = mPreferences.getBoolean("pref_motion_detection_enabled", false);

        if (showPreview && motionDetection && mMotionView.getHolder().getSurface().isValid()) {
            final Canvas canvas = mMotionView.getHolder().lockCanvas();

            if (canvas != null) {
                int boxes = Integer.parseInt(mPreferences.getString("pref_motion_detection_granularity", "20"));
                float xsize = canvas.getWidth() / (float) boxes;
                float ysize = canvas.getHeight() / (float) boxes;

                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

                if (mNavigationView.isShown()) {
                    Rect r1 = new Rect();
                    mNavigationView.getGlobalVisibleRect(r1);

                    Rect r2 = new Rect();
                    mMotionView.getGlobalVisibleRect(r2);

                    if (r1.intersect(r2)) {
                        int clipLeft = r1.right - r2.left;
                        canvas.clipRect(clipLeft, 0, canvas.getWidth(), canvas.getHeight());
                    }
                }

                canvas.drawText("Motion", (canvas.getWidth() - mMotionTextWidth) / 2, 50, mPaint);

                for (Point p : differing) {
                    canvas.drawRect(p.x * xsize, p.y * ysize, p.x * xsize + xsize, p.y * ysize + ysize, mPaint);
                }

                mMotionView.getHolder().unlockCanvasAndPost(canvas);
            }
        }

        mListener.motionDetected(differing);
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

        mListener.noMotion();
    }

    @Override
    public void tooDark() {
        boolean showPreview = mPreferences.getBoolean("pref_motion_detection_preview", false);
        boolean motionDetection = mPreferences.getBoolean("pref_motion_detection_enabled", false);

        if (showPreview && motionDetection && mMotionView.getHolder().getSurface().isValid()) {
            final Canvas canvas = mMotionView.getHolder().lockCanvas();
            if (canvas != null) {
                canvas.drawText("too dark", (canvas.getWidth() - mDarkTextWidth) / 2, 50, mPaint);
                mMotionView.getHolder().unlockCanvasAndPost(canvas);
            }
        }

        mListener.tooDark();
    }
}
