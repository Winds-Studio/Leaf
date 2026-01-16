package org.leavesmc.leaves.protocol.jade.provider;

import org.leavesmc.leaves.protocol.jade.accessor.Accessor;
import org.leavesmc.leaves.protocol.jade.util.ViewGroup;

import java.util.List;

public interface IServerExtensionProvider<T> extends IJadeProvider {
    List<ViewGroup<T>> getGroups(Accessor<?> request);
}