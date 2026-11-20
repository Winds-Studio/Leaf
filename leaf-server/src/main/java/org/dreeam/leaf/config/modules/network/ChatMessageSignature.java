package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.NETWORK)
public class ChatMessageSignature implements ConfigModule {

    @ConfigInfo(name = "chat-message-signature", comments = {
        """
            Whether or not enable chat message signature,
            disable will prevent players to report chat messages.
            And also disables the popup when joining a server without
            'secure chat', such as offline-mode servers.""",
        """
            是否启用聊天签名, 禁用后玩家无法进行聊天举报."""
    })
    public static boolean enabled = true;
}
