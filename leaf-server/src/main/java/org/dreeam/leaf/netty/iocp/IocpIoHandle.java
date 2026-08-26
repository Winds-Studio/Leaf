package org.dreeam.leaf.netty.iocp;

import io.netty.channel.IoHandle;

interface IocpIoHandle extends IoHandle {
    long nativeHandle();

    int associate(long port, long key);
}
