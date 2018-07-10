package de.vier_bier.habpanelviewer.openhab;

/**
 * Item with state.
 */
class ItemState {
    final String mItemName;
    final String mItemState;

    ItemState(String itemName, String itemState) {
        mItemName = itemName;
        mItemState = itemState;
    }

    public String getItemName() {
        return mItemName;
    }

    public String getItemState() {
        return mItemState;
    }

    @Override
    public int hashCode() {
        return mItemName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ItemState && mItemName != null && mItemName.equals(((ItemState) obj).mItemName);
    }
}
