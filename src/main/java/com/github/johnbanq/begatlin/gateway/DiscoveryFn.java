package com.github.johnbanq.begatlin.gateway;

import com.github.johnbanq.begatlin.network.ReactiveDatagramSocket;
import com.github.johnbanq.begatlin.protocol.BedrockPong;
import com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPing;
import com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPong;
import com.google.common.primitives.UnsignedLong;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.SocketAddress;
import lombok.val;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.function.Function;

import static com.github.johnbanq.begatlin.network.SimpleDatagramPacket.fromComponents;
import static com.github.johnbanq.begatlin.protocol.ProtocolUtil.OFFLINE_MESSAGE_ID_MAGIC;

public class DiscoveryFn {

    public static Flux<DiscoveredServer> periodicPing(ReactiveDatagramSocket socket, SocketAddress targetAddress, Duration duration) {
        return runPing(socket, targetAddress, Flux.interval(duration));
    }

    public static Function<Flux<Long>, Flux<DiscoveredServer>> ping(ReactiveDatagramSocket socket, SocketAddress targetAddress) {
        return flux->runPing(socket, targetAddress, flux);
    }

    public static Flux<DiscoveredServer> runPing(ReactiveDatagramSocket socket, SocketAddress targetAddress, Flux<Long> requestFlux) {
        return withListeningSocket(socket, SocketAddress.inetSocketAddress(0, "0.0.0.0"), s2 ->
                Flux.create(sink -> {
                    final val receiver = socket.receiver()
                            .filter(p -> p.data().getByte(0) == RaknetUnconnectedPong.TYPE)
                            .map(p -> {
                                final val pong = RaknetUnconnectedPong.fromRakNet(p.data().getBytes());
                                return new DiscoveredServer(p.sender().host(), pong.getServerGUID(), BedrockPong.fromRakNet(pong.getIDString()));
                            })
                            .subscribe(sink::next, sink::error, sink::complete);

                    final val sender = requestFlux
                            .onBackpressureLatest()
                            .map(i->fromComponents(targetAddress, assembleMockPingPacket()))
                            .flatMap(socket::send)
                            .subscribe(v -> {}, sink::error);

                    sink.onDispose(Disposables.composite(sender, receiver));
                }));
    }

    public static <T> Flux<T> withListeningSocket(ReactiveDatagramSocket socket, SocketAddress address, Function<ReactiveDatagramSocket, Flux<T>> handler) {
        return socket.start(address)
                .flatMapMany(handler)
                .doFinally(t->socket.stop().subscribe())
                ;
    }

    public static Buffer assembleMockPingPacket() {
        final val ping = new RaknetUnconnectedPing();
        ping.setMilliSinceStart(UnsignedLong.valueOf(100));
        ping.setClientGUID(UnsignedLong.valueOf(10));
        ping.setOfflineMessageDataID(OFFLINE_MESSAGE_ID_MAGIC);
        return Buffer.buffer(ping.toRakNet());
    }


}
