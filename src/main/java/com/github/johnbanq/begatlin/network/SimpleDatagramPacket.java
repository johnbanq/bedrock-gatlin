package com.github.johnbanq.begatlin.network;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramPacket;
import io.vertx.core.net.SocketAddress;

public final class SimpleDatagramPacket implements DatagramPacket {

    private final Buffer buffer;
    private final SocketAddress peerAddress;

    public SimpleDatagramPacket(SocketAddress peerAddress, Buffer buffer) {
        this.peerAddress = peerAddress;
        this.buffer = buffer;
    }

    public static SimpleDatagramPacket fromComponents(SocketAddress peer, Buffer buffer) {
        return new SimpleDatagramPacket(peer, buffer);
    }

    @Override
    public SocketAddress sender() {
        return peerAddress;
    }

    @Override
    public Buffer data() {
        return buffer;
    }

}
