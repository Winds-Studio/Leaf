// Leaf - Bump netty to 4.2.x

package org.dreeam.leaf;

import java.util.Locale;

public enum NetworkIoModel {

    IO_URING("io_uring"),
    KQUEUE("kqueue"),
    EPOLL("epoll"),
    NIO("nio");

    private final String name;

    NetworkIoModel(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public static NetworkIoModel fromName(String name) {
        if (name == null) return EPOLL;
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "io_uring" -> IO_URING;
            case "kqueue" -> KQUEUE;
            case "epoll" -> EPOLL;
            default -> NIO;
        };
    }

    public static NetworkIoModel fromProperty() {
        final String name = System.getProperty("Leaf.native-transport-type");
        if (name == null) return EPOLL;
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "io_uring" -> IO_URING;
            case "kqueue" -> KQUEUE;
            case "epoll" -> EPOLL;
            default -> NIO;
        };
    }
}
