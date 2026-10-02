package org.dreeam.leaf.util.queue;

@SuppressWarnings("unused")
sealed class ReadCounter permits CachePadded1 {
    public volatile long reads;
}
