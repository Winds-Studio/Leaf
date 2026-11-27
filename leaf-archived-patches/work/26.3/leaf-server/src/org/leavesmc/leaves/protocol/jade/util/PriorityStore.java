package org.leavesmc.leaves.protocol.jade.util;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public class PriorityStore<K, V> {

    private final Object2IntMap<K> priorities = new Object2IntLinkedOpenHashMap<>();
    private final Function<V, K> keyGetter;
    private final ToIntFunction<V> defaultPriorityGetter;

    public PriorityStore(ToIntFunction<V> defaultPriorityGetter, Function<V, K> keyGetter) {
        this.defaultPriorityGetter = defaultPriorityGetter;
        this.keyGetter = keyGetter;
    }

    public void put(V provider) {
        Objects.requireNonNull(provider);
        put(provider, defaultPriorityGetter.applyAsInt(provider));
    }

    public void put(V provider, int priority) {
        Objects.requireNonNull(provider);
        K uid = keyGetter.apply(provider);
        Objects.requireNonNull(uid);
        priorities.put(uid, priority);
    }

    public int byValue(V value) {
        return byKey(keyGetter.apply(value));
    }

    public int byKey(K id) {
        return priorities.getInt(id);
    }
}
