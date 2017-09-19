package vier_bier.de.habpanelviewer.motion;

/**
 * Created by volla on 15.09.17.
 */
public class LumaData {
    private int[] data = null;
    private int[][] average = null;
    private int width;
    private int height;

    public LumaData(int[] data, int width, int height, int mXBoxes, int mYBoxes) {
        if (data == null) throw new NullPointerException();

        this.data = data.clone();
        this.width = width;
        this.height = height;

        average = new int[mXBoxes][mYBoxes];
        for (int x = 0; x < mXBoxes; x++) {
            for (int y = 0; y < mYBoxes; y++) {
                average[x][y] = -1;
            }
        }
    }

    /**
     * Get the LumaData.
     *
     * @return integer array of the LumaData.
     */
    public int[] getData() {
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

    public int getAverage(int xBox, int yBox) {
        return average[xBox][yBox];
    }

    public void setAverage(int xBox, int yBox, int result) {
        average[xBox][yBox] = result;
    }

    public boolean isDarker(int minLuma) {
        int lumaSum = 0;
        for (int i : data) {
            lumaSum += i;
        }
        return lumaSum < minLuma;
    }
}
