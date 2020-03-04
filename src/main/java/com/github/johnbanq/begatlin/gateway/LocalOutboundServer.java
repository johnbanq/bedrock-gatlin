package com.github.johnbanq.begatlin.gateway;

import com.github.johnbanq.begatlin.network.ReactiveDatagramSocket;
import com.github.johnbanq.begatlin.protocol.BedrockPong;
import com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPing;
import com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPong;
import com.google.common.primitives.UnsignedLong;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramPacket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.net.SocketAddress;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.MDC;
import reactor.core.Disposables;
import reactor.core.publisher.*;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.github.johnbanq.begatlin.network.SimpleDatagramPacket.fromComponents;

/**
 * outbound server impl, models a outbound server served on local network
 */
@Slf4j
public class LocalOutboundServer {

    private static final Duration TIMEOUT_DURATION = Duration.ofSeconds(60);


    private final Vertx vertx;

    private final InboundServer inbound;


    private final ReactiveDatagramSocket socket;

    /**
     * the GUID used for server id string and raknet pong packets
     */
    private final UnsignedLong guid;

    /**
     * hot mono, indicates the state of server (running, successfully stop(complete) and errored(error))
     */
    private final Flux<Void> stateFlux;

    private final FluxSink<Void> stateSink;

    /**
     * timer flux for triggering timeout on player connections
     */
    private final Flux<Instant> timerFlux;

    /**
     * internal state for a incoming player connection
     */
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

    // factories for defaults & construct then start, eliminating excess 2-step startup

    public static Mono<LocalOutboundServer> createServer(InboundServer inbound, Vertx vertx) {
        return createServer(inbound, vertx, SocketAddress.inetSocketAddress(0, "0.0.0.0"));
    }

    public static Mono<LocalOutboundServer> createServer(InboundServer inbound, Vertx vertx, SocketAddress exposeAddress) {
        return createServer(inbound, vertx, exposeAddress, new DatagramSocketOptions());
    }

    public static Mono<LocalOutboundServer> createServer(InboundServer inbound, Vertx vertx, SocketAddress exposeAddress, DatagramSocketOptions options) {
        final val server = new LocalOutboundServer(inbound, vertx, options);
        return server.start(exposeAddress).thenReturn(server);
    }

