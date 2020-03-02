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
        return withListeningSocket(socket ->
                Mono.create(sink -> {
                    final val receiver = socket.receiver()
                            .filter(p -> p.data().getByte(0) == RaknetUnconnectedPong.TYPE)
                            .map(p -> {
                                final val pong = RaknetUnconnectedPong.fromRakNet(p.data().getBytes());
                                return new DiscoveredServer(p.sender().host(), pong.getServerGUID(), BedrockPong.fromRakNet(pong.getIDString()));
                            })
                            .take(Duration.ofSeconds(30))
                            .collect(Collectors.toSet())
                            .subscribe(sink::success, sink::error, sink::success);
                    sink.onDispose(receiver);

                    final val packets = Mono.just(fromComponents(
                            SocketAddress.inetSocketAddress(19132, "255.255.255.255"),
                            Buffer.buffer(assemblePingPacket())
                    )).repeat(10);

                    final val sender = packets
                            .flatMap(socket::send)
                            .subscribe(v -> {}, sink::error);
                    sink.onDispose(sender);
                }));
    }

    private <T> Mono<T> withListeningSocket(Function<ReactiveDatagramSocket, Mono<T>> handler) {
        options.setBroadcast(true);
        final val socket = new ReactiveDatagramSocket(vertx, options);
        return socket.start(SocketAddress.inetSocketAddress(9, "0.0.0.0"))
                .flatMap(handler)
                .doFinally(t->socket.stop().subscribe())
                ;
    }

    private byte[] assemblePingPacket() {
        final val ping = new RaknetUnconnectedPing();
        ping.setMilliSinceStart(UnsignedLong.valueOf(100));
        ping.setClientGUID(UnsignedLong.valueOf(10));
        ping.setOfflineMessageDataID(OFFLINE_MESSAGE_ID_MAGIC);
        return ping.toRakNet();
    }

}
