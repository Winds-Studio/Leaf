package org.dreeam.leaf.util.list;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

import java.util.Arrays;
import java.util.Collection;

/**
 * A list for ServerLevel's blockEntityTickers
 * <p>
 * This list behaves identically to ReferenceArrayList, but it has an additional method, `removeMarkedEntities`, that allows a list of integers to be passed indicating what
 * indexes should be deleted from the list
 * <p>
 * This is faster than using removeAll, since we don't need to compare the identity of each block entity, and faster than looping thru each index manually and deleting with remove,
 * since we don't need to resize the array every single removal.
 */
public final class BlockEntityTickersList extends ReferenceArrayList<TickingBlockEntity> {

    private final IntArrayList toRemove = new IntArrayList();

    private int minRemovalIndex = Integer.MAX_VALUE;
    private int lastAddedRemoveIndex = -1;
    private boolean isSorted = true;

    public BlockEntityTickersList() {
        super();
    }

    /**
     * Creates a new array list and fills it with a given collection.
     *
     * @param c a collection that will be used to fill the array list.
     */
    public BlockEntityTickersList(final Collection<? extends TickingBlockEntity> c) {
        super(c);
    }

    /**
     * Marks an entry as removed
     *
     * @param index the index of the item on the list to be marked as removed
     */
    public void markAsRemoved(final int index) {
        if (index < this.minRemovalIndex) {
            this.minRemovalIndex = index;
        }

        if (index < this.lastAddedRemoveIndex) {
            this.isSorted = false; // in theory, this should never happen as the TE iteration order is incremental
        }

        this.lastAddedRemoveIndex = index;
        this.toRemove.add(index);
    }

    /**
     * Removes elements that have been marked as removed.
     */
    public void removeMarkedEntries() {
        if (this.toRemove.isEmpty()) {
            return;
        }

        if (!this.isSorted) {
            IntArrays.quickSort(this.toRemove.elements(), 0, this.toRemove.size());
            minRemovalIndex = this.toRemove.getInt(0);
        }

        removeBySortedIndices();

        this.toRemove.clear();
        this.minRemovalIndex = Integer.MAX_VALUE;
        this.lastAddedRemoveIndex = -1;
        this.isSorted = true;
    }

    private void removeBySortedIndices() {
        if (this.minRemovalIndex >= this.size) {
            return;
        }

        final int[] removeIndices = this.toRemove.elements();
        final int removeCount = this.toRemove.size();
        final Object[] backingArray = this.a;

        int writeIndex = this.minRemovalIndex;
        int prevRemoveIndex = this.minRemovalIndex;

        for (int i = 1; i < removeCount; i++) {
            int currRemoveIndex = removeIndices[i];

            if (currRemoveIndex == prevRemoveIndex) {
                continue;
            }

            int length = currRemoveIndex - (prevRemoveIndex + 1);

            if (length > 0) {
                System.arraycopy(backingArray, prevRemoveIndex + 1, backingArray, writeIndex, length);
                writeIndex += length;
            }
            prevRemoveIndex = currRemoveIndex;
        }

        int tailLength = this.size - (prevRemoveIndex + 1);
        if (tailLength > 0) {
            System.arraycopy(backingArray, prevRemoveIndex + 1, backingArray, writeIndex, tailLength);
            writeIndex += tailLength;
        }

        Arrays.fill(backingArray, writeIndex, this.size, null);
        this.size = writeIndex;
    }

    @Override
    public void clear() {
        super.clear();
        this.toRemove.clear();
        this.minRemovalIndex = Integer.MAX_VALUE;
        this.lastAddedRemoveIndex = -1;
        this.isSorted = true;
    }
}
