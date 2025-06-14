package org.dreeam.leaf.async.container;

public class AsyncContainerBroadcastThread extends Thread {

    protected AsyncContainerBroadcastThread(Runnable task) {
        super(task);
    }
}
