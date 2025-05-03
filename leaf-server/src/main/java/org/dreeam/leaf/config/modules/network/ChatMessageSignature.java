package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class ChatMessageSignature extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.NETWORK.getBaseKeyName();
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".chat-message-signature", enabled, config.pickStringRegionBased("""
                Whether or not enable chat message signature,
                disable will prevent players to report chat messages.
                And also disables the popup when joining a server without
                'secure chat', such as offline-mode servers.""",
            """
                是否启用聊天签名, 禁用后玩家无法进行聊天举报."""));
    }
}
