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
import org.checkerframework.common.reflection.qual.NewInstance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.github.johnbanq.begatlin.network.SimpleDatagramPacket.fromComponents;
import static com.github.johnbanq.begatlin.protocol.ProtocolUtil.OFFLINE_MESSAGE_ID_MAGIC;

/**
 * a server that responds to server discovery(unconnected ping) of client on networks that this machine is connected to
 */
@Slf4j
public class DiscoveryServer {

    private final Vertx vertx;
    private final DatagramSocketOptions options;

    private final Map<UnsignedLong, BedrockPong> id2pong;

    public DiscoveryServer(Vertx vertx) {
        this(vertx, new DatagramSocketOptions());
    }

    public DiscoveryServer(Vertx vertx, DatagramSocketOptions options) {
        this.vertx = vertx;
        this.options = options;
        id2pong = new HashMap<>();
    }


    public void addServer(BedrockPong pong) {
        id2pong.put(pong.getServerId(), pong);
    }

    public void removeServer(UnsignedLong id) {
        id2pong.remove(id);
    }


    public Mono<Void> start() {
        final Instant started = Instant.now();
        return withListeningSocket(socket->
                socket.receiver()
                        .filter(p->p.data().getByte(0)== RaknetUnconnectedPing.TYPE)
                        .flatMap(request->
                                Flux.fromIterable(id2pong.values())
                                        .flatMap(server-> {
                                                    final val packet = new RaknetUnconnectedPong(
                                                            UnsignedLong.valueOf(Instant.now().toEpochMilli()-started.toEpochMilli()),
                                                            UnsignedLong.valueOf(123),
                                                            OFFLINE_MESSAGE_ID_MAGIC,
                                                            server.toRakNet()
                                                    ).toRakNet();
                                                    return socket.send(fromComponents(request.sender(), Buffer.buffer(packet)));
                                        })
                                        .onErrorMap(ex->{log.error("error in handling request", ex); return null;})
                        )
                        .ignoreElements()
        );
    }


    private <T> Mono<T> withListeningSocket(Function<ReactiveDatagramSocket, Mono<T>> inside) {
        final val socket = new ReactiveDatagramSocket(
                vertx,
                options
                        .setReusePort(true)
                        .setReuseAddress(true)
        );
        return socket
                .start(SocketAddress.inetSocketAddress(19132, "0.0.0.0"))
                .flatMap(inside)
                .doFinally(v->socket.stop().subscribe())
                ;
    }

}
