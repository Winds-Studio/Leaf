package org.leavesmc.leaves.protocol.syncmatica;

import net.minecraft.resources.ResourceLocation;

public enum PacketType {
    REGISTER_METADATA("register_metadata"),
    CANCEL_SHARE("cancel_share"),
    REQUEST_LITEMATIC("request_download"),
    SEND_LITEMATIC("send_litematic"),
    RECEIVED_LITEMATIC("received_litematic"),
    FINISHED_LITEMATIC("finished_litematic"),
    CANCEL_LITEMATIC("cancel_litematic"),
    REMOVE_SYNCMATIC("remove_syncmatic"),
    REGISTER_VERSION("register_version"),
    CONFIRM_USER("confirm_user"),
    FEATURE_REQUEST("feature_request"),
    FEATURE("feature"),
    MODIFY("modify"),
    MODIFY_REQUEST("modify_request"),
    MODIFY_REQUEST_DENY("modify_request_deny"),
    MODIFY_REQUEST_ACCEPT("modify_request_accept"),
    MODIFY_FINISH("modify_finish"),
    MESSAGE("mesage");

    public final ResourceLocation identifier;

    PacketType(final String id) {
        identifier = ResourceLocation.tryBuild(SyncmaticaProtocol.PROTOCOL_ID, id);
    }
}