    LocalOutboundServer(InboundServer inbound, Vertx vertx, DatagramSocketOptions options) {
        this.inbound = inbound;
        this.vertx = vertx;

        this.guid = UnsignedLong.valueOf(new Random().longs(1, 0, Long.MAX_VALUE).findFirst().getAsLong());

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
                    log.info("starting server on: {}", localAddress());
                    withMDC(log->log.debug("configuring streams"));

                    //for ease of cleanup
                    final val disposables = Disposables.composite();
                    disposables.add(()->withMDC(log->log.info("stopping server, disposing event streams")));

                    //network socket and it's cleanup
                    final val packets = socket.receiver()
                            .doOnError(ex->withMDC(log->log.error("server socket threw exception", ex)))
                            .doOnError(stateSink::error)
                            .publish().refCount();
                    disposables.add(()->socket.stop().subscribe());

                    //broadcast handler
                    final val broadcast = packets
                            .filter(p -> p.data().getByte(0) == RaknetUnconnectedPing.TYPE)
                            .transform(handlePing())
                            .subscribe();
                    disposables.add(broadcast);

                    //game packet handler
                    final val gamePackets = packets
                            .filter(p -> p.data().getByte(0) != RaknetUnconnectedPing.TYPE)
                            .flatMap(p ->
                                    handleGamePacket(p)
                                            //firewall, prevents error in handling 1 request blows up the entire server
                                            .doOnError(ex->withMDC(log->log.error("error in dispatching packets", ex)))
                                            .onErrorResume(ex->Mono.empty())
                            )
                            .subscribe();
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

    // a request handler: flux of requests into a empty flux, used to represent the upstream's complete/error
    private Function<Flux<DatagramPacket>, Flux<Void>> handlePing() {
        return requests -> {
            //get pings
            val pings = requests
                    .doOnNext(p->withMDC(log->log.debug("got ping packet from: {}, type: {}", p.sender(), p.data().getByte(0))))
                    .map(p->Tuples.of(p.sender(), RaknetUnconnectedPing.fromRakNet(p.data().getBytes())))
                    .publish()
                    .autoConnect()
                    ;
            //generate pongs out of the pings, extracted here because we need to have this cached
            Flux<RaknetUnconnectedPong> pongs = pings
                    .onBackpressureLatest()
                    .doOnNext(p->withMDC(log->log.debug("fetching pong from inbound")))
                    .flatMap(p->inbound.handlePing(p.getT2()))
                    .cache(Duration.ofSeconds(10))
                    ;
            //actually handle the reuqests
            return pings
                    //turn ping into ping&pong pair, need to do this because we only get pong from some ping in the past(cache)
                    .flatMap(p->pongs.next().map(pong->Tuples.of(p.getT1(), pong)))
                    .map(pair->{
                        final val sender = pair.getT1();
                        final val inboundPong = pair.getT2();
                        final val idstr = BedrockPong.fromRakNet(inboundPong.getIDString());
                        idstr.setIpv4Port(localAddress().port());
                        idstr.setServerId(guid);
                        final val pong = new RaknetUnconnectedPong(
                                inboundPong.getMilliSinceStart(),
                                guid,
                                inboundPong.getOfflineMessageDataID(),
                                idstr.toRakNet()
                        );
                        withMDC(log1 -> log1.debug("answering with pong: {}", pong));
                        return fromComponents(sender, Buffer.buffer(pong.toRakNet()));
                    })
                    .flatMap(socket::send)
                    //firewall, prevents error in handling 1 request blows up the entire server
                    .doOnError(ex->withMDC(log1 -> log1.error("error in handling ping", ex)))
                    .onErrorResume(ex->Mono.empty());
        };
    }

    private Mono<Void> handleGamePacket(DatagramPacket packet) {
        withMDC(log->log.debug("got client game packet from: {}, type: {}", packet.sender(), packet.data().getByte(0)));
        val source = connections.get(packet.sender());
        val dispatch = source != null ? Mono.just(source) : newPlayerConnection(packet.sender());
        return dispatch
                .doOnNext(InboundPlayerConnection::refreshLastArrival)
                .flatMap(v->Mono.fromRunnable(()->v.receiverSink.next(packet.data())));
    }

    private Mono<InboundPlayerConnection> newPlayerConnection(SocketAddress source) {
        withMDC(log->log.info("new player connection discovered: {}", source));

        final val processor = ReplayProcessor.<Buffer>cacheLast();
        final val sink = processor.sink();

        //build object and register mapping rules
        val conn = new PlayerConnection(
                "local(server:"+localAddress()+",client:"+source+")",
                processor,
                d -> socket.send(fromComponents(source, d))
        );
        val inboundConn = new InboundPlayerConnection(source, sink);
        connections.put(source, inboundConn);

        //the mono that repr. the state of connection
        final val stateMono = Mono.create(connStateSink -> {
            final val disposables = Disposables.composite();

            //garbage collect, if not active long enough, then remove
            final val gc = timerFlux
                    .subscribe(now -> {
                        final val diff = Duration.between(inboundConn.lastArrival, now).toSeconds() - TIMEOUT_DURATION.toSeconds();
                        if (diff > 0) {
                            withMDC(log -> log.info("player: {} got non active for {} seconds, closing session", source, diff));
                            connStateSink.success();
                        }
                    });
            disposables.add(gc);

            //shutdown if the entire server is going down
            final val state = stateFlux
                    .doOnComplete(() -> withMDC(log -> log.debug("player: {}‘s corresponding outbound server is closed, closing", source)))
                    .subscribe(v -> { }, connStateSink::error, connStateSink::success);
            disposables.add(state);

            connStateSink.onDispose(disposables);
        })
                //remove conn. multiplex on terminate
                .doFinally(t->connections.remove(source))
                //logging
                .doOnSuccess(o -> withMDC(log -> log.info("connection: [{}] closed normally", conn.getDescription())))
                .doOnError(o -> withMDC(log -> log.error("connection: [{}] closed with exception {}", conn.getDescription(), o)))
                //if state mono terminate, the outer receiveFlux's sink must also terminate
                .subscribe(v->{}, sink::error, sink::complete);
        ;

        return inbound.handleNewPlayer(conn)
                //on error in handling, clean up
                .doOnError(sink::error)
                .doOnError(e->connections.remove(source))
                .doOnError(e->stateMono.dispose())
                .doOnError(ex -> withMDC(log -> log.error("connection: [{}] cannot be accepted by inbound with exception {}", conn.getDescription(), ex)))
                .thenReturn(inboundConn);
    }

    private void withMDC(Consumer<Logger> logStmt) {
        try(final val ignored = MDC.putCloseable("exposedAddress", localAddress().toString())){
            logStmt.accept(log);
        }
    }

}
