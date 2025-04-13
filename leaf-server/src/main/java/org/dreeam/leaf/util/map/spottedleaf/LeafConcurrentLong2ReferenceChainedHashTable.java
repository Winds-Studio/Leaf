package org.dreeam.leaf.util.map.spottedleaf;

import ca.spottedleaf.concurrentutil.function.BiLong1Function;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.HashUtil;
import ca.spottedleaf.concurrentutil.util.IntegerUtil;
import ca.spottedleaf.concurrentutil.util.ThrowUtil;
import ca.spottedleaf.concurrentutil.util.Validate;

import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.Predicate;

/**
 * Optimized concurrent hashtable implementation supporting mapping arbitrary {@code long} keys onto non-null {@code Object}
 * values with support for multiple writer and multiple reader threads. Utilizes lock-free read paths,
 * optimistic lock-free write attempts, and fine-grained locking during modifications and resizing.
 *
 * <h2>Happens-before relationship</h2>
 * <p>
 * As with {@link ConcurrentMap}, actions in a thread prior to placing an object into this map
 * happen-before actions subsequent to the access or removal of that object in another thread.
 * </p>
 *
 * <h2>Atomicity of functional methods</h2>
 * <p>
 * Functional methods (like {@code compute}, {@code merge}, etc.) are performed atomically per key.
 * The function provided is guaranteed to be invoked at most once per call under a lock specific to the
 * entry's bin. Consequently, invoking other map modification methods on this map from within the function
 * can lead to undefined behavior or deadlock.
 * </p>
 *
 * @param <V> The type of mapped values (must be non-null).
 * @see java.util.concurrent.ConcurrentHashMap
 */

public class LeafConcurrentLong2ReferenceChainedHashTable<V> implements Iterable<LeafConcurrentLong2ReferenceChainedHashTable.TableEntry<V>> {

    // --- Constants ---

    protected static final int DEFAULT_CAPACITY = 16;
    protected static final float DEFAULT_LOAD_FACTOR = 0.75f;
    /** The maximum capacity, used if a higher value is implicitly specified by either
     *  of the constructors with arguments. MUST be a power of two <= 1<<30.
     */
    protected static final int MAXIMUM_CAPACITY = 1 << 30; // 2^30

    protected static final int THRESHOLD_NO_RESIZE = -1; // Sentinel value: table cannot be resized
    protected static final int THRESHOLD_RESIZING = -2; // Sentinel value: table is currently resizing

    // --- Instance Fields ---

    /** Tracks the number of mappings, using LongAdder for better high-contention performance. */
    protected final LongAdder size = new LongAdder();

    /** The load factor for the hash table. */
    protected final float loadFactor;

    /** The hash table array. Elements are accessed using VarHandles. */
    protected volatile TableEntry<V>[] table;

    /**
     * The next size value at which to resize (unless {@code <= 0}).
     * Accessed via VarHandle {@link #THRESHOLD_HANDLE}.
     */
    protected volatile int threshold;

    // --- VarHandles ---

    protected static final VarHandle THRESHOLD_HANDLE;
    static {
        try {
            THRESHOLD_HANDLE = ConcurrentUtil.getVarHandle(LeafConcurrentLong2ReferenceChainedHashTable.class, "threshold", int.class);
        } catch (Throwable t) {
            throw new Error("Failed to initialize VarHandles", t);
        }
        // Static initialization for TableEntry VarHandles is inside the TableEntry class
    }

    // --- Views (lazily initialized) ---

    protected transient Values<V> values;
    protected transient EntrySet<V> entrySet;

    // --- Constructors ---

    /**
     * Creates a new, empty map with the default initial capacity (16) and load factor (0.75).
     */
    public LeafConcurrentLong2ReferenceChainedHashTable() {
        this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a new, empty map with the specified initial capacity and load factor.
     *
     * @param initialCapacity The initial capacity. The implementation performs internal
     *                        sizing to accommodate this many elements.
     * @param loadFactor      The load factor threshold, used to control resizing.
     * @throws IllegalArgumentException if the initial capacity is negative or the load
     *                                  factor is non-positive or NaN.
     */
    @SuppressWarnings("unchecked")
    protected LeafConcurrentLong2ReferenceChainedHashTable(final int initialCapacity, final float loadFactor) {
        if (loadFactor <= 0.0f || !Float.isFinite(loadFactor)) {
            throw new IllegalArgumentException("Invalid load factor: " + loadFactor);
        }
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Invalid initial capacity: " + initialCapacity);
        }

        final int tableSize = getCapacityFor(initialCapacity);
        this.loadFactor = loadFactor;
        this.setThresholdPlain(getTargetThreshold(tableSize, loadFactor)); // Use plain set, happens-before established by volatile table write
        this.table = (TableEntry<V>[]) new TableEntry[tableSize]; // Volatile write publishes the initial state
    }

