package org.dreeam.leaf.util.queue;

@SuppressWarnings("unused")
sealed class WriteCounter extends CachePadded1 permits MpmcQueue {
    public volatile long writes;
}
