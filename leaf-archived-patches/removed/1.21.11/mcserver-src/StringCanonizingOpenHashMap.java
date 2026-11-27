package org.dreeam.leaf.util.map;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.Map;
import java.util.function.Function;

public class StringCanonizingOpenHashMap<T> extends Object2ObjectOpenHashMap<String, T> {

    private static final Interner<String> KEY_INTERNER = Interners.newBuilder().weak().concurrencyLevel(16).<String>build();

    private static String intern(String key) {
        return key != null ? KEY_INTERNER.intern(key) : null;
    }

    public StringCanonizingOpenHashMap() {
        super();
    }

    public StringCanonizingOpenHashMap(int expectedSize) {
        super(expectedSize);
    }

    public StringCanonizingOpenHashMap(int expectedSize, float loadFactor) {
        super(expectedSize, loadFactor);
    }

    @Override
    public T put(String key, T value) {
        return super.put(intern(key), value);
    }

    @Override
    public void putAll(Map<? extends String, ? extends T> m) {
        if (m.isEmpty()) return;
        ensureCapacity(size() + m.size());
        for (Map.Entry<? extends String, ? extends T> entry : m.entrySet()) {
            super.put(intern(entry.getKey()), entry.getValue());
        }
    }

    private void putWithoutInterning(String key, T value) {
        super.put(key, value);
    }

    public static <T> StringCanonizingOpenHashMap<T> deepCopy(StringCanonizingOpenHashMap<T> incomingMap, Function<T, T> deepCopier) {
        StringCanonizingOpenHashMap<T> newMap = new StringCanonizingOpenHashMap<>(incomingMap.size(), incomingMap.f);
        ObjectIterator<Entry<String, T>> iterator = incomingMap.object2ObjectEntrySet().fastIterator();

        while (iterator.hasNext()) {
            Map.Entry<String, T> entry = iterator.next();
            newMap.putWithoutInterning(entry.getKey(), deepCopier.apply(entry.getValue()));
        }

        return newMap;
    }
}
