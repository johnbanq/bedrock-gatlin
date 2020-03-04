package com.github.johnbanq.begatlin.gateway;

import com.github.johnbanq.begatlin.network.ReactiveDatagramSocket;
import com.github.johnbanq.begatlin.protocol.BedrockPong;
import com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPing;
import com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPong;
import com.google.common.primitives.UnsignedLong;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.net.SocketAddress;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.github.johnbanq.begatlin.network.SimpleDatagramPacket.fromComponents;
import static com.github.johnbanq.begatlin.protocol.ProtocolUtil.OFFLINE_MESSAGE_ID_MAGIC;

/**
 * a client for performing minecraft bedrock server discovery on local network
 */
@Slf4j
public class DiscoveryClient {

    private final Vertx vertx;
    private final DatagramSocketOptions options;

    public DiscoveryClient(Vertx vertx) {
        this(vertx, new DatagramSocketOptions());
    }

    public DiscoveryClient(Vertx vertx, DatagramSocketOptions options) {
        this.vertx = vertx;
        this.options = options;
    }


    public Mono<Set<DiscoveredServer>> findServersOnLocalEthernet() {
        return findServerAt(SocketAddress.inetSocketAddress(19132, "255.255.255.255"));
    }

    public Mono<Set<DiscoveredServer>> findServerAt(SocketAddress address) {
        final val socket = new ReactiveDatagramSocket(vertx, new DatagramSocketOptions().setBroadcast(true));
        return DiscoveryFn.periodicPing(socket, address, Duration.ofSeconds(1))
                .map(p-> {
                    final val pong = RaknetUnconnectedPong.fromRakNet(p.data().getBytes());
                    return new DiscoveredServer(p.sender().host(), pong.getServerGUID(), BedrockPong.fromRakNet(pong.getIDString()));
                })
                .take(Duration.ofSeconds(30))
                .collect(Collectors.toSet())
                ;
    }

}
