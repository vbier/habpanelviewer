package vier_bier.de.habpanelviewer.motion;

/**
 * Image data consisting of the data bytes and the image size.
 */
class ImageData {
    private byte[] data;
    private int width;
    private int height;

    public ImageData(byte[] bytes, int w, int h) {
        data = bytes.clone();

        width = w;
        height = h;
    }

    public LumaData extractLumaData(int xBoxCount, int yBoxCount) {
        final int frameSize = width * height;
        int[] hsl = new int[frameSize];

        for (int j = 0, yp = 0; j < height; j++) {
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & (data[yp])) - 16;
                if (y < 0) y = 0;
                hsl[yp] = y;
            }
        }

        return new LumaData(hsl, width, height, xBoxCount, yBoxCount);
    }
}
