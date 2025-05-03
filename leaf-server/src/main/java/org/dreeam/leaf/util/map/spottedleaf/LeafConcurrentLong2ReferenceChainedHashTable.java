package org.dreeam.leaf.util.map.spottedleaf;

import ca.spottedleaf.concurrentutil.function.BiLong1Function;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.HashUtil;
import ca.spottedleaf.concurrentutil.util.IntegerUtil;
import ca.spottedleaf.concurrentutil.util.ThrowUtil;
import ca.spottedleaf.concurrentutil.util.Validate;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;
import java.util.function.Consumer;
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
    /**
     * The maximum capacity, used if a higher value is implicitly specified by either
     * of the constructors with arguments. MUST be a power of two <= 1<<30.
     */
    protected static final int MAXIMUM_CAPACITY = 1 << 30; // 2^30

    protected static final int THRESHOLD_NO_RESIZE = -1; // Sentinel value: table cannot be resized
    protected static final int THRESHOLD_RESIZING = -2; // Sentinel value: table is currently resizing

    // --- Instance Fields ---

    /**
     * Tracks the number of mappings, using LongAdder for better high-contention performance.
     */
    protected final LongAdder size = new LongAdder();

    /**
     * The load factor for the hash table.
     */
    protected final float loadFactor;

    /**
     * The hash table array. Elements are accessed using VarHandles.
     */
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
     * @param capacity   The initial capacity.
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
     * @param expected   The expected number of elements.
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

    /**
     * Calculates the target resize threshold.
     */
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


    /**
     * Calculates the power-of-two capacity for a given initial capacity request.
     */
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

    /**
     * Computes the hash code for the key. Uses mixing to spread keys more evenly.
     */
    protected static int getHash(final long key) {
        return (int) HashUtil.mix(key); // Assumes HashUtil.mix provides good distribution
    }

    /**
     * Returns the load factor associated with this map.
     */
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
    protected final TableEntry<V> getNode(final long key) {
        final int hash = getHash(key);
        TableEntry<V>[] currentTable = this.table; // Volatile read

        outer_loop:
        for (;;) { // Loop handles table resizes detected during traversal
            final int tableLength = currentTable.length;
            if (tableLength == 0) {
                // Table might not be initialized yet (race in constructor?), re-read.
                currentTable = this.table;
                if (currentTable.length == 0) {
                    // Still not initialized? Should not happen normally. Return null safely.
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
            // Reduces chain traversal for head hits
            if (head.key == key) {
                return head;
            }

            // Traverse the linked list (chain) in the bin
            // Volatile read is necessary here to observe concurrent modifications (removals/resizes)
            TableEntry<V> node = head.getNextVolatile();
            while (node != null) {
                if (node.key == key) {
                    return node; // Key found
                }
                node = node.getNextVolatile(); // Move to the next node using volatile read
            }

            // Key not found in the chain.
            // Crucial check: Re-read table reference to see if a resize occurred *during* traversal.
            TableEntry<V>[] latestTable = this.table; // Volatile read
            if (currentTable != latestTable) {
                // Table reference changed, a resize happened. Retry the whole lookup.
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
        V markerValue = resizeMarker.getValuePlain(); // Plain read is safe, marker itself is effectively final
        if (markerValue instanceof TableEntry<?>[]) {
            // Consider adding active resizing help here in a contended scenario
            return (TableEntry<V>[]) markerValue;
        }
        // Fallback: Should not happen if markers are correct. Force retry by re-reading table.
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
     * @param key          the key whose associated value is to be returned
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
        NodeIterator<V> iterator = new NodeIterator<>(this.table, this);
        TableEntry<V> node;
        while ((node = iterator.findNext()) != null) { // findNext safely iterates through nodes
            V nodeValue = node.getValueVolatile(); // Volatile read for visibility
            if (value.equals(nodeValue)) {
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
        int currentThreshold;
        do {
            currentThreshold = this.getThresholdAcquire(); // Acquire fence for reading threshold
            if (currentThreshold <= 0) return; // THRESHOLD_NO_RESIZE or THRESHOLD_RESIZING

            final long currentSum = this.size.sum(); // Get current estimated size
            if (currentSum < (long) currentThreshold) {
                // Double check threshold hasn't changed due to another thread finishing resize
                if (currentThreshold == this.getThresholdVolatile()) return;
                continue; // Threshold changed, retry the loop
            }

            // Size exceeds threshold, attempt to initiate resize
            if (this.compareExchangeThresholdVolatile(currentThreshold, THRESHOLD_RESIZING) == currentThreshold) {
                this.resize(currentSum); // Pass estimated size
                return; // Resize initiated or completed
            }
            // CAS failed, another thread initiated resize. Loop might retry.
        } while (true);
    }

    /**
     * Decrements the size count.
     */
    protected final void subSize(final long count) {
        this.size.add(-count);
        // Note: No resize check needed on removal
    }

    /**
     * Resizes the table to accommodate more entries. Called by the thread
     * that successfully sets the threshold to THRESHOLD_RESIZING.
     */
    @SuppressWarnings("unchecked")
    private void resize(final long estimatedSize) { // estimatedSize might not be perfectly accurate
        final TableEntry<V>[] oldTable = this.table; // Volatile read
        final int oldCapacity = oldTable.length;

        if (oldCapacity >= MAXIMUM_CAPACITY) {
            this.setThresholdVolatile(THRESHOLD_NO_RESIZE);
            return;
        }

        int newCapacity = oldCapacity << 1; // Double the capacity
        if (newCapacity <= oldCapacity || newCapacity > MAXIMUM_CAPACITY) { // Handle overflow or max
            newCapacity = MAXIMUM_CAPACITY;
        }
        if (newCapacity == oldCapacity) { // Already maxed out
            this.setThresholdVolatile(THRESHOLD_NO_RESIZE);
            return;
        }

        final int newThreshold = getTargetThreshold(newCapacity, this.loadFactor);
        final TableEntry<V>[] newTable = (TableEntry<V>[]) new TableEntry[newCapacity];
        final TableEntry<V> resizeMarker = new TableEntry<>(0L, (V) newTable, true); // Key irrelevant for marker

        for (int i = 0; i < oldCapacity; ++i) {
            TableEntry<V> head = getAtIndexVolatile(oldTable, i);

            if (head == null) {
                // Try to CAS marker into empty bin
                if (compareAndExchangeAtIndexVolatile(oldTable, i, null, resizeMarker) == null) {
                    continue; // Marked empty bin
                }
                // CAS failed, re-read
                head = getAtIndexVolatile(oldTable, i);
                if (head == null || head.isResizeMarker()) continue; // Still null or handled
            }

            if (head.isResizeMarker()) {
                continue; // Already processed
            }

            // Bin has entries, lock head to transfer chain
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(oldTable, i);
                // Re-check after lock
                if (currentHead != head) {
                    i--; // Reprocess index 'i' if head changed while waiting
                    continue;
                }
                if (head.isResizeMarker()) {
                    continue; // Marked while waiting
                }

                // Split chain: index 'i' vs 'i + oldCapacity'
                TableEntry<V> lowH = null, lowT = null;
                TableEntry<V> highH = null, highT = null;

                TableEntry<V> current = head;
                while (current != null) {
                    TableEntry<V> next = current.getNextPlain(); // Plain read inside lock
                    int hash = getHash(current.key);

                    if ((hash & oldCapacity) == 0) { // Low bin (index i)
                        if (lowT == null) lowH = current;
                        else lowT.setNextPlain(current);
                        lowT = current;
                    } else { // High bin (index i + oldCapacity)
                        if (highT == null) highH = current;
                        else highT.setNextPlain(current);
                        highT = current;
                    }
                    current = next;
                }

                if (lowT != null) lowT.setNextPlain(null);
                if (highT != null) highT.setNextPlain(null);

                // Place chains into new table (volatile writes)
                setAtIndexVolatile(newTable, i, lowH);
                setAtIndexVolatile(newTable, i + oldCapacity, highH);

                // Mark old bin as processed (release write)
                setAtIndexRelease(oldTable, i, resizeMarker);
            } // End synchronized
        } // End loop over old table bins

        // Finalize: publish new table and threshold
        this.table = newTable;
        this.setThresholdVolatile(newThreshold);
    }


    /**
     * Maps the specified key to the specified value in this table.
     * Neither the key nor the value can be null.
     *
     * @param key   key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with {@code key}, or
     * {@code null} if there was no mapping for {@code key}.
     * @throws NullPointerException if the specified value is null
     */
    public V put(final long key, final V value) {
        Validate.notNull(value, "Value may not be null");
        final int hash = getHash(key);
        int sizeDelta;
        V oldValue;
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for (;;) {
            final int tableLength = currentTable.length;
            // Init check
            if (tableLength == 0) {
                currentTable = this.table;
                if (currentTable.length == 0) continue;
            }

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            // Case 1: Bin is empty
            if (head == null) {
                TableEntry<V> newNode = new TableEntry<>(key, value);
                if (compareAndExchangeAtIndexVolatile(currentTable, index, null, newNode) == null) {
                    this.addSize(1L);
                    return null; // Inserted successfully
                }
                continue table_loop; // CAS failed, retry
            }

            // Case 2: Resize marker
            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop;
            }

            // Case 3: Optimistic lock-free update attempt
            TableEntry<V> node = head;
            while (node != null) {
                if (node.key == key) {
                    V currentVal = node.getValueVolatile(); // Volatile read
                    if (currentVal == null) break; // Placeholder requires lock
                    // Try atomic update
                    if (node.compareAndSetValueVolatile(currentVal, value)) {
                        return currentVal; // Lock-free success
                    }
                    break; // CAS failed, need lock
                }
                node = node.getNextVolatile(); // Volatile read
            }

            // Case 4: Locking path
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                // Re-check state after lock
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop;
                }

                // Traverse again within lock
                TableEntry<V> prev = null;
                node = head;
                while (node != null) {
                    if (node.key == key) {
                        oldValue = node.getValuePlain(); // Plain read in lock
                        node.setValueVolatile(value);    // Volatile write for visibility
                        sizeDelta = (oldValue == null) ? 1 : 0; // Adjust size if replacing placeholder
                        break table_loop; // Update done
                    }
                    prev = node;
                    node = node.getNextPlain(); // Plain read in lock
                }

                // Key not found, add new node to end of chain
                if (prev != null) {
                    TableEntry<V> newNode = new TableEntry<>(key, value);
                    prev.setNextRelease(newNode); // Release write to link safely
                    sizeDelta = 1;
                    oldValue = null;
                } else {
                    continue table_loop; // Should not happen if head was non-null/non-marker. Retry.
                }
            } // End synchronized
            break table_loop; // Operation completed within lock
        } // End table_loop

        if (sizeDelta != 0) {
            this.addSize(sizeDelta);
        }
        return oldValue;
    }


    /**
     * If the specified key is not already associated with a value, associates
     * it with the given value.
     *
     * @param key   key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with the specified key, or
     * {@code null} if there was no mapping for the key.
     * @throws NullPointerException if the specified value is null
     */
    public V putIfAbsent(final long key, final V value) {
        Validate.notNull(value, "Value may not be null");
        final int hash = getHash(key);
        int sizeDelta = 0;
        V existingValue;
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for (;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) {
                currentTable = this.table;
                continue;
            }

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            // Case 1: Bin is empty
            if (head == null) {
                TableEntry<V> newNode = new TableEntry<>(key, value);
                if (compareAndExchangeAtIndexVolatile(currentTable, index, null, newNode) == null) {
                    this.addSize(1L);
                    return null; // Inserted
                }
                continue table_loop; // CAS failed, retry
            }

            // Case 2: Resize marker
            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop;
            }

            // Case 3: Lock-free check (optimistic)
            TableEntry<V> node = head;
            while (node != null) {
                if (node.key == key) {
                    existingValue = node.getValueVolatile(); // Volatile read
                    if (existingValue != null) {
                        return existingValue; // Key present with value
                    }
                    // Placeholder found, need lock
                    break;
                }
                node = node.getNextVolatile();
            }


            // Case 4: Locking path
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // State changed, retry
                }

                TableEntry<V> prev = null;
                node = head;
                while (node != null) {
                    if (node.key == key) {
                        existingValue = node.getValuePlain(); // Plain read in lock
                        if (existingValue != null) {
                            break table_loop; // Return existing value
                        } else {
                            // Placeholder: update it
                            node.setValueVolatile(value); // Volatile write
                            sizeDelta = 1;
                            existingValue = null; // Return null as per contract
                            break table_loop;
                        }
                    }
                    prev = node;
                    node = node.getNextPlain(); // Plain read in lock
                }

                // Key not found, add new node
                if (prev != null) {
                    TableEntry<V> newNode = new TableEntry<>(key, value);
                    prev.setNextRelease(newNode); // Release write
                    sizeDelta = 1;
                    existingValue = null;
                } else {
                    continue table_loop; // Should not happen
                }
            } // End synchronized
            break table_loop;
        } // End table_loop

        if (sizeDelta != 0) {
            this.addSize(sizeDelta);
        }
        return existingValue;
    }

    /**
     * Replaces the entry for a key only if currently mapped to some value.
     *
     * @param key   key with which the specified value is associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with the specified key, or
     * {@code null} if there was no mapping for the key.
     * @throws NullPointerException if the specified value is null
     */
    public V replace(final long key, final V value) {
        Validate.notNull(value, "Value may not be null");
        final int hash = getHash(key);
        V oldValue;
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for (;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) return null;

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            if (head == null) return null;

            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop;
            }

            // Try Lock-Free Replace Attempt
            TableEntry<V> node = head;
            while (node != null) {
                if (node.key == key) {
                    do { // CAS retry loop
                        oldValue = node.getValueVolatile(); // Volatile read
                        if (oldValue == null) return null; // Cannot replace placeholder

                        if (node.compareAndSetValueVolatile(oldValue, value)) {
                            return oldValue; // Lock-free success
                        }
                        // CAS failed, retry if key still matches
                    } while (node.key == key);
                    // Key changed or CAS keeps failing, fall back to lock
                    break;
                }
                node = node.getNextVolatile();
            }

            // Locking Path
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // Retry
                }
                node = head;
                while (node != null) {
                    if (node.key == key) {
                        oldValue = node.getValuePlain(); // Plain read in lock
                        if (oldValue != null) {
                            node.setValueVolatile(value); // Volatile write
                            return oldValue;
                        } else {
                            return null; // Cannot replace placeholder
                        }
                    }
                    node = node.getNextPlain(); // Plain read in lock
                }
            } // End synchronized

            // Key not found after checks
            return null;
        } // End table_loop
    }

    /**
     * Replaces the entry for a key only if currently mapped to a given value.
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
        for (;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) return false;

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            if (head == null) return false;

            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop;
            }

            // Lock-Free CAS Attempt
            TableEntry<V> node = head;
            while (node != null) {
                if (node.key == key) {
                    V currentVal = node.getValueVolatile(); // Volatile read
                    if (!Objects.equals(currentVal, expect)) {
                        return false; // Value doesn't match
                    }
                    // Value matches, try CAS
                    if (node.compareAndSetValueVolatile(expect, update)) {
                        return true; // Lock-free success
                    }
                    // CAS failed, need lock
                    break;
                }
                node = node.getNextVolatile();
            }

            // Locking Path
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // Retry
                }
                node = head;
                while (node != null) {
                    if (node.key == key) {
                        V currentVal = node.getValuePlain(); // Plain read in lock
                        if (Objects.equals(currentVal, expect)) {
                            node.setValueVolatile(update); // Volatile write
                            return true; // Replaced successfully
                        } else {
                            return false; // Value doesn't match
                        }
                    }
                    node = node.getNextPlain(); // Plain read in lock
                }
            } // End synchronized

            // Key not found
            return false;
        } // End table_loop
    }

    /**
     * Removes the mapping for a key from this map if it is present.
     *
     * @param key key whose mapping is to be removed from the map
     * @return the previous value associated with {@code key}, or
     * {@code null} if there was no mapping for {@code key}
     */
    public V remove(final long key) {
        final int hash = getHash(key);
        int sizeDelta = 0;
        V oldValue = null;
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for (;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) return null;

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            if (head == null) return null;

            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop;
            }

            // Removal needs locking
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // Retry
                }

                TableEntry<V> prev = null;
                TableEntry<V> node = head;
                while (node != null) {
                    if (node.key == key) {
                        oldValue = node.getValuePlain(); // Plain read in lock
                        sizeDelta = (oldValue != null) ? -1 : 0; // Decrement if actual mapping

                        TableEntry<V> next = node.getNextPlain(); // Plain read
                        // Update links with release semantics
                        if (prev == null) {
                            setAtIndexRelease(currentTable, index, next); // Removed head
                        } else {
                            prev.setNextRelease(next); // Removed middle/end
                        }
                        break table_loop; // Removed, exit loop
                    }
                    prev = node;
                    node = node.getNextPlain(); // Plain read
                }
                // Key not found in chain within lock
                break table_loop;
            } // End synchronized
        } // End table_loop

        if (sizeDelta != 0) {
            this.subSize(-sizeDelta); // subSize takes positive count
        }
        return oldValue;
    }


    /**
     * Removes the entry for a key only if currently mapped to a given value.
     *
     * @param key    key with which the specified value is associated
     * @param expect value expected to be associated with the specified key
     * @return {@code true} if the value was removed
     */
    public boolean remove(final long key, final V expect) {
        final int hash = getHash(key);
        int sizeDelta = 0;
        boolean removed = false;
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for (;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) return false;

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            if (head == null) return false;

            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop;
            }

            // Removal needs locking
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // Retry
                }

                TableEntry<V> prev = null;
                TableEntry<V> node = head;
                while (node != null) {
                    if (node.key == key) {
                        V currentVal = node.getValuePlain(); // Plain read in lock
                        if (Objects.equals(currentVal, expect)) { // Safe comparison
                            removed = true;
                            sizeDelta = (currentVal != null) ? -1 : 0; // Decrement if actual value

                            TableEntry<V> next = node.getNextPlain(); // Plain read
                            // Update links with release semantics
                            if (prev == null) {
                                setAtIndexRelease(currentTable, index, next);
                            } else {
                                prev.setNextRelease(next);
                            }
                        } else {
                            removed = false; // Value didn't match
                        }
                        break table_loop; // Key processed
                    }
                    prev = node;
                    node = node.getNextPlain(); // Plain read
                }
                // Key not found in chain within lock
                break table_loop;
            } // End synchronized
        } // End table_loop

        if (sizeDelta != 0) {
            this.subSize(-sizeDelta);
        }
        return removed;
    }

    /**
     * Removes the entry for the specified key only if its value satisfies the given predicate.
     *
     * @param key       key whose mapping is to be removed from the map
     * @param predicate the predicate to apply to the value associated with the key
     * @return the value associated with the key before removal if the predicate was satisfied and the entry was removed,
     * otherwise {@code null}.
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
        for (;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) return null;

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            if (head == null) return null;

            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop;
            }

            // Conditional removal needs locking
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // Retry
                }

                TableEntry<V> prev = null;
                TableEntry<V> node = head;
                while (node != null) {
                    if (node.key == key) {
                        oldValue = node.getValuePlain(); // Plain read in lock
                        if (oldValue != null && predicate.test(oldValue)) { // Test non-null value
                            removed = true;
                            sizeDelta = -1;

                            TableEntry<V> next = node.getNextPlain(); // Plain read
                            // Update links with release semantics
                            if (prev == null) {
                                setAtIndexRelease(currentTable, index, next);
                            } else {
                                prev.setNextRelease(next);
                            }
                        } else {
                            removed = false; // Predicate failed or value null
                        }
                        break table_loop; // Key processed
                    }
                    prev = node;
                    node = node.getNextPlain(); // Plain read
                }
                // Key not found in chain within lock
                break table_loop;
            } // End synchronized
        } // End table_loop

        if (sizeDelta != 0) {
            this.subSize(-sizeDelta);
        }
        return removed ? oldValue : null; // Return old value only if removed
    }

    // --- Compute Methods ---

    /**
     * Attempts to compute a mapping for the specified key and its current mapped value
     * (or {@code null} if there is no current mapping). The function is
     * applied atomically.
     *
     * @param key      key with which the specified value is to be associated
     * @param function the function to compute a value
     * @return the new value associated with the specified key, or null if none
     * @throws NullPointerException if the specified function is null
     */
    public V compute(final long key, final BiLong1Function<? super V, ? extends V> function) {
        Validate.notNull(function, "Function cannot be null");
        final int hash = getHash(key);
        int sizeDelta = 0;
        V finalValue;
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for (;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) {
                currentTable = this.table;
                continue;
            }

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            // Case 1: Bin is empty. Use placeholder logic.
            if (head == null) {
                TableEntry<V> placeholder = new TableEntry<>(key, null); // Temp node
                V computedValue;
                synchronized (placeholder) { // Lock placeholder for atomicity
                    if (getAtIndexVolatile(currentTable, index) == null) { // Re-check bin
                        try {
                            computedValue = function.apply(key, null); // Compute with null old value
                        } catch (Throwable t) {
                            ThrowUtil.throwUnchecked(t);
                            return null;
                        }

                        if (computedValue != null) {
                            placeholder.setValuePlain(computedValue); // Set value before CAS
                            // Attempt to insert the computed node
                            if (compareAndExchangeAtIndexVolatile(currentTable, index, null, placeholder) == null) {
                                sizeDelta = 1;
                                finalValue = computedValue;
                                break table_loop; // Success
                            } else {
                                continue table_loop; // CAS failed, retry
                            }
                        } else {
                            finalValue = null; // Computed null, no mapping
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

            // Case 3: Bin not empty. Lock head.
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // Retry
                }

                TableEntry<V> prev = null;
                TableEntry<V> node = head;
                while (node != null) {
                    if (node.key == key) {
                        // Key found. Compute with existing value.
                        V oldValue = node.getValuePlain(); // Plain read in lock
                        V computedValue;
                        try {
                            computedValue = function.apply(key, oldValue);
                        } catch (Throwable t) {
                            ThrowUtil.throwUnchecked(t);
                            return null;
                        }

                        if (computedValue != null) {
                            node.setValueVolatile(computedValue); // Update value (volatile write)
                            finalValue = computedValue;
                            sizeDelta = (oldValue == null) ? 1 : 0; // Size change if old was placeholder
                        } else {
                            // Remove mapping
                            finalValue = null;
                            sizeDelta = (oldValue != null) ? -1 : 0; // Size change only if old was value
                            TableEntry<V> next = node.getNextPlain(); // Plain read
                            if (prev == null) setAtIndexRelease(currentTable, index, next);
                            else prev.setNextRelease(next);
                        }
                        break table_loop; // Done
                    }
                    prev = node;
                    node = node.getNextPlain(); // Plain read
                } // End while

                // Key not found. Compute with null.
                V computedValue;
                try {
                    computedValue = function.apply(key, null);
                } catch (Throwable t) {
                    ThrowUtil.throwUnchecked(t);
                    return null;
                }

                if (computedValue != null) {
                    // Add new mapping
                    finalValue = computedValue;
                    sizeDelta = 1;
                    TableEntry<V> newNode = new TableEntry<>(key, computedValue);
                    if (prev != null) prev.setNextRelease(newNode); // Release write
                    else continue table_loop; // Should not happen
                } else {
                    finalValue = null;
                    sizeDelta = 0;
                }
                break table_loop; // Done
            } // End synchronized(head)
        } // End table_loop

        if (sizeDelta > 0) this.addSize(sizeDelta);
        else if (sizeDelta < 0) this.subSize(-sizeDelta);

        return finalValue;
    }

    /**
     * If the specified key is not already associated with a value, attempts to
     * compute its value using the given mapping function and enters it into
     * this map unless {@code null}.
     *
     * @param key      key with which the specified value is to be associated
     * @param function the function to compute a value
     * @return the current (existing or computed) value associated with the specified key,
     * or null if the computed value is null
     * @throws NullPointerException if the specified function is null
     */
    public V computeIfAbsent(final long key, final LongFunction<? extends V> function) {
        Validate.notNull(function, "Function cannot be null");
        final int hash = getHash(key);
        int sizeDelta = 0;
        V finalValue;
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for (;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) {
                currentTable = this.table;
                continue;
            }

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            // Case 1: Bin is empty. Use placeholder.
            if (head == null) {
                TableEntry<V> placeholder = new TableEntry<>(key, null);
                V computedValue;
                synchronized (placeholder) {
                    if (getAtIndexVolatile(currentTable, index) == null) {
                        try {
                            computedValue = function.apply(key);
                        } catch (Throwable t) {
                            ThrowUtil.throwUnchecked(t);
                            return null;
                        }

                        if (computedValue != null) {
                            placeholder.setValuePlain(computedValue);
                            if (compareAndExchangeAtIndexVolatile(currentTable, index, null, placeholder) == null) {
                                sizeDelta = 1;
                                finalValue = computedValue;
                                break table_loop; // Inserted
                            } else {
                                continue table_loop; // CAS failed, retry
                            }
                        } else {
                            finalValue = null; // Computed null
                            break table_loop;
                        }
                    }
                } // End synchronized(placeholder)
                continue table_loop; // Bin changed, retry
            } // End Case 1

            // Case 2: Resize marker
            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop;
            }

            // Case 3: Lock-free check if key already exists with value
            TableEntry<V> node = head;
            while (node != null) {
                if (node.key == key) {
                    V existingValue = node.getValueVolatile(); // Volatile read
                    if (existingValue != null) {
                        return existingValue; // Already present
                    }
                    break; // Placeholder found, need lock
                }
                node = node.getNextVolatile();
            }

            // Case 4: Locking path
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // Retry
                }

                TableEntry<V> prev = null;
                node = head;
                while (node != null) {
                    if (node.key == key) {
                        V existingValue = node.getValuePlain(); // Plain read in lock
                        if (existingValue != null) {
                            finalValue = existingValue; // Found inside lock
                        } else {
                            // Placeholder exists, compute and update
                            V computedValue;
                            try {
                                computedValue = function.apply(key);
                            } catch (Throwable t) {
                                ThrowUtil.throwUnchecked(t);
                                return null;
                            }

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
                } // End while

                // Key not found. Compute and add.
                V computedValue;
                try {
                    computedValue = function.apply(key);
                } catch (Throwable t) {
                    ThrowUtil.throwUnchecked(t);
                    return null;
                }

                if (computedValue != null) {
                    finalValue = computedValue;
                    sizeDelta = 1;
                    TableEntry<V> newNode = new TableEntry<>(key, computedValue);
                    if (prev != null) prev.setNextRelease(newNode); // Release write
                    else continue table_loop; // Should not happen
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
     * mapping given the key and its current mapped value.
     *
     * @param key      key with which the specified value is to be associated
     * @param function the function to compute a value
     * @return the new value associated with the specified key, or null if none
     * @throws NullPointerException if the specified function is null
     */
    public V computeIfPresent(final long key, final BiLong1Function<? super V, ? extends V> function) {
        Validate.notNull(function, "Function cannot be null");
        final int hash = getHash(key);
        int sizeDelta;
        V finalValue;
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for (;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) return null;

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            if (head == null) return null;

            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop;
            }

            // Needs lock for potential removal
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // Retry
                }

                TableEntry<V> prev = null;
                TableEntry<V> node = head;
                while (node != null) {
                    if (node.key == key) {
                        V oldValue = node.getValuePlain(); // Plain read in lock
                        if (oldValue != null) { // Only compute if value present
                            V computedValue;
                            try {
                                computedValue = function.apply(key, oldValue);
                            } catch (Throwable t) {
                                ThrowUtil.throwUnchecked(t);
                                return null;
                            }

                            if (computedValue != null) {
                                node.setValueVolatile(computedValue); // Update (volatile write)
                                finalValue = computedValue;
                                sizeDelta = 0;
                            } else {
                                // Remove mapping
                                finalValue = null;
                                sizeDelta = -1;
                                TableEntry<V> next = node.getNextPlain(); // Plain read
                                if (prev == null) setAtIndexRelease(currentTable, index, next);
                                else prev.setNextRelease(next);
                            }
                        } else {
                            // Placeholder, treat as absent
                            finalValue = null;
                            sizeDelta = 0;
                        }
                        break table_loop; // Done
                    }
                    prev = node;
                    node = node.getNextPlain(); // Plain read
                } // End while

                // Key not found
                finalValue = null;
                sizeDelta = 0;
                break table_loop;
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
     *
     * @param key      key with which the resulting value is to be associated
     * @param value    the non-null value to be merged with the existing value
     * @param function the function to recompute a value if present
     * @return the new value associated with the specified key, or null if no
     * value is associated with the key
     * @throws NullPointerException if the specified value or function is null
     */
    public V merge(final long key, final V value, final BiFunction<? super V, ? super V, ? extends V> function) {
        Validate.notNull(value, "Value cannot be null");
        Validate.notNull(function, "Function cannot be null");
        final int hash = getHash(key);
        int sizeDelta;
        V finalValue;
        TableEntry<V>[] currentTable = this.table;

        table_loop:
        for (;;) {
            final int tableLength = currentTable.length;
            if (tableLength == 0) {
                currentTable = this.table;
                continue;
            }

            final int index = hash & (tableLength - 1);
            TableEntry<V> head = getAtIndexVolatile(currentTable, index);

            // Case 1: Bin empty. Insert value.
            if (head == null) {
                TableEntry<V> newNode = new TableEntry<>(key, value);
                if (compareAndExchangeAtIndexVolatile(currentTable, index, null, newNode) == null) {
                    sizeDelta = 1;
                    finalValue = value;
                    break table_loop; // Inserted
                }
                continue table_loop; // CAS failed, retry
            }

            // Case 2: Resize marker
            if (head.isResizeMarker()) {
                currentTable = helpResizeOrGetNextTable(currentTable, head);
                continue table_loop;
            }

            // Case 3: Lock head
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, index);
                if (currentHead != head || head.isResizeMarker()) {
                    continue table_loop; // Retry
                }

                TableEntry<V> prev = null;
                TableEntry<V> node = head;
                while (node != null) {
                    if (node.key == key) {
                        // Key found. Merge.
                        V oldValue = node.getValuePlain(); // Plain read in lock
                        V computedValue;
                        if (oldValue != null) {
                            try {
                                computedValue = function.apply(oldValue, value); // Apply function
                            } catch (Throwable t) {
                                ThrowUtil.throwUnchecked(t);
                                return null;
                            }
                        } else {
                            computedValue = value; // Use provided value if old was placeholder
                        }

                        if (computedValue != null) {
                            node.setValueVolatile(computedValue); // Update (volatile write)
                            finalValue = computedValue;
                            sizeDelta = (oldValue == null) ? 1 : 0; // Size change if old was placeholder
                        } else {
                            // Remove mapping
                            finalValue = null;
                            sizeDelta = (oldValue != null) ? -1 : 0; // Size change if old was value
                            TableEntry<V> next = node.getNextPlain(); // Plain read
                            if (prev == null) setAtIndexRelease(currentTable, index, next);
                            else prev.setNextRelease(next);
                        }
                        break table_loop; // Done
                    }
                    prev = node;
                    node = node.getNextPlain(); // Plain read
                } // End while

                // Key not found. Add provided value.
                finalValue = value;
                sizeDelta = 1;
                TableEntry<V> newNode = new TableEntry<>(key, value);
                if (prev != null) prev.setNextRelease(newNode); // Release write
                else continue table_loop; // Should not happen
                break table_loop; // Done
            } // End synchronized(head)
        } // End table_loop

        if (sizeDelta > 0) this.addSize(sizeDelta);
        else if (sizeDelta < 0) this.subSize(-sizeDelta);

        return finalValue;
    }


    /**
     * Removes all of the mappings from this map.
     * The map will be empty after this call returns.
     */
    public void clear() {
        long removedCount = 0L;
        TableEntry<V>[] currentTable = this.table; // Volatile read

        for (int i = 0; i < currentTable.length; ++i) {
            TableEntry<V> head = getAtIndexVolatile(currentTable, i);

            if (head == null || head.isResizeMarker()) continue;

            // Lock bin to clear
            synchronized (head) {
                TableEntry<V> currentHead = getAtIndexVolatile(currentTable, i);
                // Re-check after lock
                if (currentHead != head || head.isResizeMarker()) {
                    continue; // Bin changed, skip
                }

                // Count actual mappings and clear bin
                TableEntry<V> node = head;
                while (node != null) {
                    if (node.getValuePlain() != null) { // Count non-placeholders
                        removedCount++;
                    }
                    node = node.getNextPlain(); // Plain read in lock
                }
                // Clear bin head with release semantics
                setAtIndexRelease(currentTable, i, null);
            } // End synchronized
        } // End loop

        if (removedCount > 0) {
            this.subSize(removedCount);
        }
    }

    // --- Iterators and Views ---

    /**
     * Returns an iterator over the map entries.
     */
    public Iterator<TableEntry<V>> entryIterator() {
        return new EntryIterator<>(this);
    }

    /**
     * Returns an iterator over the map entries (implements Iterable).
     */
    @Override
    public final Iterator<TableEntry<V>> iterator() {
        return this.entryIterator();
    }

    /**
     * Returns an iterator over the keys.
     */
    public PrimitiveIterator.OfLong keyIterator() {
        return new KeyIterator<>(this);
    }

    /**
     * Returns an iterator over the values.
     */
    public Iterator<V> valueIterator() {
        return new ValueIterator<>(this);
    }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     */
    public Collection<V> values() {
        Values<V> v = this.values;
        return (v != null) ? v : (this.values = new Values<>(this));
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map.
     */
    public Set<TableEntry<V>> entrySet() {
        EntrySet<V> es = this.entrySet;
        return (es != null) ? es : (this.entrySet = new EntrySet<>(this));
    }

    // --- Inner Classes: TableEntry, Iterators, Views ---

    /**
     * Represents a key-value mapping entry in the hash table.
     * Also used as a resize marker.
     */
    public static final class TableEntry<V> {
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

        final long key;
        private volatile V value;
        private volatile TableEntry<V> next;
        private final boolean resizeMarker;

        /**
         * Constructor for regular map entries.
         */
        TableEntry(final long key, final V value) {
            this(key, value, false);
        }

        /**
         * Constructor for potentially creating resize markers.
         */
        TableEntry(final long key, final V value, final boolean resize) {
            this.key = key;
            this.resizeMarker = resize;
            this.setValuePlain(value); // Initial plain set
        }

        public long getKey() {
            return this.key;
        }

        public V getValue() {
            return getValueVolatile();
        }

        public V setValue(V newValue) {
            throw new UnsupportedOperationException("Direct setValue on TableEntry is not supported; use map methods.");
        }

        @SuppressWarnings("unchecked")
        V getValuePlain() {
            return (V) VALUE_HANDLE.get(this);
        }

        @SuppressWarnings("unchecked")
        V getValueAcquire() {
            return (V) VALUE_HANDLE.getAcquire(this);
        }

        @SuppressWarnings("unchecked")
        V getValueVolatile() {
            return (V) VALUE_HANDLE.getVolatile(this);
        }

        void setValuePlain(final V value) {
            VALUE_HANDLE.set(this, value);
        }

        void setValueRelease(final V value) {
            VALUE_HANDLE.setRelease(this, value);
        }

        void setValueVolatile(final V value) {
            VALUE_HANDLE.setVolatile(this, value);
        }

        boolean compareAndSetValueVolatile(final V expect, final V update) {
            return VALUE_HANDLE.compareAndSet(this, expect, update);
        }

        @SuppressWarnings("unchecked")
        TableEntry<V> getNextPlain() {
            return (TableEntry<V>) NEXT_HANDLE.get(this);
        }

        @SuppressWarnings("unchecked")
        TableEntry<V> getNextVolatile() {
            return (TableEntry<V>) NEXT_HANDLE.getVolatile(this);
        }

        void setNextPlain(final TableEntry<V> next) {
            NEXT_HANDLE.set(this, next);
        }

        void setNextRelease(final TableEntry<V> next) {
            NEXT_HANDLE.setRelease(this, next);
        }

        void setNextVolatile(final TableEntry<V> next) {
            NEXT_HANDLE.setVolatile(this, next);
        }

        boolean isResizeMarker() {
            return this.resizeMarker;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TableEntry<?> that)) return false;
            return key == that.key && Objects.equals(getValueVolatile(), that.getValueVolatile()); // Use volatile read for value
        }

        @Override
        public int hashCode() {
            return Long.hashCode(key) ^ Objects.hashCode(getValueVolatile()); // Use volatile read for value
        }

        @Override
        public String toString() {
            return key + "=" + getValueVolatile(); // Use volatile read for value
        }
    }

    /**
     * Base class for traversing nodes, handling resizes.
     * Note: This iterator implementation is simplified and might not be fully robust against
     * rapid concurrent modifications during iteration, particularly multiple resize events.
     * It aims for basic correctness in common scenarios.
     */
    protected static class NodeIterator<V> {
        final LeafConcurrentLong2ReferenceChainedHashTable<V> map;
        TableEntry<V>[] currentTable;
        TableEntry<V> nextNode;
        int nextTableIndex;
        TableEntry<V> currentNodeInChain; // Current node within the chain being processed

        NodeIterator(TableEntry<V>[] initialTable, LeafConcurrentLong2ReferenceChainedHashTable<V> map) {
            this.map = map;
            this.currentTable = initialTable; // Start with the table state at iterator creation
            this.nextNode = null;
            // Start iteration from the end of the table backwards
            this.nextTableIndex = (initialTable == null || initialTable.length == 0) ? -1 : initialTable.length - 1;
            this.currentNodeInChain = null;
            advance(); // Find the first element
        }

        /**
         * Advances to find the next valid node (non-null value, non-marker).
         * Sets {@code nextNode}. Handles basic traversal and checks for table changes.
         */
        final void advance() {
            nextNode = null; // Assume no next node initially

            if (currentNodeInChain != null) {
                currentNodeInChain = currentNodeInChain.getNextVolatile(); // Move to next in chain
            }

            while (nextNode == null) {
                if (currentNodeInChain != null) {
                    // Check if the node is valid (not marker, has value)
                    if (!currentNodeInChain.isResizeMarker() && currentNodeInChain.getValueVolatile() != null) {
                        nextNode = currentNodeInChain; // Found a valid node
                        return; // Exit advance
                    }
                    // Node invalid (marker or placeholder), move to the next
                    currentNodeInChain = currentNodeInChain.getNextVolatile();
                    continue; // Check next node in chain
                }

                if (nextTableIndex < 0) {
                    // Check if the underlying table reference changed (indicates resize)
                    // This is a simplified check; robust iterators might need more complex resize handling
                    if (this.currentTable != map.table) {
                        // Table changed, restart iteration from the new table
                        this.currentTable = map.table;
                        this.nextTableIndex = (this.currentTable == null || this.currentTable.length == 0) ? -1 : this.currentTable.length - 1;
                        this.currentNodeInChain = null;
                        // Retry finding a node from the beginning of the new table
                        continue;
                    }
                    // No table change and all bins checked
                    return; // Exhausted
                }

                if (this.currentTable != null && this.nextTableIndex < this.currentTable.length) {
                    TableEntry<V> head = getAtIndexVolatile(this.currentTable, this.nextTableIndex--); // Read head and decrement index

                    if (head != null && !head.isResizeMarker()) {
                        // Start traversing this new chain
                        currentNodeInChain = head;
                        // Check if the head itself is a valid node
                        if (currentNodeInChain.getValueVolatile() != null) {
                            nextNode = currentNodeInChain;
                            return; // Found valid node (head of bin)
                        }
                        // Head is placeholder, continue loop to check next in chain
                        continue;
                    }
                    // Bin was empty or head was marker. Reset chain traversal.
                    currentNodeInChain = null;
                } else {
                    // Table became null or index out of bounds (shouldn't happen unless table shrinks drastically)
                    // Force moving to next index to avoid infinite loop
                    nextTableIndex--;
                    currentNodeInChain = null;
                    // Consider checking map.table again here for robustness
                    if (this.currentTable != map.table) {
                        // Restart if table changed
                        this.currentTable = map.table;
                        this.nextTableIndex = (this.currentTable == null || this.currentTable.length == 0) ? -1 : this.currentTable.length - 1;
                        continue;
                    }
                }
            } // End while (nextNode == null)
        }


        public final boolean hasNext() {
            return this.nextNode != null;
        }

        /**
         * Internal method to get the next node and advance.
         */
        final TableEntry<V> findNext() {
            TableEntry<V> e = this.nextNode;
            if (e == null) {
                return null; // Signifies end for internal use
            }
            advance(); // Prepare for the *next* call
            return e; // Return the previously found node
        }
    }

    /**
     * Base class for concrete iterators (Entry, Key, Value).
     * Handles remove() and NoSuchElementException.
     */
    protected static abstract class BaseIteratorImpl<V, T> extends NodeIterator<V> implements Iterator<T> {
        protected TableEntry<V> lastReturned; // Node returned by last next() call

        protected BaseIteratorImpl(final LeafConcurrentLong2ReferenceChainedHashTable<V> map) {
            super(map.table, map); // Initialize NodeIterator
            this.lastReturned = null;
        }

        /**
         * Gets the next node, updates lastReturned, advances iterator.
         */
        protected final TableEntry<V> nextNode() throws NoSuchElementException {
            TableEntry<V> node = this.nextNode; // Node pre-fetched by advance()
            if (node == null) {
                throw new NoSuchElementException();
            }
            this.lastReturned = node; // Store for remove()
            advance(); // Find the *next* node for the subsequent call
            return node; // Return the current node
        }

        @Override
        public void remove() {
            TableEntry<V> last = this.lastReturned;
            if (last == null) {
                throw new IllegalStateException("next() not called or remove() already called");
            }
            this.map.remove(last.key); // Delegate removal to map's method
            this.lastReturned = null; // Prevent double remove
        }

        @Override
        public abstract T next() throws NoSuchElementException; // Must be implemented by subclass

        @Override
        public void forEachRemaining(final Consumer<? super T> action) {
            Validate.notNull(action, "Action may not be null");
            while (hasNext()) {
                action.accept(next());
            }
        }
    }

    /**
     * Iterator over map entries (TableEntry objects).
     */
    protected static final class EntryIterator<V> extends BaseIteratorImpl<V, TableEntry<V>> {
        EntryIterator(final LeafConcurrentLong2ReferenceChainedHashTable<V> map) {
            super(map);
        }

        @Override
        public TableEntry<V> next() throws NoSuchElementException {
            return nextNode();
        }
    }

    /**
     * Iterator over map keys (long primitives).
     */
    protected static final class KeyIterator<V> extends BaseIteratorImpl<V, Long> implements PrimitiveIterator.OfLong {
        KeyIterator(final LeafConcurrentLong2ReferenceChainedHashTable<V> map) {
            super(map);
        }

        @Override
        public long nextLong() throws NoSuchElementException {
            return nextNode().key;
        }

        @Override
        public Long next() throws NoSuchElementException {
            return nextLong(); // Autoboxing
        }

        @Override
        public void forEachRemaining(final LongConsumer action) {
            Validate.notNull(action, "Action may not be null");
            while (hasNext()) {
                action.accept(nextLong());
            }
        }

        @Override
        public void forEachRemaining(final Consumer<? super Long> action) {
            if (action instanceof LongConsumer) {
                forEachRemaining((LongConsumer) action);
            } else {
                Validate.notNull(action, "Action may not be null");
                while (hasNext()) {
                    action.accept(nextLong()); // Autoboxing
                }
            }
        }
    }

    /**
     * Iterator over map values.
     */
    protected static final class ValueIterator<V> extends BaseIteratorImpl<V, V> {
        ValueIterator(final LeafConcurrentLong2ReferenceChainedHashTable<V> map) {
            super(map);
        }

        @Override
        public V next() throws NoSuchElementException {
            return nextNode().getValueVolatile(); // Volatile read for value
        }
    }

    // --- Collection Views ---

    /**
     * Base class for Collection views (Values, EntrySet).
     */
    protected static abstract class BaseCollection<V, E> implements Collection<E> {
        protected final LeafConcurrentLong2ReferenceChainedHashTable<V> map;

        protected BaseCollection(LeafConcurrentLong2ReferenceChainedHashTable<V> map) {
            this.map = Validate.notNull(map);
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public abstract boolean contains(Object o); // Subclass responsibility

        @Override
        public boolean containsAll(Collection<?> c) {
            Validate.notNull(c);
            for (Object e : c) {
                if (!contains(e)) return false;
            }
            return true;
        }

        @Override
        public Object[] toArray() {
            List<E> list = new ArrayList<>(map.size());
            for (E e : this) list.add(e); // Uses iterator() from subclass
            return list.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            Validate.notNull(a);
            List<E> list = new ArrayList<>(map.size());
            for (E e : this) list.add(e);
            return list.toArray(a);
        }

        @Override
        public void clear() {
            map.clear();
        }

        @Override
        public boolean add(E e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(Collection<? extends E> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            Iterator<E> it = iterator(); // Subclass provides iterator
            while (it.hasNext()) {
                if (Objects.equals(o, it.next())) {
                    it.remove(); // Use iterator's safe remove
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            Validate.notNull(c);
            boolean modified = false;
            Iterator<E> it = iterator();
            while (it.hasNext()) {
                if (c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            Validate.notNull(c);
            boolean modified = false;
            Iterator<E> it = iterator();
            while (it.hasNext()) {
                if (!c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }

        @Override
        public boolean removeIf(Predicate<? super E> filter) {
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

        @Override
        public String toString() {
            Iterator<E> it = iterator();
            if (!it.hasNext()) return "[]";
            StringBuilder sb = new StringBuilder("[");
            for (;;) {
                E e = it.next();
                sb.append(e == this ? "(this Collection)" : e);
                if (!it.hasNext()) return sb.append(']').toString();
                sb.append(',').append(' ');
            }
        }

        @Override
        public void forEach(Consumer<? super E> action) {
            Validate.notNull(action);
            for (E e : this) { // Uses iterator() from subclass
                action.accept(e);
            }
        }
    }

    /**
     * Collection view for the map's values.
     */
    protected static final class Values<V> extends BaseCollection<V, V> {
        Values(LeafConcurrentLong2ReferenceChainedHashTable<V> map) {
            super(map);
        }

        @Override
        public boolean contains(Object o) {
            try {
                return o != null && map.containsValue((V) o);
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @Override
        public Iterator<V> iterator() {
            return map.valueIterator();
        }
    }

    /**
     * Set view for the map's entries (TableEntry objects).
     */
    protected static final class EntrySet<V> extends BaseCollection<V, TableEntry<V>> implements Set<TableEntry<V>> {
        EntrySet(LeafConcurrentLong2ReferenceChainedHashTable<V> map) {
            super(map);
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof LeafConcurrentLong2ReferenceChainedHashTable.TableEntry<?> entry)) return false;
            V mappedValue = map.get(entry.getKey()); // Concurrent read
            // Use volatile read on entry's value for consistent comparison
            return mappedValue != null && Objects.equals(mappedValue, entry.getValueVolatile());
        }

        @Override
        public Iterator<TableEntry<V>> iterator() {
            return map.entryIterator();
        }

        @Override
        public boolean remove(Object o) {
            if (!(o instanceof LeafConcurrentLong2ReferenceChainedHashTable.TableEntry<?> entry)) return false;
            try {
                // Use map's atomic remove(key, value)
                // Use volatile read for the expected value
                return map.remove(entry.getKey(), (V) entry.getValueVolatile());
            } catch (ClassCastException | NullPointerException cce) { // Handle potential type/null issues
                return false;
            }
        }

        @Override
        public int hashCode() {
            int h = 0;
            for (TableEntry<V> e : this) {
                h += e.hashCode(); // Uses entry's hashCode
            }
            return h;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof Set<?> c)) return false;
            if (c.size() != size()) return false;
            try {
                // relies on containsAll checking entry equality correctly
                return containsAll(c);
            } catch (ClassCastException | NullPointerException unused) {
                return false;
            }
        }
    }
}
