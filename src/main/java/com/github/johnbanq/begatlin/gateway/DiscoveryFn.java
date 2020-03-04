package com.github.johnbanq.begatlin.gateway;

import com.github.johnbanq.begatlin.network.ReactiveDatagramSocket;
import com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPing;
import com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPong;
import com.google.common.primitives.UnsignedLong;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramPacket;
import io.vertx.core.net.SocketAddress;
import lombok.val;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.function.Function;

import static com.github.johnbanq.begatlin.network.SimpleDatagramPacket.fromComponents;
import static com.github.johnbanq.begatlin.protocol.ProtocolUtil.OFFLINE_MESSAGE_ID_MAGIC;

public class DiscoveryFn {

    public static Flux<DatagramPacket> periodicPing(ReactiveDatagramSocket socket, SocketAddress targetAddress, Duration duration) {
        return runPing(socket, targetAddress, Flux.interval(duration).map(i->assembleMockPingPacket()));
    }

    public static Flux<DatagramPacket> runPing(ReactiveDatagramSocket socket, SocketAddress targetAddress, Flux<RaknetUnconnectedPing> requestFlux) {
        return withListeningSocket(socket, SocketAddress.inetSocketAddress(0, "0.0.0.0"), s2 ->
                Flux.create(sink -> {
                    final val receiver = socket.receiver()
                            .filter(p -> p.data().getByte(0) == RaknetUnconnectedPong.TYPE)
                            .subscribe(sink::next, sink::error, sink::complete);

                    final val sender = requestFlux
                            .onBackpressureLatest()
                            .map(p->fromComponents(targetAddress, Buffer.buffer(p.toRakNet())))
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

    public static RaknetUnconnectedPing assembleMockPingPacket() {
        final val ping = new RaknetUnconnectedPing();
        ping.setMilliSinceStart(UnsignedLong.valueOf(100));
        ping.setClientGUID(UnsignedLong.valueOf(10));
        ping.setOfflineMessageDataID(OFFLINE_MESSAGE_ID_MAGIC);
        return ping;
    }


}
