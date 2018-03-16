package de.vier_bier.habpanelviewer;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Captures screen shots
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ScreenCapturer {
    static final int REQUEST_MEDIA_PROJECTION = 12835;

    private MediaProjection mProjection;
    private Handler mHandler;
    private int mWidth;
    private int mHeight;
    private int mDensity;

    ScreenCapturer(MediaProjection projection, int width, int height, int density) {
        mHandler = new Handler();
        mProjection = projection;
        mWidth = width;
        mHeight = height;
        mDensity = density;
    }

    public synchronized Bitmap captureScreen() throws IllegalStateException {
        AtomicReference<Image> imageHolder = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        ImageReader mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                imageHolder.set(mImageReader.acquireLatestImage());
                latch.countDown();
            }
        }, mHandler);

        VirtualDisplay display = mProjection.createVirtualDisplay("screen-mirror", mWidth, mHeight, mDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mImageReader.getSurface(),
                null, null);

        try {
            latch.await(1, TimeUnit.SECONDS);

            if (latch.getCount() == 1) {
                throw new IllegalStateException("Screen capturing timed out");
            }

            final Image image = imageHolder.get();
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * mWidth;

            // create bitmap
            Bitmap bmp = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buffer);
            image.close();

            return bmp;
        } catch (InterruptedException e) {
            throw new IllegalStateException("Got interrupt while capturing screen");
        } finally {
            display.release();
        }
    }
}
