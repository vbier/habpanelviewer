package de.vier_bier.habpanelviewer.reporting.motion;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Brightness data for an image.
 */
class LumaData {
    // as LumaData objects are created and disposed in rapid succession, reuse the underlying byte[]
    private static final ArrayList<byte[][]> AVG_POOL = new ArrayList<>();
    private static int mAvgPoolDim = -1;
    private static final ArrayList<byte[]> DATA_POOL = new ArrayList<>();
    private static int mDataPoolDim = -1;

    private byte[] data;
    private byte[][] average = null;
    private final int width;
    private final int height;
    private int mBoxes = -1;

    private LumaData(byte[] data, int width, int height) {
        this.data = data;
        this.width = width;
        this.height = height;
    }

    LumaData(ByteBuffer buffer, int width, int height) {
        data = getFromDataPool(buffer.capacity());
        buffer.get(data);
        this.width = width;
        this.height = height;
    }

    public void setBoxCount(int boxCount) {
        if (boxCount != mBoxes) {
            mBoxes = boxCount;
            average = getFromAvgPool(boxCount);
        }
    }

    /**
     * Get the LumaData.
     *
     * @return integer array of the LumaData.
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Get the width of the LumaData.
     *
     * @return integer representing the width of the state.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get the height of the LumaData.
     *
     * @return integer representing the height of the state.
     */
    public int getHeight() {
        return height;
    }

    int getAverage(int xBox, int yBox) {
        return average[xBox][yBox];
    }

    void setAverage(int xBox, int yBox, byte result) {
        average[xBox][yBox] = result;
    }

    boolean isDarker(int minLuma) {
        int lumaSum = 0;
        for (int i : data) {
            lumaSum += (i - Byte.MIN_VALUE - 16);
        }
        return lumaSum < minLuma;
    }

    private static byte[][] getFromAvgPool(int boxes) {
        synchronized (AVG_POOL) {
            if (boxes != mAvgPoolDim) {
                AVG_POOL.clear();
                mAvgPoolDim = boxes;
            }

            byte[][] average;
            if (AVG_POOL.isEmpty()) {
                average = new byte[boxes][boxes];
            } else {
                average = AVG_POOL.remove(0);
            }

            for (int x = 0; x < boxes; x++) {
                Arrays.fill(average[x], (byte) -1);
            }

            return average;
        }
    }

    private static byte[] getFromDataPool(int size) {
        synchronized (DATA_POOL) {
            if (size != mDataPoolDim) {
                DATA_POOL.clear();
                mDataPoolDim = size;
            }

            if (DATA_POOL.isEmpty()) {
                return new byte[size];
            } else {
                return DATA_POOL.remove(0);
            }
        }
    }

    public static LumaData extractLuma(byte[] data, int width, int height) {
        byte[] hsl = getFromDataPool(width * height);

        for (int j = 0, yp = 0; j < height; j++) {
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & (data[yp])) - 16;
                if (y < 0) y = 0;
                hsl[yp] = (byte) y;
            }
        }

        return new LumaData(hsl, width, height);
    }

    public void release() {
        synchronized (AVG_POOL) {
            if (mBoxes == mAvgPoolDim) {
                AVG_POOL.add(average);
                average = null;
            }
        }

        synchronized (DATA_POOL) {
            if (data.length == mDataPoolDim) {
                DATA_POOL.add(data);
                data = null;
            }
        }
    }

}
