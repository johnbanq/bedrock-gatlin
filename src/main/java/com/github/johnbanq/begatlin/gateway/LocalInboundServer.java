package com.github.johnbanq.begatlin.gateway;

import com.github.johnbanq.begatlin.network.ReactiveDatagramSocket;
import com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPing;
import com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPong;
import io.vertx.core.Vertx;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.net.SocketAddress;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.MDC;
import reactor.core.Disposables;
import reactor.core.publisher.*;

import java.time.Duration;
import java.util.function.Consumer;

import static com.github.johnbanq.begatlin.network.SimpleDatagramPacket.fromComponents;

/**
 * represents a inbound server on the local network
 */
@Slf4j
public class LocalInboundServer implements InboundServer {

    private static final SocketAddress ANY_ADDRESS = SocketAddress.inetSocketAddress(0, "0.0.0.0");

    private final Vertx vertx;

    private final SocketAddress localServerAddress;

    private final DatagramSocketOptions options;


    /**
     * hot mono, indicates the state of server (running, successfully stop(complete) and errored(error))
     */

    private Flux<Void> stateFlux;

    private FluxSink<Void> stateSink;

    public enum State {
        RUNNING,
        STOPPED,
        ERROR
    }

    //latest image of stateFlux
    private State state;


    // factories for default parameters

    public static Mono<LocalInboundServer> createServer(Vertx vertx) {
        return createServer(vertx, SocketAddress.inetSocketAddress(19132, "127.0.0.1"));
    }

    public static Mono<LocalInboundServer> createServer(Vertx vertx, SocketAddress localServerAddress) {
        return createServer(vertx, localServerAddress, new DatagramSocketOptions());
    }

    public static Mono<LocalInboundServer> createServer(Vertx vertx, SocketAddress localServerAddress, DatagramSocketOptions options) {
        final val server = new LocalInboundServer(vertx, localServerAddress, options);
        return server.start().thenReturn(server);
    }

    LocalInboundServer(Vertx vertx, SocketAddress localServerAddress, DatagramSocketOptions options) {
        this.vertx = vertx;
        this.localServerAddress = localServerAddress;
        this.options = options;

        final DirectProcessor<Void> processor = DirectProcessor.create();
        stateFlux = processor.publish(v->v);
        stateSink = processor.sink();

    }


    public Mono<Void> start() {
        final val disposables = Disposables.composite();

        //setup the latest state image
        state = State.RUNNING;
        final val stateVar = stateFlux.subscribe(v->{}, ex->{ state = State.ERROR; }, ()->{ state = State.STOPPED; });
        disposables.add(stateVar);

        stateSink.onDispose(disposables);

        return Mono.empty();
    }

    public Mono<Void> stop() {
        return Mono.fromRunnable(stateSink::complete);
    }

    public Flux<Void> getStateFlux() {
        return stateFlux;
    }

    public State getState() {
        return state;
    }

    /**
     * add a player connection to a running server
     * @param connection the connection to add
     * @return the mono indicates whether successfully added
     */
    @Override
    public Mono<Void> handleNewPlayer(PlayerConnection connection) {
        if(getState()!=State.RUNNING) {
            return Mono.error(new IllegalStateException("cannot add player to a closed inbound server!"));
        } else {
            withMDC(log -> log.info("receiving connection: [{}]", connection.getDescription()));
            final val socket = new ReactiveDatagramSocket(vertx, options);
            return socket.start(ANY_ADDRESS)
                    .flatMap((v)->{
                        withMDC(log -> log.info("socket for {} is listening on {}", socket.getSocket().localAddress(), socket.getSocket().localAddress()));
                        withMDC(log -> log.debug("setting up event streams for: [{}]", connection.getDescription()));

                        //the connection mono
                        Mono.create(connStateSink->{
                            final val disposables = Disposables.composite();

                            //the socket
                            disposables.add(()->socket.stop().subscribe());

                            // client -> server
                            final val outbound = connection.receiver()
                                    .doOnNext(p->withMDC(log->log.debug("sending client packet from:[{}] type:{}", connection.getDescription(), p.getByte(0))))
                                    .flatMap(p -> socket.send(fromComponents(localServerAddress, p)))
                                    .doOnError(ex->withMDC(log->log.error("error sending data on connection:[{}] {}", connection.getDescription(), ex)))
                                    .subscribe(v2->{}, connStateSink::error, connStateSink::success);
                            disposables.add(outbound);

                            // server -> client
                            final val inbound = socket.receiver()
                                    .doOnNext(p->withMDC(log->log.debug("receiving server packet from:[{}] type:{}", connection.getDescription(), p.data().getByte(0))))
                                    .flatMap(p -> connection.send(p.data()))
                                    .doOnError(ex->withMDC(log->log.error("error receiving data on connection:[{}] {}", connection.getDescription(), ex)))
                                    .subscribe(v2->{}, connStateSink::error, connStateSink::success);
                            disposables.add(inbound);

                            //if the server went down, we are also stopping for same cause
                            final val stateListener = stateFlux
                                    .doOnTerminate(()->withMDC(log->log.debug("disconnecting:[{}] due to server shutting down", connection.getDescription())))
                                    .subscribe(v2->{}, connStateSink::error, connStateSink::success);
                            disposables.add(stateListener);

                            //clean-ups
                            connStateSink.onDispose(disposables);
                        })
                                .doOnSuccess(o->{withMDC(log -> log.info("connection: [{}] successfully closed", connection.getDescription()));})
                                .doOnError(o->{withMDC(log -> log.error("connection: [{}] closed with exception {}", connection.getDescription(), o));})
                        .subscribe();

                        return Mono.<Void>empty();
                    })
                    .doOnSuccess(o->withMDC(log -> log.debug("set up connection for: [{}]", connection.getDescription())))
                    ;
        }
    }

    @Override
    public Mono<RaknetUnconnectedPong> handlePing(RaknetUnconnectedPing ping) {
        return periodicPing(Duration.ofSeconds(1), ping).next();
    }

    private <T> Flux<RaknetUnconnectedPong> periodicPing(Duration pingDuration, RaknetUnconnectedPing ping) {
        final val socket = new ReactiveDatagramSocket(vertx, new DatagramSocketOptions().setBroadcast(true));
        return DiscoveryFn.runPing(
                socket, SocketAddress.inetSocketAddress(localServerAddress.port(), "255.255.255.255"),
                Flux.interval(pingDuration).map(i->ping)
        )
                .filter(i->i.sender().port()==(localServerAddress.port()))
                .map(p->RaknetUnconnectedPong.fromRakNet(p.data().getBytes()))
                ;
    }

    private void withMDC(Consumer<Logger> logStmt) {
        try(final val ignored = MDC.putCloseable("localAddress", localServerAddress.toString())){
            logStmt.accept(log);
        }
    }

}
