package vier_bier.de.habpanelviewer.motion;

import android.graphics.Point;

import java.util.ArrayList;

/**
 * Compares to images for equality. Does this by dividing the images into smaller rectangles,
 * computing the brightness average and comparing that.
 */
class Comparer {
    private int boxes;
    private int xPixelsPerBox;
    private int yPixelsPerBox;
    private int xPixelsLastBox;
    private int yPixelsLastBox;
    private int leniency;

    Comparer(int width, int height, int boxes, int leniency) {
        this.boxes = boxes;
        this.leniency = leniency;

        // how many points per box
        xPixelsPerBox = width / this.boxes;
        yPixelsPerBox = height / this.boxes;

        if (boxes * yPixelsPerBox < height) {
            yPixelsLastBox = height - (boxes - 1) * yPixelsPerBox;
        }
        if (boxes * xPixelsPerBox < width) {
            xPixelsLastBox = width - (boxes - 1) * xPixelsPerBox;
        }
    }

    /**
     * Compare two images for difference
     */
    ArrayList<Point> isDifferent(LumaData s1, LumaData s2) {
        ArrayList<Point> differing = new ArrayList<>();

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

        int yPix = (yBox == boxes - 1 && yPixelsLastBox > 0) ? yPixelsLastBox : yPixelsPerBox;
        int xPix = (xBox == boxes - 1 && xPixelsLastBox > 0) ? xPixelsLastBox : xPixelsPerBox;

        int i = 0;
        int idx = yBox * yPixelsPerBox * luma.getWidth() + xBox * xPixelsPerBox;
        for (int y = 0; y < yPix; y++) {
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
