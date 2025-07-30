package org.dreeam.leaf.async.world;

import java.util.concurrent.CompletableFuture;

public record WorldReadRequest(
    ReadOperationType type,
    Object[] params, // Parameters for the read operation
    CompletableFuture<Object> future // Future to complete with the result
) {
}