    /**
     * Creates a new, empty map with the specified initial capacity and the default load factor (0.75).
     *
     * @param capacity The initial capacity.
     * @throws IllegalArgumentException if the initial capacity is negative.
     */
    public static <V> LeafConcurrentLong2ReferenceChainedHashTable<V> createWithCapacity(final int capacity) {
        return createWithCapacity(capacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a new, empty map with the specified initial capacity and load factor.
     *
     * @param capacity The initial capacity.
     * @param loadFactor The load factor threshold.
     * @throws IllegalArgumentException if the initial capacity is negative or the load factor is non-positive/NaN.
     */
    public static <V> LeafConcurrentLong2ReferenceChainedHashTable<V> createWithCapacity(final int capacity, final float loadFactor) {
        return new LeafConcurrentLong2ReferenceChainedHashTable<>(capacity, loadFactor);
    }

    /**
     * Creates a new, empty map with an initial capacity sufficient to hold the specified number of elements
     * without resizing, using the default load factor (0.75).
     *
     * @param expected The expected number of elements.
     * @throws IllegalArgumentException if the expected size is negative.
     */
    public static <V> LeafConcurrentLong2ReferenceChainedHashTable<V> createWithExpected(final int expected) {
        return createWithExpected(expected, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a new, empty map with an initial capacity sufficient to hold the specified number of elements
     * without resizing, using the specified load factor.
     *
     * @param expected The expected number of elements.
     * @param loadFactor The load factor threshold.
     * @throws IllegalArgumentException if the expected size is negative or the load factor is non-positive/NaN.
     */
    public static <V> LeafConcurrentLong2ReferenceChainedHashTable<V> createWithExpected(final int expected, final float loadFactor) {
        if (expected < 0) {
            throw new IllegalArgumentException("Invalid expected size: " + expected);
        }
        // Calculate initial capacity based on expected size and load factor
        final double capacityEstimate = ((double) expected / (double) loadFactor) + 1.0;
        final int capacity = (capacityEstimate >= (double) MAXIMUM_CAPACITY)
            ? MAXIMUM_CAPACITY
            : (int) Math.min(MAXIMUM_CAPACITY, Math.max(DEFAULT_CAPACITY, Math.ceil(capacityEstimate)));
        return createWithCapacity(capacity, loadFactor);
    }

    // --- Internal Helper Methods ---

    /** Calculates the target resize threshold. */
    protected static int getTargetThreshold(final int capacity, final float loadFactor) {
        if (capacity >= MAXIMUM_CAPACITY) {
            return THRESHOLD_NO_RESIZE; // Max capacity reached, no more resizing
        }
        // Calculate threshold, preventing overflow and ensuring it's at least 1
        final double calculatedThreshold = (double) capacity * (double) loadFactor;
        if (calculatedThreshold >= (double) MAXIMUM_CAPACITY) {
            return MAXIMUM_CAPACITY; // Cap threshold at maximum capacity if calculation exceeds it
        }
        // Use ceil to ensure threshold is met strictly *after* the size reaches it
        return (int) Math.max(1, Math.ceil(calculatedThreshold));
    }


    /** Calculates the power-of-two capacity for a given initial capacity request. */
    protected static int getCapacityFor(final int requestedCapacity) {
        if (requestedCapacity <= 0) {
            // Default capacity if non-positive requested, could also throw exception
            return DEFAULT_CAPACITY;
        }
        if (requestedCapacity >= MAXIMUM_CAPACITY) {
            return MAXIMUM_CAPACITY;
        }
        // Round up to the next power of two
        return IntegerUtil.roundCeilLog2(Math.max(DEFAULT_CAPACITY, requestedCapacity));
    }

    /** Computes the hash code for the key. Uses mixing to spread keys more evenly. */
    protected static int getHash(final long key) {
        return (int) HashUtil.mix(key); // Assumes HashUtil.mix provides good distribution
    }

    /** Returns the load factor associated with this map. */
    public final float getLoadFactor() {
        return this.loadFactor;
    }

    // --- VarHandle Accessors for 'threshold' ---

    protected final int getThresholdAcquire() {
        return (int) THRESHOLD_HANDLE.getAcquire(this);
    }

    protected final int getThresholdVolatile() {
        return (int) THRESHOLD_HANDLE.getVolatile(this);
    }

    protected final void setThresholdPlain(final int threshold) {
        THRESHOLD_HANDLE.set(this, threshold);
    }

    protected final void setThresholdRelease(final int threshold) {
        THRESHOLD_HANDLE.setRelease(this, threshold);
    }

    protected final void setThresholdVolatile(final int threshold) {
        THRESHOLD_HANDLE.setVolatile(this, threshold);
    }

    protected final int compareExchangeThresholdVolatile(final int expect, final int update) {
        return (int) THRESHOLD_HANDLE.compareAndExchange(this, expect, update);
    }

    // --- VarHandle Accessors for 'table' array elements ---

    @SuppressWarnings("unchecked")
    protected static <V> TableEntry<V> getAtIndexVolatile(final TableEntry<V>[] table, final int index) {
        return (TableEntry<V>) TableEntry.TABLE_ENTRY_ARRAY_HANDLE.getVolatile(table, index);
    }

    protected static <V> void setAtIndexRelease(final TableEntry<V>[] table, final int index, final TableEntry<V> value) {
        TableEntry.TABLE_ENTRY_ARRAY_HANDLE.setRelease(table, index, value);
    }

    protected static <V> void setAtIndexVolatile(final TableEntry<V>[] table, final int index, final TableEntry<V> value) {
        TableEntry.TABLE_ENTRY_ARRAY_HANDLE.setVolatile(table, index, value);
    }

    @SuppressWarnings("unchecked")
    protected static <V> TableEntry<V> compareAndExchangeAtIndexVolatile(final TableEntry<V>[] table, final int index,
                                                                         final TableEntry<V> expect, final TableEntry<V> update) {
        return (TableEntry<V>) TableEntry.TABLE_ENTRY_ARRAY_HANDLE.compareAndExchange(table, index, expect, update);
    }

    // --- Core Map Operations ---

    /**
     * Retrieves the node associated with the key. This is the core lookup logic.
     * It handles concurrent resizes without locking for reads.
     * Returns null if the key is not found.
     * The returned node's value might be null if it's a placeholder during a compute operation.
     */
    @SuppressWarnings("unchecked")
    protected final TableEntry<V> getNode(final long key) {
        final int hash = getHash(key);
        TableEntry<V>[] currentTable = this.table; // Volatile read of table reference

        outer_loop:
        for (;;) { // Loop handles table resizes detected during traversal
            final int tableLength = currentTable.length;
            if (tableLength == 0) {
                // Table not initialized? Should not happen after constructor. Re-read.
                currentTable = this.table;
                if (currentTable.length == 0) {
                    // Still not initialized? This indicates a deeper issue, but returning null is safe.
                    return null;
                }
                continue; // Retry with the initialized table
            }

            final int index = hash & (tableLength - 1); // Calculate index using mask
            TableEntry<V> head = getAtIndexVolatile(currentTable, index); // Volatile read of bin head

            if (head == null) {
                return null; // Bin is empty
            }

            // Check if the bin head is a resize marker
            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue outer_loop; // Retry operation with the new table
            }

            // Check if the head node itself contains the key
            if (head.key == key) {
                return head;
            }

            // Traverse the linked list (chain) in the bin
            TableEntry<V> node = head.getNextVolatile(); // Volatile read for the next node
            while (node != null) {
                if (node.key == key) {
                    return node; // Key found
                }
                node = node.getNextVolatile(); // Move to the next node
            }

            // Key not found in the chain.
            // Crucial step: Re-check if the table was resized *during* traversal.
            TableEntry<V>[] latestTable = this.table; // Volatile read
            if (currentTable != latestTable) {
                // Table reference changed, indicating a resize occurred. Retry the whole lookup.
                currentTable = latestTable;
                continue outer_loop;
            }

            // Key not found, and table reference is stable since traversal started.
            return null;
        }
    }

    /**
     * Helps with resizing or gets the reference to the next table if the current
     * bin contains a resize marker.
     */
    @SuppressWarnings("unchecked")
    private TableEntry<V>[] helpResizeOrGetNextTable(TableEntry<V>[] currentTable, TableEntry<V> resizeMarker) {
        // The new table reference is stored in the 'value' field of the resize marker
        V markerValue = resizeMarker.getValuePlain(); // Plain read is okay, marker itself is immutable reference
        if (markerValue instanceof TableEntry<?>[]) {
            TableEntry<V>[] nextTable = (TableEntry<V>[]) markerValue;
            // Optionally, could add logic here to actively help with the resize process
            // by transferring some nodes if the current thread isn't already resizing.
            // For simplicity, we just return the next table reference for the retry.
            return nextTable;
        }
        // Fallback: This should ideally not happen if resize markers are constructed correctly.
        // Re-read the latest table reference to force a retry in getNode.
        return this.table;
    }


    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     *
     * @param key the key whose associated value is to be returned
     * @return the value mapped to the key, or {@code null} if none
     */
    public V get(final long key) {
        final TableEntry<V> node = this.getNode(key);
        // Use volatile read on value to ensure happens-before visibility
        return (node == null) ? null : node.getValueVolatile();
    }

    /**
     * Returns the value to which the specified key is mapped, or
     * {@code defaultValue} if this map contains no mapping for the key.
     *
     * @param key the key whose associated value is to be returned
     * @param defaultValue the default mapping of the key
     * @return the value mapped to the key, or {@code defaultValue} if none
     */
    public V getOrDefault(final long key, final V defaultValue) {
        final TableEntry<V> node = this.getNode(key);
        if (node == null) {
            return defaultValue;
        }
        // Use volatile read for visibility. Check for null in case it's a compute placeholder.
        final V value = node.getValueVolatile();
        return (value == null) ? defaultValue : value;
    }

    /**
     * Returns {@code true} if this map contains a mapping for the specified key.
     *
     * @param key The key whose presence in this map is to be tested
     * @return {@code true} if this map contains a mapping for the specified key
     */
    public boolean containsKey(final long key) {
        final TableEntry<V> node = this.getNode(key);
        // Must check value is non-null, as getNode might return a placeholder
        return node != null && node.getValueVolatile() != null; // Volatile read for visibility
    }

    /**
     * Returns {@code true} if this map maps one or more keys to the specified value.
     * Note: This operation requires traversing the entire map.
     *
     * @param value value whose presence in this map is to be tested
     * @return {@code true} if this map maps one or more keys to the specified value
     * @throws NullPointerException if the specified value is null
     */
    public boolean containsValue(final V value) {
        Validate.notNull(value, "Value cannot be null");
        // Use an iterator that handles concurrent modifications and resizes safely.
        // The NodeIterator is designed for internal use and handles table traversal.
        NodeIterator<V> iterator = new NodeIterator<>(this.table, this); // Pass map ref if needed
        TableEntry<V> node;
        while ((node = iterator.findNext()) != null) { // findNext safely iterates through nodes
            V nodeValue = node.getValueVolatile(); // Volatile read for visibility
            // Use Objects.equals for correct value comparison (handles null nodeValue safely if possible, though values shouldn't be null)
            if (nodeValue != null && value.equals(nodeValue)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the number of key-value mappings in this map. If the
     * number of elements exceeds {@code Integer.MAX_VALUE}, returns
     * {@code Integer.MAX_VALUE}.
     *
     * @return the number of key-value mappings in this map
     */
    public int size() {
        final long ret = this.size.sum();
        // Cap the size at Integer.MAX_VALUE as per ConcurrentMap contract
        return (ret >= (long) Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) ret;
    }

    /**
     * Returns {@code true} if this map contains no key-value mappings.
     *
     * @return {@code true} if this map contains no key-value mappings
     */
    public boolean isEmpty() {
        // Check size first for a quick exit, but verify with iteration if size is 0
        // as LongAdder.sum() might be transiently inaccurate.
        if (this.size.sum() > 0L) {
            return false;
        }
        // If size reports 0, double-check by looking for any actual node
        NodeIterator<V> it = new NodeIterator<>(this.table, this);
        return it.findNext() == null;
    }

    /**
     * Increments the size count and initiates resizing if the threshold is exceeded.
     */
    protected final void addSize(final long count) {
        this.size.add(count);
        // Check if resize is needed
        int currentThreshold;
        // Loop handles potential races in threshold checking/updating
        do {
            currentThreshold = this.getThresholdAcquire(); // Acquire fence for reading threshold
            // If resizing is disabled or already in progress, nothing to do
            if (currentThreshold <= 0) return; // THRESHOLD_NO_RESIZE or THRESHOLD_RESIZING

            final long currentSum = this.size.sum(); // Get current estimated size
            // Check if size is below the threshold
            if (currentSum < (long) currentThreshold) {
                // Double check threshold hasn't changed due to another thread finishing resize
                if (currentThreshold == this.getThresholdVolatile()) return;
                // Threshold changed, retry the loop
                continue;
            }

            // Size exceeds threshold, attempt to initiate resize by setting threshold to RESIZING
            // Use CAS to ensure only one thread initiates the resize
            if (this.compareExchangeThresholdVolatile(currentThreshold, THRESHOLD_RESIZING) == currentThreshold) {
                // CAS succeeded, this thread is responsible for resizing
                this.resize(currentSum); // Pass estimated size to resize method
                return; // Resize initiated or completed, exit
            }
            // CAS failed, another thread initiated resize. Loop might retry if needed, but usually exits.
        } while (true);
    }

    /**
     * Decrements the size count.
     */
    protected final void subSize(final long count) {
        this.size.add(-count);
        // Note: No resize check needed on removal in this implementation
    }

    /**
     * Resizes the table to accommodate more entries. Called by the thread
     * that successfully sets the threshold to THRESHOLD_RESIZING.
     */
    @SuppressWarnings("unchecked")
    private void resize(final long estimatedSize) { // estimatedSize might not be perfectly accurate
        final TableEntry<V>[] oldTable = this.table; // Volatile read
        final int oldCapacity = oldTable.length;

        // Check if already at maximum capacity
        if (oldCapacity >= MAXIMUM_CAPACITY) {
            this.setThresholdVolatile(THRESHOLD_NO_RESIZE); // Mark as non-resizable
            return;
        }

        // Calculate new capacity (typically double, capped at MAXIMUM_CAPACITY)
        int newCapacity = oldCapacity << 1; // Double the capacity
        if (newCapacity <= oldCapacity || newCapacity > MAXIMUM_CAPACITY) { // Handle overflow or exceeding max
            newCapacity = MAXIMUM_CAPACITY;
        }
        if (newCapacity == oldCapacity) { // If doubling didn't change capacity (already maxed out)
            this.setThresholdVolatile(THRESHOLD_NO_RESIZE);
            return;
        }

        // Calculate the new resize threshold
        final int newThreshold = getTargetThreshold(newCapacity, this.loadFactor);
        // Create the new table array
        final TableEntry<V>[] newTable = (TableEntry<V>[]) new TableEntry[newCapacity];
        // Create the resize marker node containing the reference to the new table
        final TableEntry<V> resizeMarker = new TableEntry<>(0L, (V) newTable, true); // Key is irrelevant for marker

        // Transfer nodes from the old table to the new table
        boolean allMarked = true; // Track if all bins are processed
        for (int i = 0; i < oldCapacity; ++i) {
            TableEntry<V> head = getAtIndexVolatile(oldTable, i); // Get current head of the bin

            if (head == null) {
                // Bin is empty, try to place the resize marker
                // Use CAS to atomically mark the bin as processed
                if (compareAndExchangeAtIndexVolatile(oldTable, i, null, resizeMarker) == null) {
                    continue; // Successfully marked empty bin
                }
                // CAS failed, another thread might have added an entry or marked it. Re-read.
                head = getAtIndexVolatile(oldTable, i);
                if (head == null || head.isResizeMarker()) continue; // Already handled or still null (race?)
            }

            // If head is already a marker, this bin is processed by another thread
            if (head.isResizeMarker()) {
                continue;
            }

            // Bin has entries, acquire lock on the head node to transfer its chain
            synchronized (head) {
                // Re-check head node after acquiring lock to prevent races
                TableEntry<V> currentHead = getAtIndexVolatile(oldTable, i);
                if (currentHead != head) {
                    // Head changed while waiting for lock (e.g., removed/replaced). Retry processing bin i.
                    // This could lead to re-locking, but ensures consistency. A different strategy
                    // might be to just continue, assuming another thread is handling it.
                    // Retrying is safer.
                    i--; // Decrement i to reprocess this index in the next iteration
                    continue;
                }
                if (head.isResizeMarker()) {
                    continue; // Another thread marked it while we waited for the lock
                }

                // --- Split the chain into two: one for the original index, one for the new index offset ---
                // Nodes stay at index 'i' or move to index 'i + oldCapacity' in the new table.
                TableEntry<V> lowH = null, lowT = null;   // Head and Tail for the low bin (index i)
                TableEntry<V> highH = null, highT = null; // Head and Tail for the high bin (index i + oldCapacity)

                TableEntry<V> current = head;
                while (current != null) {
                    TableEntry<V> next = current.getNextPlain(); // Plain read inside lock
                    int hash = getHash(current.key);

                    // Check the bit corresponding to the old capacity to decide the new bin
                    if ((hash & oldCapacity) == 0) { // Stays in the low bin (index i)
                        if (lowT == null) lowH = current; // First node for low bin
                        else lowT.setNextPlain(current); // Append to tail
                        lowT = current; // Update tail
                    } else { // Moves to the high bin (index i + oldCapacity)
                        if (highT == null) highH = current; // First node for high bin
                        else highT.setNextPlain(current); // Append to tail
                        highT = current; // Update tail
                    }
                    current = next;
                }

                // Terminate the new chains
                if (lowT != null) lowT.setNextPlain(null);
                if (highT != null) highT.setNextPlain(null);

                // --- Place the new chains into the new table ---
                // Use volatile writes for visibility to threads reading the new table
                setAtIndexVolatile(newTable, i, lowH);
                setAtIndexVolatile(newTable, i + oldCapacity, highH);

                // --- Mark the old bin as processed ---
                // Use release write to ensure new table writes are visible before the marker
                setAtIndexRelease(oldTable, i, resizeMarker);
            } // End synchronized block
        } // End loop over old table bins

        // --- Finalize the resize ---
        // Volatile write to publish the new table
        this.table = newTable;
        // Volatile write to set the new threshold, making the resize official
        this.setThresholdVolatile(newThreshold);
    }


    /**
     * Maps the specified key to the specified value in this table.
     * Neither the key nor the value can be null.
     *
     * <p>The value can be retrieved by calling the {@code get} method
     * with a key that is equal to the original key.
     *
     * @param key   key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with {@code key}, or
     *         {@code null} if there was no mapping for {@code key}.
     * @throws NullPointerException if the specified value is null
     */
    public V put(final long key, final V value) {
        Validate.notNull(value, "Value may not be null");
        final int hash = getHash(key);
        int sizeDelta = 0; // Tracks change in size
        V oldValue = null; // Stores the old value if replaced
        TableEntry<V>[] currentTable = this.table; // Volatile read

        table_loop:
        for (;;) {
            final int tableLength = currentTable.length;
            // Basic init check, should normally not be needed after constructor
            if (tableLength == 0) { currentTable = this.table; if (currentTable.length == 0) continue; }

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            // Case 1: Bin is empty
            if (head == null) {
                TableEntry<V> newNode = new TableEntry<>(key, value);
                // Attempt to CAS the new node as the head
                if (compareAndExchangeAtIndexVolatile(currentTable, index, null, newNode) == null) {
                    // Success! Inserted the new node.
                    this.addSize(1L); // Increment size and check threshold
                    return null; // No previous value
                }
                // CAS failed, something changed (another thread inserted or resize started). Retry.
                continue table_loop;
            }

            // Case 2: Bin head is a resize marker
            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop; // Retry with the new table
            }

            // Case 3: Bin is not empty and not resizing. Try lock-free update first.
            TableEntry<V> node = head;
            while (node != null) {
                if (node.key == key) {
                    // Key found. Attempt lock-free replacement.
                    V currentVal = node.getValueVolatile(); // Volatile read
                    if (currentVal == null) {
                        // Value is null (placeholder). Cannot CAS replace, need lock.
                        break; // Fall through to locking path
                    }
                    // Try to CAS the value from currentVal to the new value
                    if (node.compareAndSetValueVolatile(currentVal, value)) {
                        // Lock-free update succeeded!
                        return currentVal; // Return the old value
                    }
                    // CAS failed (value changed concurrently). Fall through to locking path.
                    break;
                }
                node = node.getNextVolatile(); // Volatile read
            }

            // Case 4: Fallback to locking path (key not found in lock-free check, or CAS failed, or placeholder found)
            synchronized (head) {
                // Re-check head and resize status after acquiring lock
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    // Head changed or resize started while waiting for lock. Retry outer loop.
                    continue table_loop;
                }

                // Traverse chain again within the lock
                TableEntry<V> prev = null;
                node = head;
                while (node != null) {
                    if (node.key == key) {
                        // Key found within lock. Replace value and return old one.
                        oldValue = node.getValuePlain(); // Plain read inside lock
                        node.setValueVolatile(value); // Volatile write for visibility
                        // If old value was null (placeholder), size increases
                        sizeDelta = (oldValue == null) ? 1 : 0;
                        break table_loop; // Exit outer loop
                    }
                    prev = node;
                    node = node.getNextPlain(); // Plain read inside lock
                }

                // Key not found in chain. Add new node at the end.
                // Ensure 'prev' is not null (should be head if chain had only one node)
                if (prev != null) {
                    TableEntry<V> newNode = new TableEntry<>(key, value);
                    prev.setNextRelease(newNode); // Release write to link the new node safely
                    sizeDelta = 1; // Added a new mapping
                    oldValue = null; // No previous value
                } else {
                    // Should not happen if head was non-null and non-marker. Indicates inconsistency.
                    // Retry might be safest.
                    continue table_loop;
                }
            } // End synchronized block
            // Break outer loop as operation completed within the lock
            break table_loop;
        } // End table_loop

        // Update size if necessary (outside the lock)
        if (sizeDelta != 0) {
            this.addSize(sizeDelta);
        }
        return oldValue;
    }


    /**
     * If the specified key is not already associated with a value, associates
     * it with the given value. This is equivalent to
     * <pre> {@code
     * if (!map.containsKey(key))
     *   return map.put(key, value);
     * else
     *   return map.get(key);
     * }</pre>
     * except that the action is performed atomically.
     *
     * @param key   key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with the specified key, or
     *         {@code null} if there was no mapping for the key.
     * @throws NullPointerException if the specified value is null
     */
    public V putIfAbsent(final long key, final V value) {
        Validate.notNull(value, "Value may not be null");
        final int hash = getHash(key);
        int sizeDelta = 0;
        V existingValue = null;
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for(;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) { currentTable = this.table; continue; }

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            // Case 1: Bin is empty
            if (head == null) {
                TableEntry<V> newNode = new TableEntry<>(key, value);
                if (compareAndExchangeAtIndexVolatile(currentTable, index, null, newNode) == null) {
                    // Successfully inserted
                    this.addSize(1L);
                    return null; // No previous value
                }
                // CAS failed, retry
                continue table_loop;
            }

            // Case 2: Resize marker
            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop;
            }

            // Case 3: Bin not empty. Check head quickly (lock-free).
            if (head.key == key) {
                existingValue = head.getValueVolatile(); // Volatile read
                if (existingValue != null) {
                    return existingValue; // Key already present with non-null value
                }
                // Head is the key, but value is null (placeholder). Need lock.
            }

            // Case 4: Locking path needed
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    // Head changed or resize started. Retry.
                    continue table_loop;
                }

                // Traverse chain within lock
                TableEntry<V> prev = null;
                TableEntry<V> node = head;
                while (node != null) {
                    if (node.key == key) {
                        existingValue = node.getValuePlain(); // Plain read inside lock
                        if (existingValue != null) {
                            // Key already exists with a value
                            break table_loop; // Return existing value
                        } else {
                            // Key exists but is a placeholder (value is null).
                            // Update the placeholder with the new value.
                            node.setValueVolatile(value); // Volatile write
                            sizeDelta = 1; // Effectively added a mapping
                            existingValue = null; // Return null as per putIfAbsent contract
                            break table_loop;
                        }
                    }
                    prev = node;
                    node = node.getNextPlain(); // Plain read inside lock
                }

                // Key not found in chain. Add new node.
                if (prev != null) {
                    TableEntry<V> newNode = new TableEntry<>(key, value);
                    prev.setNextRelease(newNode); // Release write
                    sizeDelta = 1;
                    existingValue = null;
                } else {
                    // Should not happen. Retry.
                    continue table_loop;
                }
            } // End synchronized block
            break table_loop; // Exit loop after lock path completes
        } // End table_loop

        if (sizeDelta != 0) {
            this.addSize(sizeDelta);
        }
        return existingValue; // Return null if added, or existing value if found
    }

