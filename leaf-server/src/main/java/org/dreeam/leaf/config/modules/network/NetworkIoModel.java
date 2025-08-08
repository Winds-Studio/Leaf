package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

import java.util.Locale;

public class NetworkIoModel extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.NETWORK.getBaseKeyName();
    }

    public static String model = "AUTO";

    public static boolean useIoUring;
    public static boolean useEpoll;

    @Override
    public void onLoaded() {
        model = config.getString(getBasePath() + ".network-io-model", model, config.pickStringRegionBased("""
                Available I/O model: IO_URING, EPOLL, NIO.
                It will fallback to another if the one selected is not available,
                IO_URING -> EPOLL -> NIO.
                (This config overrides use-native-transport in server.properties)
                """,
            """           
                可用 I/O 模型: IO_URING, EPOLL, NIO.
                如果所选的模型不可用, 将会自动切换到可用的模型,
                IO_URING -> EPOLL -> NIO.
                (此选项会覆盖 server.properties 内的 use-native-transport)
                """));

        // Let the system choose suitable model
        // And fallback to Nio if non-match
        switch (model.toUpperCase(Locale.ROOT)) {
            case "AUTO", "IO_URING" -> {
                useIoUring = true;
                useEpoll = true;
            }
            case "EPOLL" -> useEpoll = true;
        }
    }
}
