package com.github.johnbanq.begatlin.gateway;

import com.github.johnbanq.begatlin.network.ReactiveDatagramSocket;
import com.github.johnbanq.begatlin.protocol.BedrockPong;
import com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPing;
import com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPong;
import com.google.common.collect.Comparators;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramPacket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.net.SocketAddress;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.Disposables;
import reactor.core.publisher.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import static com.github.johnbanq.begatlin.network.SimpleDatagramPacket.fromComponents;

/**
 * represents a server that needs to be served to local network
 */
@Slf4j
public class OutboundServer {

    private static final Duration TIMEOUT_DURATION = Duration.ofSeconds(60);

    private final Vertx vertx;

    private final Inbound inbound;

    /**
     * hot mono, indicates the state of server (running, successfully stop(complete) and errored(error))
     */
    private final Flux<Void> stateFlux;

    private final FluxSink<Void> stateSink;

    private final Flux<Instant> timerFlux;

    private final ReactiveDatagramSocket socket;

    private static class InboundPlayerConnection {

        public Instant lastArrival = Instant.now();

        public SocketAddress remoteAddress;

        public FluxSink<Buffer> receiverSink;

        public InboundPlayerConnection(SocketAddress remoteAddress, FluxSink<Buffer> receiverSink) {
            this.remoteAddress = remoteAddress;
            this.receiverSink = receiverSink;
        }

        public void refreshLastArrival() {
            lastArrival = Instant.now();
        }

    }

    private final Map<SocketAddress, InboundPlayerConnection> connections;


    public static Mono<OutboundServer> createServer(Inbound inbound, Vertx vertx) {
        return createServer(inbound, vertx, SocketAddress.inetSocketAddress(0, "0.0.0.0"));
    }

    public static Mono<OutboundServer> createServer(Inbound inbound, Vertx vertx, SocketAddress exposeAddress) {
        return createServer(inbound, vertx, exposeAddress, new DatagramSocketOptions());
    }

    public static Mono<OutboundServer> createServer(Inbound inbound, Vertx vertx, SocketAddress exposeAddress, DatagramSocketOptions options) {
        final val server = new OutboundServer(inbound, vertx, exposeAddress, options);
        return server.start(exposeAddress).thenReturn(server);
    }

    OutboundServer(Inbound inbound, Vertx vertx, SocketAddress exposeAddress, DatagramSocketOptions options) {
        this.inbound = inbound;
        this.vertx = vertx;

        final val processor = DirectProcessor.<Void>create();
        this.stateFlux = processor;
        this.stateSink = processor.sink();

        this.timerFlux = Flux.interval(Duration.ofSeconds(1))
                .map(i->Instant.now())
                .publish().refCount()
                ;

        socket = new ReactiveDatagramSocket(vertx, options);

        connections = new HashMap<>();
    }

    public Flux<Void> getStateFlux() {
        return stateFlux;
    }

    Mono<Void> start(SocketAddress exposeAddress) {
        return socket.start(exposeAddress)
                .flatMap(s->{
                    //for ease of cleanup
                    final val disposables = Disposables.composite();
                    disposables.add(()->log.info("stopping LocalOutboundServer: {}:{}", exposeAddress.host(),exposeAddress.port()));

                    //network socket and it's cleanup
                    final val packets = socket.receiver().publish().refCount();
                    disposables.add(()->socket.stop().subscribe());

                    //broadcast handler
                    final val broadcast = packets
                            .filter(p -> p.data().getByte(0) == RaknetUnconnectedPing.TYPE)
                            .flatMap(p ->
                                    handlePing(p)
                                            //firewall,prevents error in handling 1 request blows up the entire server
                                            .doOnError(ex->log.error("error in handling ping", ex))
                                            .onErrorResume(ex->Mono.empty())
                            )
                            .subscribe(v->{}, stateSink::error);
                    disposables.add(broadcast);

                    //game packet handler
                    final val gamePackets = packets
                            .filter(p -> p.data().getByte(0) != RaknetUnconnectedPing.TYPE)
                            .flatMap(p ->
                                    handleGamePacket(p)
                                            //firewall,prevents error in handling 1 request blows up the entire server
                                            .doOnError(ex->log.error("error in dispatching packet", ex))
                                            .onErrorResume(ex->Mono.empty())
                            )
                            .subscribe(v->{}, stateSink::error);
                    disposables.add(gamePackets);

                    stateSink.onDispose(disposables);

                    return Mono.empty();
                });
    }

    public Mono<Void> stop() {
        return Mono.fromRunnable(stateSink::complete);
    }

    public SocketAddress localAddress() {
        return socket.getSocket().localAddress();
    }

    private Mono<Void> handlePing(DatagramPacket packet) {
        final val request = RaknetUnconnectedPing.fromRakNet(packet.data().getBytes());
        return inbound.handlePing(request)
                .map(inboundPong->{
                    final val idstr = BedrockPong.fromRakNet(inboundPong.getIDString());
                    idstr.setIpv4Port(localAddress().port());
                    final val pong = new RaknetUnconnectedPong(
                            inboundPong.getMilliSinceStart(),
                            inboundPong.getServerGUID(),
                            inboundPong.getOfflineMessageDataID(),
                            idstr.toRakNet()
                    );
                    return fromComponents(packet.sender(), Buffer.buffer(pong.toRakNet()));
                })
                .flatMap(socket::send)
                ;
    }

    private Mono<Void> handleGamePacket(DatagramPacket packet) {
        val source = connections.get(packet.sender());
        val dispatch = source != null ? Mono.just(source) : newPlayerConnection(packet.sender());
        return dispatch
                .doOnNext(InboundPlayerConnection::refreshLastArrival)
                .flatMap(v->Mono.fromRunnable(()->v.receiverSink.next(packet.data())));
    }

    private Mono<InboundPlayerConnection> newPlayerConnection(SocketAddress source) {
        final val disposables = Disposables.composite();

        final val processor = ReplayProcessor.<Buffer>cacheLast();
        final val sink = processor.sink();
        val inboundConn = new InboundPlayerConnection(source, sink);

        //garbage collect, if not active long enough, then remove
        final val gc = timerFlux
                .subscribe(now->{
                    if(Duration.between(inboundConn.lastArrival, now).compareTo(TIMEOUT_DURATION)>0) {
                        sink.complete();
                    }
                });
        disposables.add(gc);
        //shutdown if the entire server is going down
        final val state = stateFlux
                .subscribe(v->{}, sink::error, sink::complete);
        disposables.add(state);

        sink.onDispose(disposables);

        connections.put(source, inboundConn);

        return inbound.handleNewPlayer(new PlayerConnection(processor, d->socket.send(fromComponents(source, d))))
                .thenReturn(inboundConn);
    }

}