    /**
     * Replaces the entry for a key only if currently mapped to some value.
     * This is equivalent to
     * <pre> {@code
     * if (map.containsKey(key)) {
     *   return map.put(key, value);
     * } else
     *   return null;
     * }</pre>
     * except that the action is performed atomically.
     *
     * @param key   key with which the specified value is associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with the specified key, or
     *         {@code null} if there was no mapping for the key.
     * @throws NullPointerException if the specified value is null
     */
    public V replace(final long key, final V value) {
        Validate.notNull(value, "Value may not be null");
        final int hash = getHash(key);
        V oldValue = null;
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for(;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) return null; // Map not initialized or empty

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            if (head == null) return null; // Key not present

            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop; // Retry with new table
            }

            // Try Lock-Free Replace Attempt
            TableEntry<V> node = head;
            while (node != null) {
                if (node.key == key) {
                    // Loop for CAS retry
                    do {
                        oldValue = node.getValueVolatile(); // Volatile read
                        if (oldValue == null) {
                            // Key exists but is a placeholder, cannot replace. Need lock to confirm.
                            // Or, treat as not mapped, return null immediately?
                            // Standard 'replace' usually ignores placeholders. Return null.
                            return null; // Don't replace if not currently mapped to a *value*
                        }
                        // Attempt CAS
                        if (node.compareAndSetValueVolatile(oldValue, value)) {
                            return oldValue; // Lock-free success!
                        }
                        // CAS failed, value changed. Loop re-reads oldValue and retries CAS.
                        // If key changes (e.g., node removed), loop condition `node.key == key` might fail?
                        // Let's ensure we only retry if the node is still the one we expect
                    } while (node.key == key); // Re-check key in case node structure changed drastically
                    // If key changed or CAS keeps failing, fall back to lock
                    break; // Exit inner loop, proceed to locking path
                }
                node = node.getNextVolatile();
            }

