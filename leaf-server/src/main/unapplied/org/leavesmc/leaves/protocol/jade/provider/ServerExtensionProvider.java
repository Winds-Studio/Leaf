package org.leavesmc.leaves.protocol.jade.provider;

import org.leavesmc.leaves.protocol.jade.accessor.Accessor;
import org.leavesmc.leaves.protocol.jade.util.ViewGroup;

import java.util.List;

public interface ServerExtensionProvider<T> extends JadeProvider {
    List<ViewGroup<T>> getGroups(Accessor<?> request);
}