package de.vier_bier.habpanelviewer.openhab;

/**
 * Item with state.
 */
public class ItemState {
    final String mItemName;
    final String mItemState;

    public ItemState(String itemName, String itemState) {
        mItemName = itemName;
        mItemState = itemState;
    }

    @Override
    public int hashCode() {
        return mItemName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ItemState)) {
            return false;
        }

        return mItemName == null ? obj == null : mItemName.equals(((ItemState) obj).mItemName);
    }
}
