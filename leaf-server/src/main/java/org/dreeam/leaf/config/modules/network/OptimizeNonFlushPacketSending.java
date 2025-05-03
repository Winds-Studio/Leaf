package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class OptimizeNonFlushPacketSending extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.NETWORK.getBaseKeyName();
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".OptimizeNonFlushPacketSending", enabled, config.pickStringRegionBased("""
                WARNING: This option is NOT compatible with ProtocolLib and may cause
                issues with other plugins that modify packet handling.
                
                Optimizes non-flush packet sending by using Netty's lazyExecute method to avoid
                expensive thread wakeup calls when scheduling packet operations.
                
                Requires server restart to take effect.""",
            """           
                警告: 此选项与 ProtocolLib 不兼容, 并可能导致与其他修改数据包
                处理的插件出现问题.
                
                通过使用 Netty 的 lazyExecute 方法来优化非刷新数据包的发送,
                避免在调度数据包操作时进行昂贵的线程唤醒调用.
                
                需要重启服务器才能生效."""));
    }
}
