package org.leavesmc.leaves.protocol.jade.util;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.minecraft.core.IdMapper;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.LeavesLogger;
import org.leavesmc.leaves.protocol.jade.JadeProtocol;
import org.leavesmc.leaves.protocol.jade.provider.IJadeProvider;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

public class HierarchyLookup<T extends IJadeProvider> implements IHierarchyLookup<T> {
    private final Class<?> baseClass;
    private final Cache<Class<?>, List<T>> resultCache = CacheBuilder.newBuilder().build();
    private final boolean singleton;
    protected boolean idMapped;
    @Nullable
    protected IdMapper<T> idMapper;
    private ListMultimap<Class<?>, T> objects = ArrayListMultimap.create();

    public HierarchyLookup(Class<?> baseClass) {
        this(baseClass, false);
    }

    public HierarchyLookup(Class<?> baseClass, boolean singleton) {
        this.baseClass = baseClass;
        this.singleton = singleton;
    }

    @Override
    public void idMapped() {
        this.idMapped = true;
    }

    @Override
    @Nullable
    public IdMapper<T> idMapper() {
        return idMapper;
    }

    @Override
    public void register(Class<?> clazz, T provider) {
        Preconditions.checkArgument(isClassAcceptable(clazz), "Class %s is not acceptable", clazz);
        Objects.requireNonNull(provider.getUid());
        JadeProtocol.priorities.put(provider);
        objects.put(clazz, provider);
    }

    @Override
    public boolean isClassAcceptable(Class<?> clazz) {
        return baseClass.isAssignableFrom(clazz);
    }

    @Override
    public List<T> get(Class<?> clazz) {
        try {
            return resultCache.get(clazz, () -> {
                List<T> list = Lists.newArrayList();
                getInternal(clazz, list);
                list = ImmutableList.sortedCopyOf(COMPARATOR, list);
                if (singleton && !list.isEmpty()) {
                    return ImmutableList.of(list.getFirst());
                }
                return list;
            });
        } catch (ExecutionException e) {
            LeavesLogger.LOGGER.warning("HierarchyLookup error", e);
        }
        return List.of();
    }

    private void getInternal(Class<?> clazz, List<T> list) {
        if (clazz != baseClass && clazz != Object.class) {
            getInternal(clazz.getSuperclass(), list);
        }
        list.addAll(objects.get(clazz));
    }

    @Override
    public boolean isEmpty() {
        return objects.isEmpty();
    }

    @Override
    public Stream<Map.Entry<Class<?>, Collection<T>>> entries() {
        return objects.asMap().entrySet().stream();
    }

    @Override
    public void invalidate() {
        resultCache.invalidateAll();
    }

    @Override
    public void loadComplete(PriorityStore<ResourceLocation, IJadeProvider> priorityStore) {
        objects.asMap().forEach((clazz, list) -> {
            if (list.size() < 2) {
                return;
            }
            Set<ResourceLocation> set = Sets.newHashSetWithExpectedSize(list.size());
            for (T provider : list) {
                if (set.contains(provider.getUid())) {
                    throw new IllegalStateException("Duplicate UID: %s for %s".formatted(provider.getUid(), list.stream()
                        .filter(p -> p.getUid().equals(provider.getUid()))
                        .map(p -> p.getClass().getName())
                        .toList()
                    ));
                }
                set.add(provider.getUid());
            }
        });

        objects = ImmutableListMultimap.<Class<?>, T>builder()
            .orderValuesBy(Comparator.comparingInt(priorityStore::byValue))
            .putAll(objects)
            .build();

        if (idMapped) {
            idMapper = createIdMapper();
        }
    }
}