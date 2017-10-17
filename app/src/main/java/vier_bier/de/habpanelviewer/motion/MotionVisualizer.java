package vier_bier.de.habpanelviewer.motion;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.view.SurfaceView;

import java.util.ArrayList;

/**
 * Visualizes motion areas on the given surface view.
 */
public class MotionVisualizer implements MotionListener {
    private final SurfaceView mMotionView;
    private final SharedPreferences mPreferences;
    private final MotionListener mListener;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MotionVisualizer(SurfaceView motionView, SharedPreferences preferences, MotionListener listener) {
        mMotionView = motionView;
        mPreferences = preferences;
        mListener = listener;

        mMotionView.setZOrderOnTop(true);
        mMotionView.getHolder().setFormat(PixelFormat.TRANSPARENT);

        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setTextSize(48);
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
                canvas.drawText("Motion", 160, 50, mPaint);

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
                canvas.drawText("too dark", 140, 50, mPaint);
                mMotionView.getHolder().unlockCanvasAndPost(canvas);
            }
        }

        mListener.tooDark();
    }
}
