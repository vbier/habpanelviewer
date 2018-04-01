package de.vier_bier.habpanelviewer.reporting.motion;

import android.app.Activity;
import android.graphics.Point;
import android.util.Log;
import android.view.TextureView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Base camera implementation.
 */
abstract class AbstractCameraImpl implements ICamera {
    private static final String TAG = "AbstractCameraImpl";

    final Activity mActivity;
    final TextureView mPreviewView;
    int mDeviceOrientation;

    List<ILumaListener> mListeners = new ArrayList<>();

    AbstractCameraImpl(Activity activity, TextureView previewView, int orientation) {
        mActivity = activity;
        mPreviewView = previewView;
        mDeviceOrientation = orientation;
    }

    @Override
    public void addLumaListener(ILumaListener l) {
        mListeners.add(l);
    }

    @Override
    public void removeLumaListener(ILumaListener l) {
        mListeners.remove(l);
    }

    Point chooseOptimalSize(Point[] choices) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Point> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Point> notBigEnough = new ArrayList<>();
        int w = 640;
        int h = 480;
        for (Point option : choices) {
            if (option.y == option.x * h / w) {
                if (option.x >= 640 &&
                        option.y >= 480) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private static class CompareSizesByArea implements Comparator<Point> {
        @Override
        public int compare(Point lhs, Point rhs) {
            return Long.signum((long) lhs.x * lhs.y -
                    (long) rhs.x * rhs.y);
        }

    }
}