            // Fallback to Locking Path
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // Head changed, retry
                }
                node = head;
                while (node != null) {
                    if (node.key == key) {
                        oldValue = node.getValuePlain(); // Plain read inside lock
                        if (oldValue != null) { // Only replace if currently mapped to a value
                            node.setValueVolatile(value); // Volatile write
                            return oldValue;
                        } else {
                            // Key exists but has null value (placeholder). Do not replace.
                            return null;
                        }
                    }
                    node = node.getNextPlain(); // Plain read inside lock
                }
            } // End synchronized block

            // Key not found after lock-free check and locked check
            return null;
        } // End table_loop
    }

    /**
     * Replaces the entry for a key only if currently mapped to a given value.
     * This is equivalent to
     * <pre> {@code
     * if (map.containsKey(key) && Objects.equals(map.get(key), expect)) {
     *   map.put(key, update);
     *   return true;
     * } else
     *   return false;
     * }</pre>
     * except that the action is performed atomically.
     *
     * @param key    key with which the specified value is associated
     * @param expect value expected to be associated with the specified key
     * @param update value to be associated with the specified key
     * @return {@code true} if the value was replaced
     * @throws NullPointerException if {@code expect} or {@code update} is null
     */
    public boolean replace(final long key, final V expect, final V update) {
        Validate.notNull(expect, "Expected value may not be null");
        Validate.notNull(update, "Update value may not be null");
        final int hash = getHash(key);
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for(;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) return false;

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            if (head == null) return false; // Key not present

            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop; // Retry with new table
            }

            // Try Lock-Free CAS Replace Attempt
            TableEntry<V> node = head;
            while (node != null) {
                if (node.key == key) {
                    V currentVal = node.getValueVolatile(); // Volatile read
                    // Use Objects.equals for comparison, handles null currentVal correctly
                    if (!Objects.equals(currentVal, expect)) {
                        // Value does not match expectation. Stop searching this chain.
                        return false;
                    }
                    // Value matches, attempt CAS
                    if (node.compareAndSetValueVolatile(expect, update)) {
                        return true; // Lock-free success!
                    }
                    // CAS failed (value changed concurrently). Need lock.
                    break; // Exit inner loop, proceed to locking path
                }
                node = node.getNextVolatile();
            }

            // Fallback to Locking Path
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // Head changed, retry
                }
                node = head;
                while (node != null) {
                    if (node.key == key) {
                        V currentVal = node.getValuePlain(); // Plain read inside lock
                        if (Objects.equals(currentVal, expect)) { // Use Objects.equals
                            node.setValueVolatile(update); // Volatile write
                            return true; // Replacement successful
                        } else {
                            // Value doesn't match within lock
                            return false;
                        }
                    }
                    node = node.getNextPlain(); // Plain read inside lock
                }
            } // End synchronized block

            // Key not found after lock-free check and locked check
            return false;
        } // End table_loop
    }

    /**
     * Removes the mapping for a key from this map if it is present.
     * Returns the value to which this map previously associated the key,
     * or {@code null} if the map contained no mapping for the key.
     *
     * @param key key whose mapping is to be removed from the map
     * @return the previous value associated with {@code key}, or
     *         {@code null} if there was no mapping for {@code key}
     */
    public V remove(final long key) {
        final int hash = getHash(key);
        int sizeDelta = 0;
        V oldValue = null;
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for(;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) return null;

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            if (head == null) return null; // Key not present

            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop; // Retry with new table
            }

            // Removal requires structural modification, always use locking path
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // Head changed, retry
                }

                TableEntry<V> prev = null;
                TableEntry<V> node = head;
                while (node != null) {
                    if (node.key == key) {
                        // Key found. Remove the node.
                        oldValue = node.getValuePlain(); // Plain read inside lock
                        // Only decrement size if it was actually mapped to a value
                        sizeDelta = (oldValue != null) ? -1 : 0;

                        TableEntry<V> next = node.getNextPlain(); // Plain read inside lock
                        // Update links using release semantics for visibility
                        if (prev == null) {
                            // Removing the head node
                            setAtIndexRelease(currentTable, index, next);
                        } else {
                            // Removing node in the middle or end
                            prev.setNextRelease(next);
                        }
                        break table_loop; // Node removed, exit outer loop
                    }
                    prev = node;
                    node = node.getNextPlain(); // Plain read inside lock
                }
                // Key not found in chain within lock
                break table_loop; // Exit outer loop
            } // End synchronized block
        } // End table_loop

        // Update size if a mapping was removed
        if (sizeDelta != 0) {
            this.subSize(-sizeDelta); // subSize expects positive count
        }
        return oldValue;
    }


    /**
     * Removes the entry for a key only if currently mapped to a given value.
     * This is equivalent to
     * <pre> {@code
     * if (map.containsKey(key) && Objects.equals(map.get(key), value)) {
     *   map.remove(key);
     *   return true;
     * } else
     *   return false;
     * }</pre>
     * except that the action is performed atomically.
     *
     * @param key   key with which the specified value is associated
     * @param expect value expected to be associated with the specified key
     * @return {@code true} if the value was removed
     * @throws NullPointerException if the specified value is null (though behavior with null `expect` might vary, standard maps often allow it)
     *         Current implementation assumes `expect` is non-null due to `V` constraint, but uses `Objects.equals`.
     */
    public boolean remove(final long key, final V expect) {
        // Validate.notNull(expect, "Expected value may not be null"); // Consider if null check is needed based on V constraints
        final int hash = getHash(key);
        int sizeDelta = 0;
        boolean removed = false;
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for(;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) return false;

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            if (head == null) return false; // Key not present

            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop; // Retry with new table
            }

            // Removal requires structural modification, always use locking path
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // Head changed, retry
                }

                TableEntry<V> prev = null;
                TableEntry<V> node = head;
                while (node != null) {
                    if (node.key == key) {
                        // Key found. Check if value matches expectation.
                        V currentVal = node.getValuePlain(); // Plain read inside lock
                        if (Objects.equals(currentVal, expect)) { // Use Objects.equals for safe comparison
                            // Value matches. Remove the node.
                            removed = true;
                            // Only decrement size if it was mapped to a non-null value (matching 'expect')
                            sizeDelta = (currentVal != null) ? -1 : 0;

                            TableEntry<V> next = node.getNextPlain(); // Plain read inside lock
                            // Update links using release semantics
                            if (prev == null) {
                                setAtIndexRelease(currentTable, index, next);
                            } else {
                                prev.setNextRelease(next);
                            }
                        } else {
                            // Key found, but value does not match. Do not remove.
                            removed = false;
                        }
                        break table_loop; // Key processed, exit outer loop
                    }
                    prev = node;
                    node = node.getNextPlain(); // Plain read inside lock
                }
                // Key not found in chain within lock
                break table_loop; // Exit outer loop
            } // End synchronized block
        } // End table_loop

        // Update size if removal occurred
        if (sizeDelta != 0) {
            this.subSize(-sizeDelta); // subSize expects positive count
        }
        return removed;
    }

    /**
     * Removes the entry for the specified key only if its value satisfies the given predicate.
     *
     * @param key key whose mapping is to be removed from the map
     * @param predicate the predicate to apply to the value associated with the key
     * @return the value associated with the key before removal if the predicate was satisfied and the entry was removed,
     *         otherwise {@code null}.
     * @throws NullPointerException if the specified predicate is null
     */
    public V removeIf(final long key, final Predicate<? super V> predicate) {
        Validate.notNull(predicate, "Predicate may not be null");
        final int hash = getHash(key);
        int sizeDelta = 0;
        V oldValue = null;
        boolean removed = false;
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for(;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) return null;

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            if (head == null) return null; // Key not present

            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop; // Retry with new table
            }

            // Requires locking due to conditional removal and structural change
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // Head changed, retry
                }

                TableEntry<V> prev = null;
                TableEntry<V> node = head;
                while (node != null) {
                    if (node.key == key) {
                        // Key found. Evaluate predicate on the value.
                        oldValue = node.getValuePlain(); // Plain read inside lock
                        // Only test predicate if value is non-null (consistent with Map behavior)
                        if (oldValue != null && predicate.test(oldValue)) {
                            // Predicate satisfied. Remove the node.
                            removed = true;
                            sizeDelta = -1; // Decrement size

                            TableEntry<V> next = node.getNextPlain(); // Plain read inside lock
                            // Update links using release semantics
                            if (prev == null) {
                                setAtIndexRelease(currentTable, index, next);
                            } else {
                                prev.setNextRelease(next);
                            }
                        } else {
                            // Predicate not satisfied or value was null. Do not remove.
                            removed = false;
                        }
                        break table_loop; // Key processed, exit outer loop
                    }
                    prev = node;
                    node = node.getNextPlain(); // Plain read inside lock
                }
                // Key not found in chain within lock
                break table_loop; // Exit outer loop
            } // End synchronized block
        } // End table_loop

        // Update size if removal occurred
        if (sizeDelta != 0) {
            this.subSize(-sizeDelta); // subSize expects positive count
        }
        // Return the old value only if removed
        return removed ? oldValue : null;
    }

    // --- Compute Methods ---

    /**
     * Attempts to compute a mapping for the specified key and its current mapped value
     * (or {@code null} if there is no current mapping). The function is
     * applied atomically.
     *
     * <p>If the function returns {@code null}, the mapping is removed (or remains absent
     * if initially absent). If the function returns a non-null value, the mapping is
     * updated or added with the resulting value.</p>
     *
     * <p><b>Warning:</b> The computation function should not attempt to modify this map
     * during computation, as it may lead to deadlock.</p>
     *
     * @param key key with which the specified value is to be associated
     * @param function the function to compute a value
     * @return the new value associated with the specified key, or null if none
     * @throws NullPointerException if the specified function is null
     */
    public V compute(final long key, final BiLong1Function<? super V, ? extends V> function) {
        Validate.notNull(function, "Function cannot be null");
        final int hash = getHash(key);
        int sizeDelta = 0;
        V finalValue = null;
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for(;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) { currentTable = this.table; continue; } // Handle init race

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            // Case 1: Bin is empty. Need special handling to compute.
            if (head == null) {
                // Create a temporary placeholder node.
                // Lock on this placeholder to ensure atomicity for the initial computation.
                TableEntry<V> placeholder = new TableEntry<>(key, null); // Value starts null
                V computedValue;
                synchronized (placeholder) {
                    // Double-check if bin is still empty after acquiring lock on placeholder
                    if (getAtIndexVolatile(currentTable, index) == null) {
                        // Bin is still empty, compute the value using null as the old value
                        try {
                            computedValue = function.apply(key, null);
                        } catch (Throwable t) {
                            // Function threw exception, ensure no changes are made
                            ThrowUtil.throwUnchecked(t);
                            return null; // Should be unreachable
                        }

                        if (computedValue != null) {
                            // Function returned a non-null value.
                            // Set the value in the placeholder *before* attempting CAS
                            placeholder.setValuePlain(computedValue); // Plain write inside sync
                            // Attempt to CAS the placeholder into the table
                            if (compareAndExchangeAtIndexVolatile(currentTable, index, null, placeholder) == null) {
                                // CAS succeeded. New mapping added.
                                sizeDelta = 1;
                                finalValue = computedValue;
                                break table_loop; // Done
                            } else {
                                // CAS failed. Bin changed concurrently. Retry outer loop.
                                continue table_loop;
                            }
                        } else {
                            // Function returned null. No mapping should be added.
                            finalValue = null;
                            break table_loop; // Done
                        }
                    }
                    // Bin is not empty anymore (changed while waiting for placeholder lock). Fall through to retry.
                } // End synchronized (placeholder)
                // Retry outer loop as bin state changed
                continue table_loop;
            } // End Case 1 (head == null)

            // Case 2: Resize marker
            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop;
            }

            // Case 3: Bin is not empty. Lock the head node.
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // Head changed, retry
                }

                TableEntry<V> prev = null;
                TableEntry<V> node = head;
                while (node != null) {
                    if (node.key == key) {
                        // Key found. Compute with the existing value.
                        V oldValue = node.getValuePlain(); // Plain read inside lock
                        V computedValue;
                        try {
                            computedValue = function.apply(key, oldValue);
                        } catch (Throwable t) { ThrowUtil.throwUnchecked(t); return null; } // Unreachable

                        if (computedValue != null) {
                            // Update existing node
                            node.setValueVolatile(computedValue); // Volatile write
                            finalValue = computedValue;
                            sizeDelta = (oldValue == null) ? 1 : 0; // Size increases if old value was null (placeholder)
                        } else {
                            // Remove existing mapping
                            finalValue = null;
                            sizeDelta = (oldValue != null) ? -1 : 0; // Size decreases only if value was non-null
                            TableEntry<V> next = node.getNextPlain(); // Plain read
                            if (prev == null) setAtIndexRelease(currentTable, index, next);
                            else prev.setNextRelease(next);
                        }
                        break table_loop; // Done
                    }
                    prev = node;
                    node = node.getNextPlain(); // Plain read
                } // End while (node != null)

                // Key not found in chain. Compute with null as old value.
                V computedValue;
                try {
                    computedValue = function.apply(key, null);
                } catch (Throwable t) { ThrowUtil.throwUnchecked(t); return null; } // Unreachable

                if (computedValue != null) {
                    // Add new mapping
                    finalValue = computedValue;
                    sizeDelta = 1;
                    TableEntry<V> newNode = new TableEntry<>(key, computedValue);
                    if (prev != null) prev.setNextRelease(newNode); // Release write
                    else { /* Should not happen if head locked */ continue table_loop; }
                } else {
                    // Function returned null, do nothing
                    finalValue = null;
                    sizeDelta = 0;
                }
                break table_loop; // Done
            } // End synchronized(head)
        } // End table_loop

        // Update size outside lock
        if (sizeDelta > 0) this.addSize(sizeDelta);
        else if (sizeDelta < 0) this.subSize(-sizeDelta);

        return finalValue;
    }

    /**
     * If the specified key is not already associated with a value, attempts to
     * compute its value using the given mapping function and enters it into
     * this map unless {@code null}. The function is applied atomically.
     *
     * <p><b>Warning:</b> The computation function should not attempt to modify this map
     * during computation, as it may lead to deadlock.</p>
     *
     * @param key key with which the specified value is to be associated
     * @param function the function to compute a value
     * @return the current (existing or computed) value associated with the specified key,
     *         or null if the computed value is null
     * @throws NullPointerException if the specified function is null
     */
    public V computeIfAbsent(final long key, final LongFunction<? extends V> function) {
        Validate.notNull(function, "Function cannot be null");
        final int hash = getHash(key);
        int sizeDelta = 0;
        V finalValue = null; // Can be existing or computed value
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for(;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) { currentTable = this.table; continue; }

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            // Case 1: Bin is empty. Use placeholder logic.
            if (head == null) {
                TableEntry<V> placeholder = new TableEntry<>(key, null);
                V computedValue;
                synchronized (placeholder) {
                    if (getAtIndexVolatile(currentTable, index) == null) {
                        try {
                            computedValue = function.apply(key);
                        } catch (Throwable t) { ThrowUtil.throwUnchecked(t); return null; }

                        if (computedValue != null) {
                            placeholder.setValuePlain(computedValue);
                            if (compareAndExchangeAtIndexVolatile(currentTable, index, null, placeholder) == null) {
                                sizeDelta = 1;
                                finalValue = computedValue;
                                break table_loop;
                            } else {
                                continue table_loop; // CAS failed, retry
                            }
                        } else {
                            finalValue = null; // Computed null, don't add
                            break table_loop;
                        }
                    }
                } // End synchronized(placeholder)
                continue table_loop; // Bin changed, retry
            } // End Case 1 (head == null)

            // Case 2: Resize marker
            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop;
            }

            // Case 3: Bin not empty. Quick lock-free check.
            // Avoids locking if key is already present with a non-null value.
            TableEntry<V> node = head;
            while (node != null) {
                if (node.key == key) {
                    V existingValue = node.getValueVolatile(); // Volatile read
                    if (existingValue != null) {
                        return existingValue; // Key already present, return existing value
                    }
                    // Key found, but value is null (placeholder). Need lock.
                    break; // Proceed to locking path
                }
                node = node.getNextVolatile();
            }

            // Case 4: Locking path needed (key not found lock-free, or placeholder found).
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // Head changed, retry
                }

                TableEntry<V> prev = null;
                node = head;
                while (node != null) {
                    if (node.key == key) {
                        V existingValue = node.getValuePlain(); // Plain read inside lock
                        if (existingValue != null) {
                            // Key found with value inside lock
                            finalValue = existingValue;
                        } else {
                            // Key exists as placeholder. Compute and update.
                            V computedValue;
                            try {
                                computedValue = function.apply(key);
                            } catch (Throwable t) { ThrowUtil.throwUnchecked(t); return null; }

                            if (computedValue != null) {
                                node.setValueVolatile(computedValue); // Volatile write
                                sizeDelta = 1;
                                finalValue = computedValue;
                            } else {
                                finalValue = null; // Computed null
                            }
                        }
                        break table_loop; // Done
                    }
                    prev = node;
                    node = node.getNextPlain(); // Plain read
                } // End while (node != null)

                // Key not found in chain. Compute and add.
                V computedValue;
                try {
                    computedValue = function.apply(key);
                } catch (Throwable t) { ThrowUtil.throwUnchecked(t); return null; }

                if (computedValue != null) {
                    finalValue = computedValue;
                    sizeDelta = 1;
                    TableEntry<V> newNode = new TableEntry<>(key, computedValue);
                    if (prev != null) prev.setNextRelease(newNode); // Release write
                    else { /* Should not happen */ continue table_loop; }
                } else {
                    finalValue = null;
                    sizeDelta = 0;
                }
                break table_loop; // Done
            } // End synchronized(head)
        } // End table_loop

        if (sizeDelta > 0) this.addSize(sizeDelta);
        return finalValue;
    }


    /**
     * If the value for the specified key is present, attempts to compute a new
     * mapping given the key and its current mapped value. The function is
     * applied atomically. If the function returns {@code null}, the mapping is removed.
     *
     * <p><b>Warning:</b> The computation function should not attempt to modify this map
     * during computation, as it may lead to deadlock.</p>
     *
     * @param key key with which the specified value is to be associated
     * @param function the function to compute a value
     * @return the new value associated with the specified key, or null if none
     * @throws NullPointerException if the specified function is null
     */
    public V computeIfPresent(final long key, final BiLong1Function<? super V, ? extends V> function) {
        Validate.notNull(function, "Function cannot be null");
        final int hash = getHash(key);
        int sizeDelta = 0;
        V finalValue = null;
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for(;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) return null; // Map empty

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            if (head == null) return null; // Key not present

            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop;
            }

            // Lock is always needed as we might remove the node
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // Head changed, retry
                }

                TableEntry<V> prev = null;
                TableEntry<V> node = head;
                while (node != null) {
                    if (node.key == key) {
                        // Key found.
                        V oldValue = node.getValuePlain(); // Plain read inside lock
                        if (oldValue != null) {
                            // Value is present, apply function
                            V computedValue;
                            try {
                                computedValue = function.apply(key, oldValue);
                            } catch (Throwable t) { ThrowUtil.throwUnchecked(t); return null; }

                            if (computedValue != null) {
                                // Update value
                                node.setValueVolatile(computedValue); // Volatile write
                                finalValue = computedValue;
                                sizeDelta = 0; // Size doesn't change
                            } else {
                                // Function returned null, remove mapping
                                finalValue = null;
                                sizeDelta = -1; // Size decreases
                                TableEntry<V> next = node.getNextPlain(); // Plain read
                                if (prev == null) setAtIndexRelease(currentTable, index, next);
                                else prev.setNextRelease(next);
                            }
                        } else {
                            // Value is null (placeholder), treat as absent. Do nothing.
                            finalValue = null;
                            sizeDelta = 0;
                        }
                        break table_loop; // Done
                    }
                    prev = node;
                    node = node.getNextPlain(); // Plain read
                } // End while (node != null)

                // Key not found in chain. Do nothing.
                finalValue = null;
                sizeDelta = 0;
                break table_loop; // Done
            } // End synchronized(head)
        } // End table_loop

        if (sizeDelta < 0) this.subSize(-sizeDelta);
        return finalValue;
    }

    /**
     * If the specified key is not already associated with a value or is
     * associated with null, associates it with the given non-null value.
     * Otherwise, replaces the associated value with the results of the given
     * remapping function, or removes if the result is {@code null}.
     * The function is applied atomically.
     *
     * <p><b>Warning:</b> The computation function should not attempt to modify this map
     * during computation, as it may lead to deadlock.</p>
     *
     * @param key key with which the resulting value is to be associated
     * @param value the non-null value to be merged with the existing value
     *        associated with the key or, if none, associated with the key
     * @param function the function to recompute a value if present
     * @return the new value associated with the specified key, or null if no
     *         value is associated with the key
     * @throws NullPointerException if the specified value or function is null
     */
    public V merge(final long key, final V value, final BiFunction<? super V, ? super V, ? extends V> function) {
        Validate.notNull(value, "Value cannot be null");
        Validate.notNull(function, "Function cannot be null");
        final int hash = getHash(key);
        int sizeDelta = 0;
        V finalValue = null;
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for(;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) { currentTable = this.table; continue; }

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            // Case 1: Bin is empty. Insert the given value.
            if (head == null) {
                TableEntry<V> newNode = new TableEntry<>(key, value);
                if (compareAndExchangeAtIndexVolatile(currentTable, index, null, newNode) == null) {
                    sizeDelta = 1;
                    finalValue = value;
                    break table_loop; // Done
                }
                continue table_loop; // CAS failed, retry
            }

            // Case 2: Resize marker
            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop;
            }

            // Case 3: Bin not empty. Lock the head.
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // Head changed, retry
                }

                TableEntry<V> prev = null;
                TableEntry<V> node = head;
                while (node != null) {
                    if (node.key == key) {
                        // Key found. Apply merge logic.
                        V oldValue = node.getValuePlain(); // Plain read inside lock
                        V computedValue;
                        if (oldValue != null) {
                            // Apply function if old value exists
                            try {
                                computedValue = function.apply(oldValue, value);
                            } catch (Throwable t) { ThrowUtil.throwUnchecked(t); return null; }
                        } else {
                            // Old value is null (placeholder), use the provided value directly
                            computedValue = value;
                        }

                        if (computedValue != null) {
                            // Update node
                            node.setValueVolatile(computedValue); // Volatile write
                            finalValue = computedValue;
                            sizeDelta = (oldValue == null) ? 1 : 0; // Size increases if old was placeholder
                        } else {
                            // Remove mapping
                            finalValue = null;
                            sizeDelta = (oldValue != null) ? -1 : 0; // Size decreases only if old value existed
                            TableEntry<V> next = node.getNextPlain(); // Plain read
                            if (prev == null) setAtIndexRelease(currentTable, index, next);
                            else prev.setNextRelease(next);
                        }
                        break table_loop; // Done
                    }
                    prev = node;
                    node = node.getNextPlain(); // Plain read
                } // End while (node != null)

                // Key not found in chain. Add the provided value.
                finalValue = value;
                sizeDelta = 1;
                TableEntry<V> newNode = new TableEntry<>(key, value);
                if (prev != null) prev.setNextRelease(newNode); // Release write
                else { /* Should not happen */ continue table_loop; }
                break table_loop; // Done
            } // End synchronized(head)
        } // End table_loop

        // Update size outside lock
        if (sizeDelta > 0) this.addSize(sizeDelta);
        else if (sizeDelta < 0) this.subSize(-sizeDelta);

        return finalValue;
    }


    /**
     * Removes all of the mappings from this map.
     * The map will be empty after this call returns.
     * This operation requires locking each bin sequentially.
     */
    public void clear() {
        long removedCount = 0L;
        TableEntry<V>[] currentTable = this.table; // Volatile read

        for (int i = 0; i < currentTable.length; ++i) {
            TableEntry<V> head = getAtIndexVolatile(currentTable, i);

            // Skip empty or already resizing bins
            if (head == null || head.isResizeMarker()) continue;

            // Lock the bin head to clear its contents
            synchronized (head) {
                // Re-check state after acquiring lock
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, i);
                if (currentHead != head || head.isResizeMarker()) {
                    // Bin state changed while waiting for lock. Skip or retry?
                    // Skipping is safer, assuming another thread (or resize) handled it.
                    continue;
                }

                // Count removals and null out the bin head
                TableEntry<V> node = head;
                while (node != null) {
                    if (node.getValuePlain() != null) { // Count only actual mappings, not placeholders
                        removedCount++;
                    }
                    node = node.getNextPlain(); // Plain read inside lock
                }
                // Set bin head to null with release semantics for visibility
                setAtIndexRelease(currentTable, i, null);
            } // End synchronized block
        } // End loop over table bins

        // Update the total size count
        if (removedCount > 0) {
            this.subSize(removedCount);
        }
    }

    // --- Iterators and Views ---

    /** Returns an iterator over the map entries. */
    public Iterator<TableEntry<V>> entryIterator() { return new EntryIterator<>(this); }

    /** Returns an iterator over the map entries (implements Iterable). */
    @Override public final Iterator<TableEntry<V>> iterator() { return this.entryIterator(); }

    /** Returns an iterator over the keys. */
    public PrimitiveIterator.OfLong keyIterator() { return new KeyIterator<>(this); }

    /** Returns an iterator over the values. */
    public Iterator<V> valueIterator() { return new ValueIterator<>(this); }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.
     * The collection supports element removal, which removes the corresponding
     * mapping from the map, via the {@code Iterator.remove},
     * {@code Collection.remove}, {@code removeAll}, {@code retainAll} and
     * {@code clear} operations. It does not support the {@code add} or
     * {@code addAll} operations.
     *
     * @return a view of the values contained in this map
     */
    public Collection<V> values() {
        Values<V> v = this.values; // Lazy initialization
        return (v != null) ? v : (this.values = new Values<>(this));
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.
     * The set supports element removal, which removes the corresponding
     * mapping from the map, via the {@code Iterator.remove},
     * {@code Set.remove}, {@code removeAll}, {@code retainAll}, and
     * {@code clear} operations. It does not support the {@code add} or
     * {@code addAll} operations.
     *
     * @return a set view of the mappings contained in this map
     */
    public Set<TableEntry<V>> entrySet() {
        EntrySet<V> es = this.entrySet; // Lazy initialization
        return (es != null) ? es : (this.entrySet = new EntrySet<>(this));
    }

    // --- Inner Classes: TableEntry, Iterators, Views ---

    /**
     * Represents a key-value mapping entry in the hash table.
     * Also used as a resize marker.
     */
    public static final class TableEntry<V> {
        // VarHandles for atomic access to table array elements and entry fields
        static final VarHandle TABLE_ENTRY_ARRAY_HANDLE;
        private static final VarHandle VALUE_HANDLE;
        private static final VarHandle NEXT_HANDLE;

        static {
            try {
                TABLE_ENTRY_ARRAY_HANDLE = ConcurrentUtil.getArrayHandle(TableEntry[].class);
                VALUE_HANDLE = ConcurrentUtil.getVarHandle(TableEntry.class, "value", Object.class);
                NEXT_HANDLE = ConcurrentUtil.getVarHandle(TableEntry.class, "next", TableEntry.class);
            } catch (Throwable t) {
                throw new Error("Failed to initialize TableEntry VarHandles", t);
            }
        }

        final long key; // The hash map key (final)
        private volatile V value; // The hash map value (volatile for visibility, non-null for mappings)
        private volatile TableEntry<V> next; // Link to next entry in the chain (volatile)
        private final boolean resizeMarker; // Flag indicating if this is a resize marker

        /** Constructor for regular map entries. */
        TableEntry(final long key, final V value) {
            this(key, value, false);
        }

        /** Constructor for potentially creating resize markers. */
        TableEntry(final long key, final V value, final boolean resize) {
            this.key = key;
            this.resizeMarker = resize;
            // Use plain set initially, visibility handled by insertion logic (CAS/Release/Volatile)
            this.setValuePlain(value);
        }

        // --- Public Accessors ---

        /** Returns the key for this entry. */
        public long getKey() { return this.key; }

        /**
         * Returns the value corresponding to this entry.
         * Uses volatile read for happens-before consistency.
         */
        public V getValue() { return getValueVolatile(); }

        /**
         * Replaces the value corresponding to this entry with the specified value.
         * Throws UnsupportedOperationException as direct modification via entry
         * is discouraged in concurrent contexts; use map methods instead.
         *
         * @param newValue the new value to be stored in this entry
         * @return throws UnsupportedOperationException
         */
        public V setValue(V newValue) {
            // Disallow direct setting via entry to avoid bypassing map's concurrency control
            throw new UnsupportedOperationException("Direct setValue on TableEntry is not supported; use map methods.");
        }

        // --- Internal VarHandle Accessors ---

        @SuppressWarnings("unchecked") final V getValuePlain() { return (V) VALUE_HANDLE.get(this); }
        @SuppressWarnings("unchecked") final V getValueAcquire() { return (V) VALUE_HANDLE.getAcquire(this); }
        @SuppressWarnings("unchecked") final V getValueVolatile() { return (V) VALUE_HANDLE.getVolatile(this); }

        final void setValuePlain(final V value) { VALUE_HANDLE.set(this, value); }
        final void setValueRelease(final V value) { VALUE_HANDLE.setRelease(this, value); }
        final void setValueVolatile(final V value) { VALUE_HANDLE.setVolatile(this, value); }

        @SuppressWarnings("unchecked")
        final boolean compareAndSetValueVolatile(final V expect, final V update) {
            return VALUE_HANDLE.compareAndSet(this, expect, update);
        }

        @SuppressWarnings("unchecked") final TableEntry<V> getNextPlain() { return (TableEntry<V>) NEXT_HANDLE.get(this); }
        @SuppressWarnings("unchecked") final TableEntry<V> getNextVolatile() { return (TableEntry<V>) NEXT_HANDLE.getVolatile(this); }

        final void setNextPlain(final TableEntry<V> next) { NEXT_HANDLE.set(this, next); }
        final void setNextRelease(final TableEntry<V> next) { NEXT_HANDLE.setRelease(this, next); }
        final void setNextVolatile(final TableEntry<V> next) { NEXT_HANDLE.setVolatile(this, next); }

        final boolean isResizeMarker() { return this.resizeMarker; }

        // --- Standard Object Methods ---

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            // Note: This equals checks key AND value. Map.Entry contract usually requires this.
            // Be mindful if comparing only keys is needed elsewhere.
            if (o == null || !(o instanceof LeafConcurrentLong2ReferenceChainedHashTable.TableEntry)) return false;
            TableEntry<?> that = (TableEntry<?>) o;
            // Use volatile reads for value comparison for consistency
            return key == that.key && Objects.equals(getValueVolatile(), that.getValueVolatile());
        }

        @Override
        public int hashCode() {
            // Consistent with equals: hash based on key and value
            return Long.hashCode(key) ^ Objects.hashCode(getValueVolatile());
        }

        @Override
        public String toString() {
            // Use volatile read for value in string representation
            return key + "=" + getValueVolatile();
        }
    }

    /**
     * Base class for traversing nodes across the table.
     * Handles basic table traversal and advancing pointers.
     * Needs refinement to be fully robust against concurrent resizes during iteration.
     */
    protected static class NodeIterator<V> {
        final LeafConcurrentLong2ReferenceChainedHashTable<V> map; // Reference to map if needed later
        TableEntry<V>[] currentTable; // The table being iterated
        TableEntry<V> nextNode;     // The next node to return
        int nextTableIndex;         // The next bin index to check
        TableEntry<V> currentNode;  // The current node within a chain being traversed

        NodeIterator(TableEntry<V>[] table, LeafConcurrentLong2ReferenceChainedHashTable<V> map) {
            this.map = map; // Store map reference (used by derived iterators for remove)
            this.currentTable = table; // Start with the given table state
            this.nextNode = null; // No node found yet
            this.nextTableIndex = (table == null || table.length == 0) ? -1 : table.length - 1; // Start from last bin
            this.currentNode = null; // Not currently traversing a chain
            advance(); // Find the first valid node
        }

        /**
         * Advances to find the next valid (non-null value, non-marker) node.
         * Sets {@code nextNode}.
         * This implementation is simplified and might not be fully robust
         * against complex concurrent resize scenarios during iteration.
         */
        final void advance() {
            nextNode = null; // Assume no next node found initially

            // If currently traversing a chain, try the next node in that chain first
            if (currentNode != null) {
                currentNode = currentNode.getNextVolatile(); // Advance in current chain
            }

            // Loop until a valid node is found or the table is exhausted
            while (nextNode == null) {
                // If still in a chain, check the current node
                if (currentNode != null) {
                    // Check if node has a non-null value and is not a marker
                    if (!currentNode.isResizeMarker() && currentNode.getValueVolatile() != null) {
                        nextNode = currentNode; // Found a valid node
                        return; // Exit advance
                    }
                    // Invalid node, move to the next in the chain
                    currentNode = currentNode.getNextVolatile();
                    continue; // Check the next node in the chain
                }

                // If not in a chain (or chain finished), move to the next bin index
                if (nextTableIndex < 0) {
                    return; // Exhausted all bins
                }

                // Check if the current table reference is still valid
                // (A more robust iterator might need to re-read map.table here)
                if (this.currentTable != null && this.nextTableIndex < this.currentTable.length) {
                    // Get the head of the next bin to check (decrement index after read)
                    TableEntry<V> head = getAtIndexVolatile(this.currentTable, this.nextTableIndex--);
                    // Start traversing this new chain if it's valid
                    if (head != null && !head.isResizeMarker()) {
                        currentNode = head;
                        // Immediately check if this head node is valid
                        if (currentNode.getValueVolatile() != null) {
                            nextNode = currentNode;
                            return; // Found valid node
                        }
                        // Head is a placeholder, continue loop to check next in chain
                        continue;
                    }
                    // Bin was empty or head was marker/placeholder. Reset currentNode.
                    currentNode = null;
                } else {
                    // Table became null or index out of bounds (shouldn't normally happen with decrement)
                    nextTableIndex--; // Ensure progress
                    currentNode = null;
                }
            } // End while (nextNode == null)
        }

        /** Checks if there are more elements. */
        public final boolean hasNext() {
            return this.nextNode != null;
        }

        /**
         * Finds the next valid node. Internal helper.
         * Returns null if no more elements (callers should use hasNext first).
         */
        final TableEntry<V> findNext() {
            TableEntry<V> e = this.nextNode;
            if (e == null) {
                // Optional: Throw exception here if hasNext() contract is desired externally
                // throw new NoSuchElementException();
                return null; // Return null signifies end for internal use
            }
            advance(); // Find the node for the *next* call to findNext/hasNext
            return e; // Return the previously found node
        }
    }

    /**
     * Base class for iterators (Entry, Key, Value).
     * Handles remove() operation and NoSuchElementException.
     */
    protected static abstract class BaseIteratorImpl<V, T> extends NodeIterator<V> implements Iterator<T> {
        protected TableEntry<V> lastReturned; // The node returned by the last call to next()

        protected BaseIteratorImpl(final LeafConcurrentLong2ReferenceChainedHashTable<V> map) {
            // Initialize NodeIterator starting from the map's current table state
            super(map.table, map);
            this.lastReturned = null;
        }

        /**
         * Returns the next valid node, throwing NoSuchElementException if none remain.
         * Updates lastReturned for the remove() method.
         */
        protected final TableEntry<V> nextNode() throws NoSuchElementException {
            // Use the node found by the superclass's advance() mechanism (via hasNext or findNext)
            TableEntry<V> node = this.nextNode;
            if (node == null) {
                throw new NoSuchElementException();
            }
            this.lastReturned = node; // Remember this node for remove()
            advance(); // Prepare for the *next* call to next() or hasNext()
            return node; // Return the current node
        }

        /**
         * Removes the last element returned by this iterator from the underlying map.
         * This method can be called only once per call to {@code next()}.
         *
         * @throws IllegalStateException if the {@code next} method has not
         *         yet been called, or the {@code remove} method has already
         *         been called after the last call to the {@code next}
         *         method
         */
        @Override
        public void remove() {
            TableEntry<V> last = this.lastReturned;
            if (last == null) {
                throw new IllegalStateException("next() must be called before remove()");
            }
            // Remove the entry corresponding to the last returned node using the map's remove method
            this.map.remove(last.key);
            // Prevent multiple removes for the same element
            this.lastReturned = null;
        }

        /** Abstract next() method to be implemented by subclasses. */
        @Override
        public abstract T next() throws NoSuchElementException;

        /** Default forEachRemaining using hasNext/next. Subclasses can override for efficiency. */
        @Override
        public void forEachRemaining(final Consumer<? super T> action) {
            Validate.notNull(action, "Action may not be null");
            while (hasNext()) {
                action.accept(next());
            }
        }
    }

    /** Iterator over map entries (TableEntry objects). */
    protected static final class EntryIterator<V> extends BaseIteratorImpl<V, TableEntry<V>> {
        EntryIterator(final LeafConcurrentLong2ReferenceChainedHashTable<V> map) { super(map); }

        /** Returns the next entry. */
        @Override public TableEntry<V> next() throws NoSuchElementException {
            return nextNode(); // Directly return the node found by the base class
        }
    }

    /** Iterator over map keys (long primitives). */
    protected static final class KeyIterator<V> extends BaseIteratorImpl<V, Long> implements PrimitiveIterator.OfLong {
        KeyIterator(final LeafConcurrentLong2ReferenceChainedHashTable<V> map) { super(map); }

        /** Returns the next key as a long primitive. */
        @Override public long nextLong() throws NoSuchElementException {
            return nextNode().key; // Get key from the next node
        }

        /** Returns the next key as a boxed Long. */
        @Override public Long next() throws NoSuchElementException {
            return nextLong(); // Autoboxing
        }

        /** Optimized forEachRemaining for LongConsumer. */
        @Override public void forEachRemaining(final LongConsumer action) {
            Validate.notNull(action, "Action may not be null");
            while (hasNext()) {
                action.accept(nextLong());
            }
        }

        /** Overridden forEachRemaining to handle both Consumer<Long> and LongConsumer. */
        @Override public void forEachRemaining(final Consumer<? super Long> action) {
            if (action instanceof LongConsumer) {
                forEachRemaining((LongConsumer) action); // Use specialized version
            } else {
                // Fallback to default implementation (autoboxing)
                Validate.notNull(action, "Action may not be null");
                while (hasNext()) {
                    action.accept(nextLong()); // Autoboxing happens here
                }
            }
        }
    }

    /** Iterator over map values. */
    protected static final class ValueIterator<V> extends BaseIteratorImpl<V, V> {
        ValueIterator(final LeafConcurrentLong2ReferenceChainedHashTable<V> map) { super(map); }

        /** Returns the next value. */
        @Override public V next() throws NoSuchElementException {
            // Get value using volatile read from the next node
            return nextNode().getValueVolatile();
        }
    }

    // --- Collection Views ---

    /** Base class for Collection views (Values, EntrySet). */
    protected static abstract class BaseCollection<V, E> implements Collection<E> {
        protected final LeafConcurrentLong2ReferenceChainedHashTable<V> map; // Backing map

        protected BaseCollection(LeafConcurrentLong2ReferenceChainedHashTable<V> map) {
            this.map = Validate.notNull(map);
        }

        // --- Read Operations (delegated to map or implemented via iteration) ---

        @Override public int size() { return map.size(); }
        @Override public boolean isEmpty() { return map.isEmpty(); }

        /** Checks if the collection contains the object. Implemented by subclasses. */
        @Override public abstract boolean contains(Object o);

        /** Checks if the collection contains all elements from another collection. */
        @Override public boolean containsAll(Collection<?> c) {
            Validate.notNull(c);
            for (Object e : c) {
                if (!contains(e)) return false; // Subclass implements contains
            }
            return true;
        }

        /** Returns an array containing all elements in this collection. */
        @Override public Object[] toArray() {
            // Estimate size, but list grows dynamically
            List<E> list = new ArrayList<>(map.size());
            // Use iterator() provided by subclass
            for (E e : this) {
                list.add(e);
            }
            return list.toArray();
        }

        /** Returns an array containing all elements, reusing provided array if possible. */
        @Override @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a) {
            Validate.notNull(a);
            List<E> list = new ArrayList<>(map.size());
            for (E e : this) {
                list.add(e);
            }
            return list.toArray(a);
        }

        // --- Modification Operations (mostly unsupported or implemented via iterator remove) ---

        /** Clears the collection by clearing the underlying map. */
        @Override public void clear() { map.clear(); }

        /** Not supported by map views. */
        @Override public boolean add(E e) { throw new UnsupportedOperationException(); }
        /** Not supported by map views. */
        @Override public boolean addAll(Collection<? extends E> c) { throw new UnsupportedOperationException(); }

        /** Removes a single instance of the specified element using the iterator. */
        @Override public boolean remove(Object o) {
            Iterator<E> it = iterator(); // Subclass provides iterator
            while (it.hasNext()) {
                if (Objects.equals(o, it.next())) {
                    it.remove(); // Use iterator's remove for concurrency safety
                    return true;
                }
            }
            return false;
        }

        /** Removes all elements contained in the specified collection. */
        @Override public boolean removeAll(Collection<?> c) {
            Validate.notNull(c);
            boolean modified = false;
            Iterator<E> it = iterator();
            while (it.hasNext()) {
                if (c.contains(it.next())) { // Check if element should be removed
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }

        /** Retains only the elements contained in the specified collection. */
        @Override public boolean retainAll(Collection<?> c) {
            Validate.notNull(c);
            boolean modified = false;
            Iterator<E> it = iterator();
            while (it.hasNext()) {
                if (!c.contains(it.next())) { // Check if element should be removed
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }

        /** Removes all elements satisfying the given predicate. */
        @Override public boolean removeIf(Predicate<? super E> filter) {
            Validate.notNull(filter);
            boolean removed = false;
            Iterator<E> it = iterator();
            while (it.hasNext()) {
                if (filter.test(it.next())) {
                    it.remove();
                    removed = true;
                }
            }
            return removed;
        }

        // --- Utility ---

        @Override public String toString() {
            Iterator<E> it = iterator();
            if (! it.hasNext()) return "[]";
            StringBuilder sb = new StringBuilder("[");
            for (;;) {
                E e = it.next();
                sb.append(e == this ? "(this Collection)" : e); // Prevent self-reference issues
                if (! it.hasNext()) return sb.append(']').toString();
                sb.append(',').append(' ');
            }
        }

        /** Default forEach using iterator. */
        @Override public void forEach(Consumer<? super E> action) {
            Validate.notNull(action);
            for (E e : this) {
                action.accept(e);
            }
        }
    }

    /** Collection view for the map's values. */
    protected static final class Values<V> extends BaseCollection<V, V> {
        Values(LeafConcurrentLong2ReferenceChainedHashTable<V> map) { super(map); }

        /** Checks if the map contains the specified value. */
        @Override public boolean contains(Object o) {
            // Delegate to map's containsValue (requires value type V)
            try {
                return o != null && map.containsValue((V)o);
            } catch (ClassCastException cce) {
                return false; // Object is not of type V
            }
        }

        /** Returns an iterator over the values. */
        @Override public Iterator<V> iterator() { return map.valueIterator(); }

        // Inherits remove, removeAll, retainAll, clear, etc. from BaseCollection
        // which use the valueIterator's remove method.
    }

    /** Set view for the map's entries (TableEntry objects). */
    protected static final class EntrySet<V> extends BaseCollection<V, TableEntry<V>> implements Set<TableEntry<V>> {
        EntrySet(LeafConcurrentLong2ReferenceChainedHashTable<V> map) { super(map); }

        /** Checks if the map contains the specified entry. */
        @Override public boolean contains(Object o) {
            if (!(o instanceof LeafConcurrentLong2ReferenceChainedHashTable.TableEntry<?>)) return false;
            TableEntry<?> entry = (TableEntry<?>) o;
            // Check if the map contains the key and maps it to the entry's value
            V mappedValue = map.get(entry.getKey()); // Use map.get for concurrent read
            // Use volatile read on entry's value for consistent comparison
            return mappedValue != null && Objects.equals(mappedValue, entry.getValueVolatile());
        }

        /** Returns an iterator over the entries. */
        @Override public Iterator<TableEntry<V>> iterator() { return map.entryIterator(); }

        /**
         * Removes the specified entry from the map if it is present.
         * Uses the map's remove(key, value) for atomic removal.
         */
        @Override public boolean remove(Object o) {
            if (!(o instanceof LeafConcurrentLong2ReferenceChainedHashTable.TableEntry<?>)) return false;
            TableEntry<?> entry = (TableEntry<?>) o;
            try {
                // Use map's atomic remove(key, value)
                return map.remove(entry.getKey(), (V)entry.getValueVolatile());
            } catch(ClassCastException cce) {
                return false; // Value type mismatch
            }
        }

        @Override public int hashCode() {
            int h = 0;
            for (TableEntry<V> e : this) {
                h += e.hashCode();
            }
            return h;
        }

        @Override public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof Set)) return false;
            Set<?> c = (Set<?>) o;
            if (c.size() != size()) return false;
            try {
                return containsAll(c);
            } catch (ClassCastException | NullPointerException unused) {
                return false;
            }
        }
    }
}
