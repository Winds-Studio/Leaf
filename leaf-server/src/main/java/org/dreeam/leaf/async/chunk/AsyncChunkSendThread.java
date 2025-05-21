package org.dreeam.leaf.async.chunk;

public class AsyncChunkSendThread extends Thread {

    protected AsyncChunkSendThread(Runnable task) {
        super(task);
    }
}
