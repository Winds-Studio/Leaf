package org.dreeam.leaf.async.chunk;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.async.LocalDispatcher;

public final class AsyncChunkSend {

    public static final LocalDispatcher POOL = new LocalDispatcher();
    public static final Logger LOGGER = LogManager.getLogger("Leaf Async Chunk Send");

    private AsyncChunkSend() {
    }
}
