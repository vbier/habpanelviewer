package vier_bier.de.habpanelviewer.motion;

/**
 * Created by volla on 15.09.17.
 */
public class Comparer {
    private int xBoxes;
    private int yBoxes;
    private int xPixelsPerBox;
    private int yPixelsPerBox;
    private int xPixelsLastBox;
    private int yPixelsLastBox;
    private int leniency;

    public Comparer(int width, int height, int xBoxes, int yBoxes, int leniency) {
        this.xBoxes = xBoxes;
        this.yBoxes = yBoxes;
        this.leniency = leniency;

        // how many points per box
        xPixelsPerBox = width / this.xBoxes;
        yPixelsPerBox = height / this.yBoxes;

        if (yBoxes * yPixelsPerBox < height) {
            yPixelsLastBox = height - (yBoxes - 1) * yPixelsPerBox;
        }
        if (xBoxes * xPixelsPerBox < width) {
            xPixelsLastBox = width - (xBoxes - 1) * xPixelsPerBox;
        }
    }

    /**
     * Compare two images for difference
     */
    public boolean isDifferent(LumaData s1, LumaData s2) {
        boolean different = false;

        int b1;
        int b2;
        int diff;
        for (int y = 0; y < yBoxes; y++) {
            for (int x = 0; x < xBoxes; x++) {
                b1 = calcAverage(s1, x, y);
                b2 = calcAverage(s2, x, y);
                diff = Math.abs(b1 - b2);

                if (diff > leniency) different = true;
            }
        }
        return different;
    }

    private int calcAverage(LumaData luma, int xBox, int yBox) {
        if (luma.getAverage(xBox, yBox) != -1) {
            return luma.getAverage(xBox, yBox);
        }

        int[] data = luma.getData();
        if (data == null) throw new NullPointerException();

        int yPix = (yBox == yBoxes - 1 && yPixelsLastBox > 0) ? yPixelsLastBox : yPixelsPerBox;
        int xPix = (xBox == xBoxes - 1 && xPixelsLastBox > 0) ? xPixelsLastBox : xPixelsPerBox;

        int i = 0;
        int idx = yBox * yPixelsPerBox * luma.getWidth() + xBox * xPixelsPerBox;
        for (int y = 0; y < yPix; y++) {
            for (int x = 0; x < xPix; x++) {
                i += data[idx++];
            }
            idx += luma.getWidth() - xPix;
        }

        int result = (i / (xPix * yPix));
        luma.setAverage(xBox, yBox, result);

        return result;
    }
}
