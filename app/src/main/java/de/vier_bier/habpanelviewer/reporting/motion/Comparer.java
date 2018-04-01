package de.vier_bier.habpanelviewer.reporting.motion;

import android.graphics.Point;

import java.util.ArrayList;

/**
 * Compares to images for equality. Does this by dividing the images into smaller rectangles,
 * computing the brightness average and comparing that.
 */
class Comparer {
    private final int boxes;

    private final Point[] boxStart;
    private final Point[] boxEnd;
    private final int leniency;

    Comparer(int width, int height, int boxes, int leniency) {
        this.boxes = boxes;
        this.leniency = leniency;

        // how many points per box
        float xPixelsPerBox = width / (float) boxes;
        float yPixelsPerBox = height / (float) boxes;

        boxStart = new Point[boxes];
        boxEnd = new Point[boxes];

        for (int i = 0; i < boxes; i++) {
            int startX = (int) (i * xPixelsPerBox);
            int endX = i == (boxes - 1) ? width - 1 : (int) ((i + 1) * xPixelsPerBox) - 1;

            int startY = (int) (i * yPixelsPerBox);
            int endY = i == (boxes - 1) ? height - 1 : (int) ((i + 1) * yPixelsPerBox) - 1;

            boxStart[i] = new Point(startX, startY);
            boxEnd[i] = new Point(endX, endY);
        }
    }

    /**
     * Compare two images for difference
     */
    ArrayList<Point> isDifferent(LumaData s1, LumaData s2) {
        ArrayList<Point> differing = new ArrayList<>();

        s1.setBoxCount(boxes);
        s2.setBoxCount(boxes);

        int b1;
        int b2;
        int diff;
        for (int y = 0; y < boxes; y++) {
            for (int x = 0; x < boxes; x++) {
                b1 = calcAverage(s1, x, y);
                b2 = calcAverage(s2, x, y);
                diff = Math.abs(b1 - b2);

                if (diff > leniency * 2.55f) {
                    differing.add(new Point(x, y));
                }
            }
        }
        return differing;
    }

    private int calcAverage(LumaData luma, int xBox, int yBox) {
        if (luma.getAverage(xBox, yBox) != -1) {
            return luma.getAverage(xBox, yBox);
        }

        byte[] data = luma.getData();
        if (data == null) throw new NullPointerException();

        int yPix = boxEnd[yBox].y - boxStart[yBox].y + 1;
        int xPix = boxEnd[xBox].x - boxStart[xBox].x + 1;

        int i = 0;
        int idx = boxStart[yBox].y * luma.getWidth() + boxStart[xBox].x;
        for (int y = boxStart[yBox].y; y <= boxEnd[yBox].y; y++) {
            for (int x = 0; x < xPix; x++) {
                i += data[idx++];
            }
            idx += luma.getWidth() - xPix;
        }

        byte result = (byte) (i / (xPix * yPix));
        luma.setAverage(xBox, yBox, result);

        return result;
    }
}
