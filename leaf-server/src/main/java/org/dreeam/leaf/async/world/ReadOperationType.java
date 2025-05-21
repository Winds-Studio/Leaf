package org.dreeam.leaf.async.world;

public enum ReadOperationType {

    GET_HIGHEST_BLOCK_Y_AT,
    GET_BLOCK_DATA,
    GET_BLOCK_STATE,
    GET_BLOCK_TYPE,
    GET_BIOME,
    GET_COMPUTED_BIOME,
    GET_ENTITIES_IN_BOX, // Note: Handling lists/collections needs care
    IS_CHUNK_LOADED,
    IS_CHUNK_GENERATED,

    BLOCK_GET_BIOME,
    BLOCK_GET_COMPUTED_BIOME,
    BLOCK_IS_INDIRECTLY_POWERED,
    BLOCK_GET_BLOCK_POWER,
    BLOCK_RAY_TRACE,
    BLOCK_CAN_PLACE,
    BLOCK_GET_NMS_STATE
}
